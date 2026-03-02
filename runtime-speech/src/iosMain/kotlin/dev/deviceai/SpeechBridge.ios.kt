package dev.deviceai

import androidx.compose.runtime.Composable
import dev.deviceai.native.*
import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * iOS actual implementation of [SpeechBridge] — shared across arm64, x64, and simulatorArm64.
 *
 * Bridges Kotlin calls to the C API exposed via speech_ios.def (cinterop).
 * JSON parsing of transcription results is delegated to [TranscriptionJsonParser].
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual object SpeechBridge {

    // ══════════════════════════════════════════════════════════════
    //                    SPEECH-TO-TEXT (STT)
    // ══════════════════════════════════════════════════════════════

    actual fun initStt(modelPath: String, config: SttConfig): Boolean {
        return speech_stt_init(
            modelPath,
            config.language,
            config.translateToEnglish,
            config.maxThreads,
            config.useGpu,
            config.useVad
        )
    }

    actual fun transcribe(audioPath: String): String {
        val result = speech_stt_transcribe(audioPath)
        return result?.toKString()?.also { speech_free_string(result) } ?: ""
    }

    actual fun transcribeDetailed(audioPath: String): TranscriptionResult {
        val jsonResult = speech_stt_transcribe_detailed(audioPath)
        val jsonStr = jsonResult?.toKString()?.also { speech_free_string(jsonResult) } ?: "{}"
        return TranscriptionJsonParser.parse(jsonStr)
    }

    actual fun transcribeAudio(samples: FloatArray): String {
        memScoped {
            val nativeSamples = allocArray<FloatVar>(samples.size)
            samples.forEachIndexed { index, value -> nativeSamples[index] = value }
            val result = speech_stt_transcribe_audio(nativeSamples, samples.size)
            return result?.toKString()?.also { speech_free_string(result) } ?: ""
        }
    }

    actual fun transcribeStream(samples: FloatArray, callback: SttStream) {
        memScoped {
            val nativeSamples = allocArray<FloatVar>(samples.size)
            samples.forEachIndexed { index, value -> nativeSamples[index] = value }

            val ref = StableRef.create(callback)

            val onPartial = staticCFunction { text: CPointer<ByteVar>?, userData: COpaquePointer? ->
                val cb = userData!!.asStableRef<SttStream>().get()
                cb.onPartialResult(text?.toKString() ?: "")
            }

            val onFinal = staticCFunction { jsonResult: CPointer<ByteVar>?, userData: COpaquePointer? ->
                val cb = userData!!.asStableRef<SttStream>().get()
                cb.onFinalResult(
                    TranscriptionJsonParser.parse(jsonResult?.toKString() ?: "{}")
                )
            }

            val onError = staticCFunction { message: CPointer<ByteVar>?, userData: COpaquePointer? ->
                val cb = userData!!.asStableRef<SttStream>().get()
                cb.onError(message?.toKString() ?: "Unknown error")
            }

            speech_stt_transcribe_stream(
                nativeSamples, samples.size,
                onPartial, onFinal, onError,
                ref.asCPointer()
            )

            ref.dispose()
        }
    }

    actual fun cancelStt() = speech_stt_cancel()

    actual fun shutdownStt() = speech_stt_shutdown()

    // ══════════════════════════════════════════════════════════════
    //                    TEXT-TO-SPEECH (TTS)
    // ══════════════════════════════════════════════════════════════

    actual fun initTts(modelPath: String, configPath: String, config: TtsConfig): Boolean {
        return speech_tts_init(
            modelPath,
            configPath,
            config.espeakDataPath ?: "",
            config.speakerId ?: -1,
            config.speechRate,
            config.sampleRate,
            config.sentenceSilence
        )
    }

    actual fun synthesize(text: String): ShortArray {
        memScoped {
            val outLength = alloc<IntVar>()
            val result = speech_tts_synthesize(text, outLength.ptr)
            if (result == null) return shortArrayOf()
            val samples = ShortArray(outLength.value) { result[it] }
            speech_free_audio(result)
            return samples
        }
    }

    actual fun synthesizeToFile(text: String, outputPath: String): Boolean =
        speech_tts_synthesize_to_file(text, outputPath)

    actual fun synthesizeStream(text: String, callback: TtsStream) {
        val ref = StableRef.create(callback)

        val onChunk = staticCFunction { samples: CPointer<ShortVar>?, nSamples: Int, userData: COpaquePointer? ->
            val cb = userData!!.asStableRef<TtsStream>().get()
            if (samples != null && nSamples > 0) {
                cb.onAudioChunk(ShortArray(nSamples) { samples[it] })
            }
        }

        val onComplete = staticCFunction { userData: COpaquePointer? ->
            userData!!.asStableRef<TtsStream>().get().onComplete()
        }

        val onError = staticCFunction { message: CPointer<ByteVar>?, userData: COpaquePointer? ->
            val cb = userData!!.asStableRef<TtsStream>().get()
            cb.onError(message?.toKString() ?: "Unknown error")
        }

        speech_tts_synthesize_stream(text, onChunk, onComplete, onError, ref.asCPointer())
        ref.dispose()
    }

    actual fun cancelTts() = speech_tts_cancel()

    actual fun shutdownTts() = speech_tts_shutdown()

    // ══════════════════════════════════════════════════════════════
    //                         UTILITIES
    // ══════════════════════════════════════════════════════════════

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        val bundle = NSBundle.mainBundle
        val resourcePath = bundle.pathForResource(
            modelFileName.substringBeforeLast("."),
            modelFileName.substringAfterLast(".")
        )
        if (resourcePath != null) return resourcePath

        val fileManager = NSFileManager.defaultManager

        @Suppress("UNCHECKED_CAST")
        val documentsUrl = (fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask) as List<NSURL>)
            .firstOrNull()
        if (documentsUrl != null) {
            val filePath = documentsUrl.path + "/$modelFileName"
            if (fileManager.fileExistsAtPath(filePath)) return filePath
        }

        @Suppress("UNCHECKED_CAST")
        val cacheUrl = (fileManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask) as List<NSURL>)
            .firstOrNull()
        if (cacheUrl != null) {
            val cachePath = cacheUrl.path + "/$modelFileName"
            if (fileManager.fileExistsAtPath(cachePath)) return cachePath
        }

        return modelFileName
    }

    actual fun shutdown() = speech_shutdown_all()
}
