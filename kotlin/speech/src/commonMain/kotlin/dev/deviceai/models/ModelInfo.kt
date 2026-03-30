package dev.deviceai.models

import kotlinx.serialization.Serializable

/**
 * Whisper model size tiers.
 */
enum class WhisperSize(val label: String) {
    TINY("tiny"),
    BASE("base"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE_V1("large-v1"),
    LARGE_V2("large-v2"),
    LARGE_V3("large-v3"),
    LARGE_V3_TURBO("large-v3-turbo");

    companion object {
        fun fromString(s: String): WhisperSize? = entries.find { it.label == s }
    }
}

/**
 * Quantization formats available for Whisper models.
 */
enum class Quantization(val label: String) {
    Q5_1("q5_1"),
    Q8_0("q8_0");

    companion object {
        fun fromString(s: String): Quantization? = entries.find { it.label == s }
    }
}

/**
 * Piper voice quality tiers.
 */
enum class PiperQuality(val label: String) {
    X_LOW("x_low"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromString(s: String): PiperQuality? = entries.find { it.label == s }
    }
}

/**
 * Language metadata for Piper voices.
 */
@Serializable
data class LanguageInfo(
    val code: String,
    val family: String,
    val region: String,
    val nameNative: String,
    val nameEnglish: String,
    val countryEnglish: String
)

/**
 * Whisper GGML model available on HuggingFace.
 * Implements [ModelInfo] from runtime-core.
 */
data class WhisperModelInfo(
    override val id: String,
    override val displayName: String,
    override val sizeBytes: Long,
    val size: WhisperSize,
    val isEnglishOnly: Boolean,
    val quantization: Quantization?,
    val downloadUrl: String
) : ModelInfo

/**
 * Piper ONNX voice model available on HuggingFace.
 * Implements [ModelInfo] from runtime-core.
 */
data class PiperVoiceInfo(
    override val id: String,
    override val displayName: String,
    override val sizeBytes: Long,
    val language: LanguageInfo,
    val quality: PiperQuality,
    val numSpeakers: Int,
    val speakerIdMap: Map<String, Int>,
    val modelUrl: String,
    val configUrl: String
) : ModelInfo

/**
 * sherpa-onnx TTS voice model (VITS or Kokoro).
 *
 * Kokoro models need only [modelUrl] + [tokensUrl] + [voicesUrl] (no espeak-ng-data).
 * VITS models that use espeak phonemization also need [dataDirZipUrl] for the espeak-ng-data directory.
 */
data class TtsVoiceInfo(
    override val id: String,
    override val displayName: String,
    override val sizeBytes: Long,
    /** ISO 639-1 language code, e.g. "en", "zh". */
    val languageCode: String,
    val modelType: TtsModelType,
    /** Direct URL to the ONNX model file. */
    val modelUrl: String,
    /** Direct URL to tokens.txt. */
    val tokensUrl: String,
    /** URL to voices.bin — required for Kokoro, null for VITS. */
    val voicesUrl: String? = null,
    /** Number of speaker IDs supported by this voice model. */
    val numSpeakers: Int = 1
) : ModelInfo

/**
 * Distinguishes sherpa-onnx TTS model architectures.
 * - [KOKORO]: needs model.onnx + tokens.txt + voices.bin; no espeak-ng-data required.
 * - [VITS]: needs model.onnx + tokens.txt; may also need espeak-ng-data for English phonemization.
 */
enum class TtsModelType { VITS, KOKORO }

/**
 * Returns the absolute path to voices.bin for a downloaded Kokoro TTS model,
 * or an empty string if this is a VITS model that does not need voices.bin.
 *
 * Usage:
 * ```kotlin
 * val local = ModelRegistry.download(voice).getOrThrow()
 * SpeechBridge.initTts(
 *     modelPath  = local.modelPath,
 *     tokensPath = local.configPath!!,
 *     config     = TtsConfig(voicesPath = local.ttsVoicesPath())
 * )
 * ```
 */
fun LocalModel.ttsVoicesPath(): String {
    if (modelType != LocalModelType.TTS) return ""
    val dir = modelPath.substringBeforeLast('/')
    val candidate = "$dir/voices.bin"
    return candidate
}

// LocalModelType constants for runtime-speech model types.
// Defined here as companion extensions so runtime-core never needs to be modified
// when new modules are added — Open/Closed Principle.
val LocalModelType.Companion.WHISPER get() = LocalModelType("WHISPER")
val LocalModelType.Companion.PIPER   get() = LocalModelType("PIPER")
val LocalModelType.Companion.TTS     get() = LocalModelType("TTS")
