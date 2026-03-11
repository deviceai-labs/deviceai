/**
 * deviceai_speech_engine.cpp
 *
 * Unified Speech engine — STT (whisper.cpp) + TTS (Piper/eSpeak-ng).
 * Pure C++ core, zero JNI/Swift/Flutter imports.
 *
 * Key STT features carried forward from the Android implementation:
 *   - Adaptive energy-based VAD: trims silence, prevents whisper looping
 *   - audio_ctx derived from actual sample count (avoids full 30s window)
 *   - Fresh whisper_state per call (prevents context bleed between calls)
 *
 * Exposes dai_stt_* and dai_tts_* C API declared in deviceai_speech_engine.h.
 */

#include "deviceai_speech_engine.h"
#include "whisper.h"
#include "piper.hpp"
#include "onnxruntime_cxx_api.h"

#include <string>
#include <vector>
#include <tuple>
#include <atomic>
#include <mutex>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <chrono>
#include <memory>

// ─── Logging ─────────────────────────────────────────────────────────────────

#ifdef __ANDROID__
#  include <android/log.h>
#  define STT_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "DeviceAI-STT", __VA_ARGS__)
#  define STT_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DeviceAI-STT", __VA_ARGS__)
#  define STT_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "DeviceAI-STT", __VA_ARGS__)
#  define TTS_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "DeviceAI-TTS", __VA_ARGS__)
#  define TTS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DeviceAI-TTS", __VA_ARGS__)
#  define TTS_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "DeviceAI-TTS", __VA_ARGS__)
#else
#  define STT_LOGI(...) fprintf(stdout, "[DeviceAI-STT] "       __VA_ARGS__); fputc('\n', stdout)
#  define STT_LOGE(...) fprintf(stderr, "[DeviceAI-STT ERROR] " __VA_ARGS__); fputc('\n', stderr)
#  define STT_LOGD(...) fprintf(stdout, "[DeviceAI-STT DEBUG] " __VA_ARGS__); fputc('\n', stdout)
#  define TTS_LOGI(...) fprintf(stdout, "[DeviceAI-TTS] "       __VA_ARGS__); fputc('\n', stdout)
#  define TTS_LOGE(...) fprintf(stderr, "[DeviceAI-TTS ERROR] " __VA_ARGS__); fputc('\n', stderr)
#  define TTS_LOGD(...) fprintf(stdout, "[DeviceAI-TTS DEBUG] " __VA_ARGS__); fputc('\n', stdout)
#endif

