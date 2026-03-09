/**
 * deviceai_speech_engine.h
 *
 * Unified C API for on-device speech: STT (Whisper) + TTS (Piper).
 * Single implementation compiled once — consumed by all platform wrappers:
 *
 *   Kotlin/Android  →  JNI wrapper calls dai_stt_* / dai_tts_*
 *   Swift/iOS       →  C interop via .def file
 *   Flutter         →  dart:ffi DynamicLibrary.lookup
 *   React Native    →  JSI/FFI bridge
 *
 * Design rules:
 *   - Pure C API (extern "C") — callable from any FFI
 *   - Callbacks carry void* user_data — safe across thread/language boundaries
 *   - Heap-allocated returns must be freed with the matching dai_speech_free_* function
 *   - STT result JSON schema: { text, language, durationMs, segments:[{text,startMs,endMs}] }
 */

#ifndef DEVICEAI_SPEECH_ENGINE_H
#define DEVICEAI_SPEECH_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ─── STT callbacks ───────────────────────────────────────────────────────────

/** Partial transcription result (accumulated text so far). */
typedef void (*dai_stt_partial_cb)(const char *partial_text, void *user_data);

/** Final transcription result as JSON string. Schema:
 *  { "text": "...", "language": "en", "durationMs": 1234,
 *    "segments": [{ "text": "...", "startMs": 0, "endMs": 500 }] }
 */
typedef void (*dai_stt_final_cb)(const char *result_json, void *user_data);

/** Error callback for STT. */
typedef void (*dai_stt_error_cb)(const char *error, void *user_data);

// ─── TTS callbacks ───────────────────────────────────────────────────────────

/** Audio chunk callback: int16_t PCM samples at the configured sample rate. */
typedef void (*dai_tts_chunk_cb)(const int16_t *samples, int count, void *user_data);

/** Called when streaming synthesis is complete. */
typedef void (*dai_tts_complete_cb)(void *user_data);

/** Error callback for TTS. */
typedef void (*dai_tts_error_cb)(const char *error, void *user_data);

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - STT (Speech-to-Text via whisper.cpp)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Load a Whisper GGML model and initialize STT.
 *
 * @param model_path     Absolute path to the .bin model file
 * @param language       BCP-47 language code (e.g. "en", "es"). Pass "" for auto-detect.
 * @param translate      1 = translate to English; 0 = transcribe in source language
 * @param max_threads    CPU threads for inference
 * @param use_gpu        1 = use GPU (Metal on iOS, OpenCL on Android); 0 = CPU only
 * @param use_vad        1 = enable energy-based VAD with adaptive threshold (recommended)
 * @param single_segment 1 = force single output segment (faster for short utterances)
 * @param no_context     1 = disable cross-segment context (reduces hallucinations)
 * @return 1 on success, 0 on failure
 */
int dai_stt_init(
    const char *model_path,
    const char *language,
    int         translate,
    int         max_threads,
    int         use_gpu,
    int         use_vad,
    int         single_segment,
    int         no_context
);

/**
 * Transcribe raw float PCM samples (16 kHz mono, range [-1.0, 1.0]).
 *
 * Includes adaptive VAD: trims leading/trailing silence, derives audio_ctx
 * from actual sample count, and allocates a fresh whisper_state per call
 * to prevent context bleed between calls.
 *
 * @param samples   Float32 PCM samples at 16 kHz
 * @param n_samples Number of samples
 * @return Heap-allocated transcription text. Free with dai_speech_free_string().
 *         Returns empty string on error or if no speech detected.
 */
char *dai_stt_transcribe(const float *samples, int n_samples);

/**
 * Transcribe a WAV file from disk (reads, resamples to 16 kHz if needed).
 *
 * @param wav_path Absolute path to a PCM WAV file
 * @return Heap-allocated transcription text. Free with dai_speech_free_string().
 */
char *dai_stt_transcribe_file(const char *wav_path);

