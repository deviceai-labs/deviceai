/**
 * whisper_jni.cpp
 *
 * Thin JNI delegate — converts Android/JVM types to plain C, calls
 * the unified dai_stt_* API in deviceai_speech_engine.{h,cpp}.
 *
 * No STT logic lives here. JSON parsing is kept minimal inline so
 * the JNI boundary stays as the sole data-marshalling point.
 */

#include "speech_jni.h"
#include "deviceai_speech_engine.h"

#include <jni.h>
#include <string>
#include <vector>
#include <cstdlib>

// ─── Helpers ─────────────────────────────────────────────────────────────────

static std::string jstring_to_std(JNIEnv *env, jstring js) {
    if (!js) return "";
    const char *chars = env->GetStringUTFChars(js, nullptr);
    std::string s(chars);
    env->ReleaseStringUTFChars(js, chars);
    return s;
}

// Minimal JSON field extractor for the known-schema engine output:
// {"text":"...","language":"en","durationMs":1234,
//  "segments":[{"text":"...","startMs":0,"endMs":500}]}

static std::string json_str(const std::string &json, const char *key) {
    std::string pat = std::string("\"") + key + "\":\"";
    size_t p = json.find(pat);
    if (p == std::string::npos) return "";
    p += pat.size();
    size_t e = json.find('"', p);
    return e != std::string::npos ? json.substr(p, e - p) : "";
}

static long long json_ll(const std::string &json, const char *key) {
    std::string pat = std::string("\"") + key + "\":";
    size_t p = json.find(pat);
    return p != std::string::npos ? std::atoll(json.c_str() + p + pat.size()) : 0LL;
}

// Build a dev.deviceai.TranscriptionResult Java object from an engine JSON string.
static jobject build_result(JNIEnv *env, const std::string &json) {
    jclass resultClass  = env->FindClass("dev/deviceai/TranscriptionResult");
    jclass segmentClass = env->FindClass("dev/deviceai/Segment");
    jclass listClass    = env->FindClass("java/util/ArrayList");

    jmethodID listCtor    = env->GetMethodID(listClass,    "<init>", "()V");
    jmethodID listAdd     = env->GetMethodID(listClass,    "add",    "(Ljava/lang/Object;)Z");
    jmethodID segmentCtor = env->GetMethodID(segmentClass, "<init>", "(Ljava/lang/String;JJ)V");
    jmethodID resultCtor  = env->GetMethodID(resultClass,  "<init>",
        "(Ljava/lang/String;Ljava/util/List;Ljava/lang/String;J)V");

    std::string text = json_str(json, "text");
    std::string lang = json_str(json, "language");
    long long   dur  = json_ll(json, "durationMs");

    jobject segList = env->NewObject(listClass, listCtor);

    size_t arr_start = json.find("\"segments\":[");
    if (arr_start != std::string::npos) {
        arr_start = json.find('[', arr_start) + 1;
        size_t arr_end = json.find(']', arr_start);
        std::string arr = json.substr(arr_start, arr_end - arr_start);

        size_t pos = 0;
        while ((pos = arr.find('{', pos)) != std::string::npos) {
            size_t obj_end = arr.find('}', pos);
            if (obj_end == std::string::npos) break;
            std::string obj = arr.substr(pos, obj_end - pos + 1);

            jobject seg = env->NewObject(segmentClass, segmentCtor,
                env->NewStringUTF(json_str(obj, "text").c_str()),
                (jlong)json_ll(obj, "startMs"),
                (jlong)json_ll(obj, "endMs")
            );
            env->CallBooleanMethod(segList, listAdd, seg);
            env->DeleteLocalRef(seg);
            pos = obj_end + 1;
        }
    }

    return env->NewObject(resultClass, resultCtor,
        env->NewStringUTF(text.c_str()),
        segList,
        env->NewStringUTF(lang.empty() ? "en" : lang.c_str()),
        (jlong)dur
    );
}

// ─── Streaming context ────────────────────────────────────────────────────────

struct SttStreamCtx {
    JavaVM    *jvm;
    jobject    callback;   // global ref
    jmethodID  onPartial;
    jmethodID  onFinal;
    jmethodID  onError;
};