static inline long now_ms() {
    using namespace std::chrono;
    return (long)duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

// ─── STT global state ─────────────────────────────────────────────────────────

static struct whisper_context    *g_stt_ctx     = nullptr;
static struct whisper_full_params g_stt_params;
static std::mutex                 g_stt_mutex;
static std::atomic<bool>          g_stt_cancel{false};

static std::string  g_stt_language    = "en";
static std::atomic<bool>  g_stt_translate{false};
static std::atomic<int>   g_stt_threads{4};
static std::atomic<bool>  g_stt_use_gpu{true};
static std::atomic<bool>  g_stt_use_vad{true};
static std::atomic<bool>  g_stt_single_segment{true};
static std::atomic<bool>  g_stt_no_context{true};

// ─── TTS global state ─────────────────────────────────────────────────────────

static piper::PiperConfig       g_tts_config;
static piper::Voice             g_tts_voice;
static bool                     g_tts_initialized = false;
static std::mutex               g_tts_mutex;
static std::atomic<bool>        g_tts_cancel{false};
static std::atomic<int>         g_tts_sample_rate{22050};

// ─── VAD global state ─────────────────────────────────────────────────────────
// Silero VAD v4: LSTM-based, 32ms windows (512 samples @ 16kHz).
// State tensors (h, c) are maintained between windows for streaming use.
// Reset between independent audio sessions via dai_vad_reset().

static Ort::Env                      g_vad_env{ORT_LOGGING_LEVEL_WARNING, "DeviceAI-VAD"};
static std::unique_ptr<Ort::Session> g_vad_session;
static float                         g_vad_threshold  = 0.5f;
static int                           g_vad_sample_rate_val = 16000;
static std::vector<float>            g_vad_h(2 * 1 * 64, 0.0f);  // LSTM hidden [2,1,64]
static std::vector<float>            g_vad_c(2 * 1 * 64, 0.0f);  // LSTM cell   [2,1,64]
static std::atomic<bool>             g_vad_in_speech{false};
static std::mutex                    g_vad_mutex;

static const int VAD_WINDOW = 512;   // 32ms at 16kHz
static const int VAD_PAD    = 10;    // ~320ms end-of-speech padding (10 * 512 samples)

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Shared C string helpers
// ═══════════════════════════════════════════════════════════════════════════

static char *strdup_c(const std::string &s) {
    char *out = static_cast<char *>(malloc(s.size() + 1));
    if (out) memcpy(out, s.c_str(), s.size() + 1);
    return out;
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Audio utilities
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Read a PCM WAV file into float32 samples.
 * Supports mono and stereo (stereo is downmixed to mono).
 */
static bool read_wav_file(
    const std::string    &path,
    std::vector<float>   &samples,
    int                  &sample_rate
) {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        STT_LOGE("Cannot open WAV: %s", path.c_str());
        return false;
    }

    // RIFF header
    char riff[4];
    file.read(riff, 4);
    if (std::strncmp(riff, "RIFF", 4) != 0) {
        STT_LOGE("Not a RIFF WAV file: %s", path.c_str());
        return false;
    }
    file.seekg(4, std::ios::cur); // skip file size

    char wave[4];
    file.read(wave, 4);
    if (std::strncmp(wave, "WAVE", 4) != 0) {
        STT_LOGE("Missing WAVE header: %s", path.c_str());
        return false;
    }

    uint16_t num_channels = 1;

    while (file.good()) {
        char     chunk_id[4];
        uint32_t chunk_size;
        file.read(chunk_id, 4);
        file.read(reinterpret_cast<char *>(&chunk_size), 4);
        if (!file) break;

        if (std::strncmp(chunk_id, "fmt ", 4) == 0) {
            uint16_t audio_format;
            file.read(reinterpret_cast<char *>(&audio_format), 2);
            file.read(reinterpret_cast<char *>(&num_channels), 2);
            uint32_t sr;
            file.read(reinterpret_cast<char *>(&sr), 4);
            sample_rate = static_cast<int>(sr);
            file.seekg(chunk_size - 8, std::ios::cur);

        } else if (std::strncmp(chunk_id, "data", 4) == 0) {
            std::vector<int16_t> pcm(chunk_size / 2);
            file.read(reinterpret_cast<char *>(pcm.data()), chunk_size);

            // Downmix to mono if stereo, convert int16 → float32
            size_t n_frames = pcm.size() / num_channels;
            samples.resize(n_frames);
            for (size_t i = 0; i < n_frames; i++) {
                float sum = 0.0f;
                for (int c = 0; c < num_channels; c++)
                    sum += static_cast<float>(pcm[i * num_channels + c]);
                samples[i] = (sum / num_channels) / 32768.0f;
            }
            break;
        } else {
            file.seekg(chunk_size, std::ios::cur);
        }
    }

    return !samples.empty();
}

/**
 * Write int16_t PCM samples to a WAV file.
 */
static bool write_wav_file(
    const std::string          &path,
    const std::vector<int16_t> &samples,
    int                         sample_rate
) {
    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) {
        TTS_LOGE("Cannot open for writing: %s", path.c_str());
        return false;
    }

    uint32_t data_size  = static_cast<uint32_t>(samples.size() * sizeof(int16_t));
    uint32_t file_size  = 36 + data_size;
    uint16_t channels   = 1;
    uint16_t bits       = 16;
    uint32_t byte_rate  = sample_rate * channels * sizeof(int16_t);
    uint16_t block_align = channels * sizeof(int16_t);
    uint16_t fmt_size   = 16;
    uint16_t audio_fmt  = 1; // PCM

    file.write("RIFF", 4);
    file.write(reinterpret_cast<char *>(&file_size),   4);
    file.write("WAVE", 4);
    file.write("fmt ", 4);
    file.write(reinterpret_cast<char *>(&fmt_size),    4);
    file.write(reinterpret_cast<char *>(&audio_fmt),   2);
    file.write(reinterpret_cast<char *>(&channels),    2);
    file.write(reinterpret_cast<char *>(&sample_rate), 4);
    file.write(reinterpret_cast<char *>(&byte_rate),   4);
    file.write(reinterpret_cast<char *>(&block_align), 2);
    file.write(reinterpret_cast<char *>(&bits),        2);
    file.write("data", 4);
    file.write(reinterpret_cast<char *>(&data_size),   4);
    file.write(reinterpret_cast<const char *>(samples.data()), data_size);
    file.close();
    return true;
}

