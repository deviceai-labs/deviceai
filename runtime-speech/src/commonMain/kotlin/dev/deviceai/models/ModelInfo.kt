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

// LocalModelType constants for runtime-speech model types.
// Defined here as companion extensions so runtime-core never needs to be modified
// when new modules are added — Open/Closed Principle.
val LocalModelType.Companion.WHISPER get() = LocalModelType("WHISPER")
val LocalModelType.Companion.PIPER   get() = LocalModelType("PIPER")