static void stt_on_partial(const char *partial_text, void *user_data) {
    auto *ctx = static_cast<SttStreamCtx *>(user_data);
    JNIEnv *env;
    ctx->jvm->AttachCurrentThread(&env, nullptr);
    jstring jt = env->NewStringUTF(partial_text ? partial_text : "");
    env->CallVoidMethod(ctx->callback, ctx->onPartial, jt);
    env->DeleteLocalRef(jt);
}

static void stt_on_final(const char *result_json, void *user_data) {
    auto *ctx = static_cast<SttStreamCtx *>(user_data);
    JNIEnv *env;
    ctx->jvm->AttachCurrentThread(&env, nullptr);
    std::string json = result_json ? result_json : "{}";
    jobject result = build_result(env, json);
    env->CallVoidMethod(ctx->callback, ctx->onFinal, result);
    env->DeleteLocalRef(result);
}

static void stt_on_error(const char *error, void *user_data) {
    auto *ctx = static_cast<SttStreamCtx *>(user_data);
    JNIEnv *env;
    ctx->jvm->AttachCurrentThread(&env, nullptr);
    jstring je = env->NewStringUTF(error ? error : "Unknown error");
    env->CallVoidMethod(ctx->callback, ctx->onError, je);
    env->DeleteLocalRef(je);
}

// ─── VAD streaming context ────────────────────────────────────────────────────

struct VadStreamCtx {
    JavaVM   *jvm;
    jobject   callback;   // global ref
    jmethodID onSpeechStart;
    jmethodID onSpeechEnd;
};

static void vad_on_speech_start(void *user_data) {
    auto *ctx = static_cast<VadStreamCtx *>(user_data);
    JNIEnv *env;
    ctx->jvm->AttachCurrentThread(&env, nullptr);
    env->CallVoidMethod(ctx->callback, ctx->onSpeechStart);
}

static void vad_on_speech_end(void *user_data) {
    auto *ctx = static_cast<VadStreamCtx *>(user_data);
    JNIEnv *env;
    ctx->jvm->AttachCurrentThread(&env, nullptr);
    env->CallVoidMethod(ctx->callback, ctx->onSpeechEnd);
}

// ─── JNI exports ─────────────────────────────────────────────────────────────