/**
 * Resample float32 audio to 16 kHz using linear interpolation.
 * No-op if already at 16 kHz.
 */
static void resample_to_16k(
    const std::vector<float> &input,
    int                       input_rate,
    std::vector<float>       &output
) {
    if (input_rate == WHISPER_SAMPLE_RATE) {
        output = input;
        return;
    }

    double ratio      = static_cast<double>(WHISPER_SAMPLE_RATE) / input_rate;
    size_t output_len = static_cast<size_t>(input.size() * ratio);
    output.resize(output_len);

    for (size_t i = 0; i < output_len; i++) {
        double src   = i / ratio;
        size_t idx0  = static_cast<size_t>(src);
        size_t idx1  = std::min(idx0 + 1, input.size() - 1);
        double frac  = src - idx0;
        output[i]    = static_cast<float>(input[idx0] * (1.0 - frac) + input[idx1] * frac);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Silero VAD (internal helpers)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Run Silero VAD on a single 512-sample window.
 *
 * Updates g_vad_h / g_vad_c LSTM state in-place so consecutive calls
 * form a coherent stream. Call dai_vad_reset() to clear state between
 * independent audio sessions.
 *
 * @return Speech probability [0.0, 1.0], or -1.0 if VAD not initialized.
 */
static float run_silero_window(const float *samples, int n_samples) {
    if (!g_vad_session) return -1.0f;

    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    // Input: [1, n_samples]
    std::array<int64_t, 2> input_shape  = {1, n_samples};
    // sr: [1]
    std::array<int64_t, 1> sr_shape     = {1};
    // h, c: [2, 1, 64]
    std::array<int64_t, 3> state_shape  = {2, 1, 64};

    int64_t sr_val = g_vad_sample_rate_val;

    Ort::Value inputs[4] = {
        Ort::Value::CreateTensor<float>(
            mem_info, const_cast<float *>(samples), n_samples,
            input_shape.data(), input_shape.size()),
        Ort::Value::CreateTensor<int64_t>(
            mem_info, &sr_val, 1,
            sr_shape.data(), sr_shape.size()),
        Ort::Value::CreateTensor<float>(
            mem_info, g_vad_h.data(), g_vad_h.size(),
            state_shape.data(), state_shape.size()),
        Ort::Value::CreateTensor<float>(
            mem_info, g_vad_c.data(), g_vad_c.size(),
            state_shape.data(), state_shape.size()),
    };

    const char *input_names[]  = {"input", "sr", "h", "c"};
    const char *output_names[] = {"output", "hn", "cn"};

    auto outputs = g_vad_session->Run(
        Ort::RunOptions{nullptr},
        input_names,  inputs,  4,
        output_names, 3
    );

    // Update LSTM state for next window
    float *hn = outputs[1].GetTensorMutableData<float>();
    float *cn = outputs[2].GetTensorMutableData<float>();
    std::copy(hn, hn + g_vad_h.size(), g_vad_h.begin());
    std::copy(cn, cn + g_vad_c.size(), g_vad_c.begin());

    return *outputs[0].GetTensorMutableData<float>();
}

/**
 * Adaptive energy-based VAD (fallback when Silero not initialized).
 * Kept as a fallback — used when g_vad_session == nullptr.
 */
static bool apply_energy_vad(std::vector<float> &audio) {
    const int FRAME = 480;  // 30ms at 16kHz
    const int PAD   = 10;

    int n_frames = static_cast<int>(audio.size()) / FRAME;
    if (n_frames == 0) return false;

    std::vector<float> frame_rms(n_frames);
    for (int f = 0; f < n_frames; f++) {
        const float *p = audio.data() + f * FRAME;
        float sum = 0.0f;
        for (int i = 0; i < FRAME; i++) sum += p[i] * p[i];
        frame_rms[f] = std::sqrt(sum / FRAME);
    }

    std::vector<float> sorted_rms = frame_rms;
    std::sort(sorted_rms.begin(), sorted_rms.end());
    float noise_floor = sorted_rms[std::max(0, n_frames / 10)];
    float threshold   = std::max(0.02f, noise_floor * 4.0f);

    int first_speech = -1, last_speech = -1;
    for (int f = 0; f < n_frames; f++) {
        if (frame_rms[f] >= threshold) {
            if (first_speech < 0) first_speech = f;
            last_speech = f;
        }
    }
    if (first_speech < 0) return false;

    int start = std::max(0,        first_speech - PAD) * FRAME;
    int end   = std::min(n_frames, last_speech  + PAD + 1) * FRAME;
    audio = std::vector<float>(audio.begin() + start, audio.begin() + end);
    return true;
}

/**
 * Apply VAD to a batch audio buffer (used internally by STT).
 *
 * Dispatches to Silero if initialized, otherwise energy-based fallback.
 * Resets Silero state before batch processing (each STT call is independent).
 */
static bool apply_vad(std::vector<float> &audio) {
    if (!g_vad_session) {
        STT_LOGD("VAD: using energy-based fallback (Silero not initialized)");
        return apply_energy_vad(audio);
    }

    // Reset LSTM state for this independent batch
    std::fill(g_vad_h.begin(), g_vad_h.end(), 0.0f);
    std::fill(g_vad_c.begin(), g_vad_c.end(), 0.0f);

    int n_windows = static_cast<int>(audio.size()) / VAD_WINDOW;
    if (n_windows == 0) return false;

    std::vector<float> probs(n_windows);
    for (int i = 0; i < n_windows; i++)
        probs[i] = run_silero_window(audio.data() + i * VAD_WINDOW, VAD_WINDOW);

    int first_speech = -1, last_speech = -1;
    for (int i = 0; i < n_windows; i++) {
        if (probs[i] >= g_vad_threshold) {
            if (first_speech < 0) first_speech = i;
            last_speech = i;
        }
    }

    if (first_speech < 0) {
        STT_LOGI("Silero VAD: no speech detected");
        return false;
    }

    int start = std::max(0,         first_speech - VAD_PAD) * VAD_WINDOW;
    int end   = std::min(n_windows, last_speech  + VAD_PAD + 1) * VAD_WINDOW;

    float before_sec = static_cast<float>(audio.size()) / WHISPER_SAMPLE_RATE;
    float after_sec  = static_cast<float>(end - start)  / WHISPER_SAMPLE_RATE;
    STT_LOGI("Silero VAD: trimmed %.2fs → %.2fs", before_sec, after_sec);

    audio = std::vector<float>(audio.begin() + start, audio.begin() + end);
    return true;
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - STT core logic (internal, no JNI/FFI types)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Build JSON result string from transcription segments.
 * Schema: { "text": "...", "language": "en", "durationMs": 1234,
 *           "segments": [{ "text": "...", "startMs": 0, "endMs": 500 }] }
 */
static std::string build_result_json(
    const std::string                                            &text,
    const std::vector<std::tuple<std::string, int64_t, int64_t>> &segments,
    const std::string                                            &language,
    int64_t                                                       duration_ms
) {
    std::ostringstream j;
    j << "{\"text\":\"" << text << "\""
      << ",\"language\":\"" << language << "\""
      << ",\"durationMs\":" << duration_ms
      << ",\"segments\":[";

    for (size_t i = 0; i < segments.size(); i++) {
        if (i > 0) j << ",";
        j << "{\"text\":\"" << std::get<0>(segments[i]) << "\""
          << ",\"startMs\":" << std::get<1>(segments[i])
          << ",\"endMs\":"   << std::get<2>(segments[i])
          << "}";
    }
    j << "]}";
    return j.str();
}

/**
 * Core transcription: run whisper_full_with_state on pre-processed audio.
 *
 * Applies VAD, derives audio_ctx from actual sample count, allocates a fresh
 * whisper_state per call (prevents KV-cache bleed between calls).
 *
 * Returns collected text. Also fills segments if non-null.
 */
static std::string run_transcription(
    std::vector<float>                                           &audio,
    std::vector<std::tuple<std::string, int64_t, int64_t>>      *segments_out,
    std::function<void(const std::string &)>                      on_partial
) {
    if (g_stt_ctx == nullptr) {
        STT_LOGE("Whisper not initialized");
        return "";
    }

    g_stt_cancel = false;
    float audio_sec = static_cast<float>(audio.size()) / WHISPER_SAMPLE_RATE;

    // VAD: trim silence before inference
    if (g_stt_use_vad.load()) {
        if (!apply_vad(audio)) return "";
        audio_sec = static_cast<float>(audio.size()) / WHISPER_SAMPLE_RATE;
    }

    // Derive audio_ctx from actual sample count to avoid full 30s attention window.
    // Formula: each whisper mel frame = 160 samples; encoder conv halves → /320.
    struct whisper_full_params params = g_stt_params;
    int auto_ctx = (static_cast<int>(audio.size()) + 319) / 320;
    params.audio_ctx = std::min(auto_ctx, 1500);
    STT_LOGD("audio_ctx=%d (%.2fs after VAD)", params.audio_ctx, audio_sec);

    // Fresh state per call — prevents result_all accumulation across calls.
    struct whisper_state *state = whisper_init_state(g_stt_ctx);
    if (!state) {
        STT_LOGE("Failed to allocate whisper_state");
        return "";
    }

    long t0 = now_ms();
    int rc = whisper_full_with_state(g_stt_ctx, state, params,
                                     audio.data(), static_cast<int>(audio.size()));
    long t1 = now_ms();
    STT_LOGI("Inference: %ld ms (RTF %.2fx)", t1 - t0, (float)(t1 - t0) / (audio_sec * 1000.0f));

    if (rc != 0) {
        whisper_free_state(state);
        STT_LOGE("whisper_full_with_state failed");
        return "";
    }

    std::string full_text;
    int n_seg = whisper_full_n_segments_from_state(state);

    for (int i = 0; i < n_seg; i++) {
        if (g_stt_cancel.load()) break;

        const char *text = whisper_full_get_segment_text_from_state(state, i);
        int64_t     t_s0 = whisper_full_get_segment_t0_from_state(state, i) * 10; // cs → ms
        int64_t     t_s1 = whisper_full_get_segment_t1_from_state(state, i) * 10;

        if (text) {
            full_text += text;
            if (segments_out)
                segments_out->emplace_back(text, t_s0, t_s1);
            if (on_partial)
                on_partial(full_text);
        }
    }

    whisper_free_state(state);
    STT_LOGD("Transcribed %d segments: \"%s\"", n_seg, full_text.c_str());
    return full_text;
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Public C API — STT
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

int dai_stt_init(
    const char *model_path,
    const char *language,
    int         translate,
    int         max_threads,
    int         use_gpu,
    int         use_vad,
    int         single_segment,
    int         no_context
) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);

    if (g_stt_ctx) {
        whisper_free(g_stt_ctx);
        g_stt_ctx = nullptr;
    }

    g_stt_language      = (language && language[0]) ? language : "en";
    g_stt_translate     = (translate != 0);
    g_stt_threads       = max_threads;
    g_stt_use_gpu       = (use_gpu != 0);
    g_stt_use_vad       = (use_vad != 0);
    g_stt_single_segment = (single_segment != 0);
    g_stt_no_context    = (no_context != 0);

    struct whisper_context_params ctx_params = whisper_context_default_params();
    ctx_params.use_gpu = (use_gpu != 0);

    g_stt_ctx = whisper_init_from_file_with_params(model_path, ctx_params);
    if (!g_stt_ctx) {
        STT_LOGE("Failed to load model: %s", model_path);
        return 0;
    }

    g_stt_params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    g_stt_params.language         = g_stt_language.c_str();
    g_stt_params.translate        = (translate != 0);
    g_stt_params.n_threads        = max_threads;
    g_stt_params.single_segment   = (single_segment != 0);
    g_stt_params.no_context       = (no_context != 0);
    g_stt_params.no_timestamps    = false;
    g_stt_params.print_special    = false;
    g_stt_params.print_progress   = false;
    g_stt_params.print_realtime   = false;
    g_stt_params.print_timestamps = false;

    STT_LOGI("Initialized: %s (lang=%s vad=%d gpu=%d threads=%d)",
             model_path, g_stt_language.c_str(), use_vad, use_gpu, max_threads);
    return 1;
}

char *dai_stt_transcribe(const float *samples, int n_samples) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);
    std::vector<float> audio(samples, samples + n_samples);
    std::string text = run_transcription(audio, nullptr, nullptr);
    return strdup_c(text);
}