/**
 * Transcribe a WAV file and return full detail as JSON.
 * JSON schema: { text, language, durationMs, segments:[{text,startMs,endMs}] }
 *
 * @param wav_path Absolute path to a PCM WAV file
 * @return Heap-allocated JSON string. Free with dai_speech_free_string().
 */
char *dai_stt_transcribe_file_detailed(const char *wav_path);

/**
 * Transcribe audio and deliver results via callbacks.
 *
 * on_partial is called for each segment as it completes.
 * on_final is called once with the full JSON result.
 * Blocks until complete or cancelled.
 *
 * @param samples   Float32 PCM samples at 16 kHz
 * @param n_samples Number of samples
 */
void dai_stt_transcribe_stream(
    const float      *samples,
    int               n_samples,
    dai_stt_partial_cb on_partial,
    dai_stt_final_cb   on_final,
    dai_stt_error_cb   on_error,
    void              *user_data
);

/** Cancel an in-progress transcription. Safe to call from any thread. */
void dai_stt_cancel(void);

/** Unload the Whisper model and release all STT resources. */
void dai_stt_shutdown(void);

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - TTS (Text-to-Speech via Piper + eSpeak-ng)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Load a Piper voice model and initialize TTS.
 *
 * @param model_path      Absolute path to .onnx voice model
 * @param config_path     Absolute path to .onnx.json voice config
 * @param espeak_data_path Absolute path to espeak-ng data directory (required for phonemization)
 * @param speaker_id      Speaker ID for multi-speaker models (-1 for single-speaker models)
 * @param speech_rate     Playback rate multiplier (1.0 = normal, 0.5 = slow, 2.0 = fast)
 * @param sample_rate     Output sample rate in Hz (typically 22050)
 * @param sentence_silence Silence in seconds inserted between sentences (e.g. 0.2)
 * @return 1 on success, 0 on failure
 */
int dai_tts_init(
    const char *model_path,
    const char *config_path,
    const char *espeak_data_path,
    int         speaker_id,
    float       speech_rate,
    int         sample_rate,
    float       sentence_silence
);

/**
 * Synthesize text to PCM audio (blocking).
 *
 * @param text       Input text to synthesize
 * @param out_length Set to the number of int16_t samples in the returned buffer
 * @return Heap-allocated int16_t PCM samples. Free with dai_speech_free_audio().
 *         Returns NULL on error.
 */
int16_t *dai_tts_synthesize(const char *text, int *out_length);

/**
 * Synthesize text and write to a WAV file on disk.
 *
 * @param text        Input text
 * @param output_path Absolute path for the output .wav file
 * @return 1 on success, 0 on failure
 */
int dai_tts_synthesize_to_file(const char *text, const char *output_path);

/**
 * Synthesize text and deliver audio in chunks via callback (streaming playback).
 *
 * on_chunk delivers ~185ms of audio at a time (4096 samples at 22050 Hz).
 * on_complete is called after the last chunk.
 * Blocks until complete or cancelled.
 */
void dai_tts_synthesize_stream(
    const char         *text,
    dai_tts_chunk_cb    on_chunk,
    dai_tts_complete_cb on_complete,
    dai_tts_error_cb    on_error,
    void               *user_data
);

/** Cancel an in-progress synthesis. Safe to call from any thread. */
void dai_tts_cancel(void);

/** Unload the Piper voice model and release all TTS resources. */
void dai_tts_shutdown(void);

// ═══════════════════════════════════════════════════════════════════════════
// MARK: - Shared utilities
// ═══════════════════════════════════════════════════════════════════════════

/** Free a string returned by any dai_stt_* function. */
void dai_speech_free_string(char *ptr);

/** Free an audio buffer returned by dai_tts_synthesize. */
void dai_speech_free_audio(int16_t *ptr);

/** Shutdown both STT and TTS in one call. */
void dai_speech_shutdown_all(void);

#ifdef __cplusplus
}
#endif

#endif // DEVICEAI_SPEECH_ENGINE_H