extern "C" {

// ── VAD ──────────────────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_dev_deviceai_SpeechBridge_nativeInitVad(
    JNIEnv *env, jobject,
    jstring modelPath, jfloat threshold, jint sampleRate
) {
    std::string path = jstring_to_std(env, modelPath);
    return dai_vad_init(path.c_str(), (float)threshold, (int)sampleRate)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_deviceai_SpeechBridge_nativeIsSpeech(
    JNIEnv *env, jobject,
    jfloatArray samples
) {
    jsize  len  = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    int result = dai_vad_is_speech(data, (int)len);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeProcessVadStream(
    JNIEnv *env, jobject,
    jfloatArray samples, jobject callback
) {
    jclass cbClass = env->GetObjectClass(callback);
    VadStreamCtx ctx;
    env->GetJavaVM(&ctx.jvm);
    ctx.callback      = env->NewGlobalRef(callback);
    ctx.onSpeechStart = env->GetMethodID(cbClass, "onSpeechStart", "()V");
    ctx.onSpeechEnd   = env->GetMethodID(cbClass, "onSpeechEnd",   "()V");

    jsize  len  = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    std::vector<float> audio(data, data + len);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);

    dai_vad_process_stream(audio.data(), (int)audio.size(),
        vad_on_speech_start, vad_on_speech_end, &ctx);

    env->DeleteGlobalRef(ctx.callback);
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeResetVad(JNIEnv *, jobject) {
    dai_vad_reset();
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeShutdownVad(JNIEnv *, jobject) {
    dai_vad_shutdown();
}

// ── STT ──────────────────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_dev_deviceai_SpeechBridge_nativeInitStt(
    JNIEnv *env, jobject,
    jstring modelPath, jstring language,
    jboolean translate, jint maxThreads, jboolean useGpu,
    jboolean useVad, jboolean singleSegment, jboolean noContext
) {
    std::string path = jstring_to_std(env, modelPath);
    std::string lang = jstring_to_std(env, language);
    return dai_stt_init(
        path.c_str(), lang.c_str(),
        (int)translate, (int)maxThreads, (int)useGpu,
        (int)useVad, (int)singleSegment, (int)noContext
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_deviceai_SpeechBridge_nativeTranscribe(JNIEnv *env, jobject, jstring audioPath) {
    std::string path = jstring_to_std(env, audioPath);
    char *result = dai_stt_transcribe_file(path.c_str());
    jstring out = env->NewStringUTF(result ? result : "");
    dai_speech_free_string(result);
    return out;
}

JNIEXPORT jobject JNICALL
Java_dev_deviceai_SpeechBridge_nativeTranscribeDetailed(JNIEnv *env, jobject, jstring audioPath) {
    std::string path = jstring_to_std(env, audioPath);
    char *json = dai_stt_transcribe_file_detailed(path.c_str());
    std::string json_str = json ? json : "{}";
    dai_speech_free_string(json);
    return build_result(env, json_str);
}

JNIEXPORT jstring JNICALL
Java_dev_deviceai_SpeechBridge_nativeTranscribeAudio(JNIEnv *env, jobject, jfloatArray samples) {
    jsize len    = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    char *result = dai_stt_transcribe(data, (int)len);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    jstring out = env->NewStringUTF(result ? result : "");
    dai_speech_free_string(result);
    return out;
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeTranscribeStream(
    JNIEnv *env, jobject,
    jfloatArray samples, jobject callback
) {
    jclass cbClass = env->GetObjectClass(callback);
    SttStreamCtx ctx;
    env->GetJavaVM(&ctx.jvm);
    ctx.callback  = env->NewGlobalRef(callback);
    ctx.onPartial = env->GetMethodID(cbClass, "onPartialResult", "(Ljava/lang/String;)V");
    ctx.onFinal   = env->GetMethodID(cbClass, "onFinalResult",   "(Ldev/deviceai/TranscriptionResult;)V");
    ctx.onError   = env->GetMethodID(cbClass, "onError",         "(Ljava/lang/String;)V");

    jsize  len  = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    std::vector<float> audio(data, data + len);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);

    dai_stt_transcribe_stream(audio.data(), (int)audio.size(),
        stt_on_partial, stt_on_final, stt_on_error, &ctx);

    env->DeleteGlobalRef(ctx.callback);
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeCancelStt(JNIEnv *, jobject) {
    dai_stt_cancel();
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeShutdownStt(JNIEnv *, jobject) {
    dai_stt_shutdown();
}

// ─── TTS stubs (STT-only builds) ─────────────────────────────────────────────

#ifdef SPEECHKMP_STT_ONLY

JNIEXPORT jboolean JNICALL
Java_dev_deviceai_SpeechBridge_nativeInitTts(
    JNIEnv *, jobject, jstring, jstring, jstring, jint, jfloat, jint, jfloat
) { return JNI_FALSE; }

JNIEXPORT jshortArray JNICALL
Java_dev_deviceai_SpeechBridge_nativeSynthesize(JNIEnv *env, jobject, jstring) {
    return env->NewShortArray(0);
}

JNIEXPORT jboolean JNICALL
Java_dev_deviceai_SpeechBridge_nativeSynthesizeToFile(JNIEnv *, jobject, jstring, jstring) {
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeSynthesizeStream(JNIEnv *env, jobject, jstring, jobject callback) {
    jclass cb = env->GetObjectClass(callback);
    jmethodID onError = env->GetMethodID(cb, "onError", "(Ljava/lang/String;)V");
    env->CallVoidMethod(callback, onError, env->NewStringUTF("TTS not available in this build"));
}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeCancelTts(JNIEnv *, jobject) {}

JNIEXPORT void JNICALL
Java_dev_deviceai_SpeechBridge_nativeShutdownTts(JNIEnv *, jobject) {}

#endif // SPEECHKMP_STT_ONLY

} // extern "C"