char *dai_stt_transcribe_file(const char *wav_path) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);

    std::vector<float> samples;
    int sample_rate = 0;
    if (!read_wav_file(wav_path, samples, sample_rate)) return strdup_c("");

    std::vector<float> samples_16k;
    resample_to_16k(samples, sample_rate, samples_16k);

    std::string text = run_transcription(samples_16k, nullptr, nullptr);
    return strdup_c(text);
}

char *dai_stt_transcribe_file_detailed(const char *wav_path) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);

    static const char *EMPTY_JSON =
        "{\"text\":\"\",\"language\":\"en\",\"durationMs\":0,\"segments\":[]}";

    std::vector<float> samples;
    int sample_rate = 0;
    if (!read_wav_file(wav_path, samples, sample_rate)) return strdup_c(EMPTY_JSON);

    std::vector<float> samples_16k;
    resample_to_16k(samples, sample_rate, samples_16k);

    std::vector<std::tuple<std::string, int64_t, int64_t>> segs;
    std::string text = run_transcription(samples_16k, &segs, nullptr);

    int64_t duration_ms = static_cast<int64_t>(samples_16k.size()) * 1000 / WHISPER_SAMPLE_RATE;
    std::string json = build_result_json(text, segs, g_stt_language, duration_ms);
    return strdup_c(json);
}

void dai_stt_transcribe_stream(
    const float        *samples,
    int                 n_samples,
    dai_stt_partial_cb  on_partial,
    dai_stt_final_cb    on_final,
    dai_stt_error_cb    on_error,
    void               *user_data
) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);

    if (!g_stt_ctx) {
        if (on_error) on_error("STT not initialized", user_data);
        return;
    }

    std::vector<float> audio(samples, samples + n_samples);
    std::vector<std::tuple<std::string, int64_t, int64_t>> segs;

    std::string text = run_transcription(
        audio, &segs,
        [&](const std::string &partial) {
            if (on_partial && !g_stt_cancel.load())
                on_partial(partial.c_str(), user_data);
        }
    );

    if (g_stt_cancel.load()) {
        if (on_error) on_error("Cancelled", user_data);
        return;
    }

    int64_t duration_ms = static_cast<int64_t>(n_samples) * 1000 / WHISPER_SAMPLE_RATE;
    std::string json = build_result_json(text, segs, g_stt_language, duration_ms);
    if (on_final) on_final(json.c_str(), user_data);
}

void dai_stt_cancel(void) {
    g_stt_cancel = true;
}

void dai_stt_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_stt_mutex);
    if (g_stt_ctx) {
        STT_LOGI("Shutdown");
        whisper_free(g_stt_ctx);
        g_stt_ctx = nullptr;
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Public C API — TTS
// ═══════════════════════════════════════════════════════════════════════════

int dai_tts_init(
    const char *model_path,
    const char *config_path,
    const char *espeak_data_path,
    int         speaker_id,
    float       speech_rate,
    int         sample_rate,
    float       sentence_silence
) {
    std::lock_guard<std::mutex> lock(g_tts_mutex);

    if (g_tts_initialized) {
        piper::terminate(g_tts_config);
        g_tts_initialized = false;
    }

    g_tts_sample_rate = sample_rate;

    TTS_LOGI("Initializing Piper: model=%s espeak=%s", model_path, espeak_data_path);

    try {
        g_tts_config.eSpeakDataPath = espeak_data_path ? espeak_data_path : "";
        piper::initialize(g_tts_config);

        std::optional<piper::SpeakerId> sid;
        if (speaker_id >= 0)
            sid = static_cast<piper::SpeakerId>(speaker_id);

        piper::loadVoice(g_tts_config, model_path, config_path, g_tts_voice, sid, /*cuda=*/false);

        if (speech_rate != 1.0f)
            g_tts_voice.synthesisConfig.lengthScale = 1.0f / speech_rate;
        g_tts_voice.synthesisConfig.sentenceSilenceSeconds = sentence_silence;

        g_tts_initialized = true;
        TTS_LOGI("TTS ready (rate=%dHz speaker=%d)", sample_rate, speaker_id);
        return 1;

    } catch (const std::exception &e) {
        TTS_LOGE("Init failed: %s", e.what());
        return 0;
    }
}

int16_t *dai_tts_synthesize(const char *text, int *out_length) {
    std::lock_guard<std::mutex> lock(g_tts_mutex);

    if (!g_tts_initialized) {
        TTS_LOGE("Not initialized");
        *out_length = 0;
        return nullptr;
    }

    g_tts_cancel = false;

    try {
        std::vector<int16_t> audio;
        piper::SynthesisResult result;
        piper::textToAudio(g_tts_config, g_tts_voice, text, audio, result, []() {});

        if (audio.empty()) {
            TTS_LOGE("No audio produced");
            *out_length = 0;
            return nullptr;
        }

        TTS_LOGD("Synthesized %zu samples (%.2fs, RTF %.2f)",
                 audio.size(), result.audioSeconds, result.realTimeFactor);

        int16_t *out = static_cast<int16_t *>(malloc(audio.size() * sizeof(int16_t)));
        if (out) {
            memcpy(out, audio.data(), audio.size() * sizeof(int16_t));
            *out_length = static_cast<int>(audio.size());
        } else {
            *out_length = 0;
        }
        return out;

    } catch (const std::exception &e) {
        TTS_LOGE("Synthesis failed: %s", e.what());
        *out_length = 0;
        return nullptr;
    }
}

int dai_tts_synthesize_to_file(const char *text, const char *output_path) {
    std::lock_guard<std::mutex> lock(g_tts_mutex);

    if (!g_tts_initialized) {
        TTS_LOGE("Not initialized");
        return 0;
    }

    g_tts_cancel = false;

    try {
        std::vector<int16_t> audio;
        piper::SynthesisResult result;
        piper::textToAudio(g_tts_config, g_tts_voice, text, audio, result, []() {});

        if (audio.empty()) {
            TTS_LOGE("No audio produced");
            return 0;
        }

        int sr = g_tts_voice.synthesisConfig.sampleRate;
        if (!write_wav_file(output_path, audio, sr)) return 0;

        TTS_LOGI("Wrote %zu samples to %s (%.2fs)", audio.size(), output_path, result.audioSeconds);
        return 1;

    } catch (const std::exception &e) {
        TTS_LOGE("Synthesis failed: %s", e.what());
        return 0;
    }
}

void dai_tts_synthesize_stream(
    const char          *text,
    dai_tts_chunk_cb     on_chunk,
    dai_tts_complete_cb  on_complete,
    dai_tts_error_cb     on_error,
    void                *user_data
) {
    std::lock_guard<std::mutex> lock(g_tts_mutex);

    if (!g_tts_initialized) {
        if (on_error) on_error("TTS not initialized", user_data);
        return;
    }

    g_tts_cancel = false;

    try {
        std::vector<int16_t> audio;
        piper::SynthesisResult result;

        // Piper synthesizes all at once; we chunk for streaming playback.
        piper::textToAudio(g_tts_config, g_tts_voice, text, audio, result, [&]() {
            if (g_tts_cancel.load()) throw std::runtime_error("Cancelled");
        });

        if (g_tts_cancel.load() || audio.empty()) {
            if (!g_tts_cancel.load() && on_error) on_error("No audio produced", user_data);
            return;
        }

        // Deliver in ~185ms chunks (4096 samples at 22050 Hz)
        const size_t CHUNK = 4096;
        for (size_t i = 0; i < audio.size() && !g_tts_cancel.load(); i += CHUNK) {
            size_t len = std::min(CHUNK, audio.size() - i);
            if (on_chunk) on_chunk(&audio[i], static_cast<int>(len), user_data);
        }

        if (!g_tts_cancel.load() && on_complete) on_complete(user_data);

    } catch (const std::exception &e) {
        if (!g_tts_cancel.load() && on_error) on_error(e.what(), user_data);
    }
}

void dai_tts_cancel(void) {
    g_tts_cancel = true;
}

void dai_tts_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_tts_mutex);
    if (g_tts_initialized) {
        TTS_LOGI("Shutdown");
        piper::terminate(g_tts_config);
        g_tts_initialized = false;
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Public C API — VAD
// ═══════════════════════════════════════════════════════════════════════════

int dai_vad_init(const char *model_path, float threshold, int sample_rate) {
    std::lock_guard<std::mutex> lock(g_vad_mutex);

    g_vad_session.reset();
    g_vad_threshold       = threshold;
    g_vad_sample_rate_val = sample_rate;
    std::fill(g_vad_h.begin(), g_vad_h.end(), 0.0f);
    std::fill(g_vad_c.begin(), g_vad_c.end(), 0.0f);
    g_vad_in_speech = false;

    try {
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(1);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        g_vad_session = std::make_unique<Ort::Session>(g_vad_env, model_path, opts);
        STT_LOGI("Silero VAD initialized: threshold=%.2f rate=%d", threshold, sample_rate);
        return 1;
    } catch (const std::exception &e) {
        STT_LOGE("VAD init failed: %s", e.what());
        return 0;
    }
}

int dai_vad_is_speech(const float *samples, int n_samples) {
    std::lock_guard<std::mutex> lock(g_vad_mutex);
    if (!g_vad_session) return 0;

    // Reset state for single-shot check
    std::fill(g_vad_h.begin(), g_vad_h.end(), 0.0f);
    std::fill(g_vad_c.begin(), g_vad_c.end(), 0.0f);

    int n_windows = n_samples / VAD_WINDOW;
    if (n_windows == 0) return 0;

    float total = 0.0f;
    for (int i = 0; i < n_windows; i++)
        total += run_silero_window(samples + i * VAD_WINDOW, VAD_WINDOW);

    return (total / n_windows) >= g_vad_threshold ? 1 : 0;
}

void dai_vad_process_stream(
    const float            *samples,
    int                     n_samples,
    dai_vad_speech_start_cb on_speech_start,
    dai_vad_speech_end_cb   on_speech_end,
    void                   *user_data
) {
    std::lock_guard<std::mutex> lock(g_vad_mutex);
    if (!g_vad_session) return;

    // Process in VAD_WINDOW chunks; state carries across chunks (streaming)
    static int silence_frames = 0;

    int n_windows = n_samples / VAD_WINDOW;
    for (int i = 0; i < n_windows; i++) {
        float prob = run_silero_window(samples + i * VAD_WINDOW, VAD_WINDOW);
        bool  is_speech = prob >= g_vad_threshold;

        if (is_speech) {
            silence_frames = 0;
            if (!g_vad_in_speech.exchange(true)) {
                if (on_speech_start) on_speech_start(user_data);
            }
        } else if (g_vad_in_speech.load()) {
            silence_frames++;
            // End of speech after VAD_PAD consecutive silent windows (~320ms)
            if (silence_frames >= VAD_PAD) {
                silence_frames = 0;
                g_vad_in_speech = false;
                if (on_speech_end) on_speech_end(user_data);
            }
        }
    }
}

void dai_vad_reset(void) {
    std::lock_guard<std::mutex> lock(g_vad_mutex);
    std::fill(g_vad_h.begin(), g_vad_h.end(), 0.0f);
    std::fill(g_vad_c.begin(), g_vad_c.end(), 0.0f);
    g_vad_in_speech = false;
}

void dai_vad_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_vad_mutex);
    if (g_vad_session) {
        g_vad_session.reset();
        STT_LOGI("Silero VAD shutdown");
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Shared utilities
// ═══════════════════════════════════════════════════════════════════════════

void dai_speech_free_string(char *ptr) {
    free(ptr);
}

void dai_speech_free_audio(int16_t *ptr) {
    free(ptr);
}

void dai_speech_shutdown_all(void) {
    dai_vad_shutdown();
    dai_stt_shutdown();
    dai_tts_shutdown();
}

} // extern "C"
