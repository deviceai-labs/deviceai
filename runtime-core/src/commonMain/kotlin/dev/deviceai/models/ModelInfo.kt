package dev.deviceai.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Common interface for all downloadable model types across DeviceAI modules.
 */
interface ModelInfo {
    val id: String
    val displayName: String
    val sizeBytes: Long
}

/**
 * A model that has been downloaded and is available locally.
 *
 * @param modelId Unique identifier matching the source [ModelInfo.id]
 * @param modelType Type discriminator — e.g. WHISPER, PIPER, LLM
 * @param modelPath Absolute path to the primary model file on disk
 * @param configPath Absolute path to a secondary config file, if required (e.g. Piper .json)
 * @param downloadedAt Unix timestamp (ms) when the model was downloaded
 */
@Serializable
data class LocalModel(
    val modelId: String,
    val modelType: LocalModelType,
    val modelPath: String,
    val configPath: String? = null,
    val downloadedAt: Long
)

/**
 * Type discriminator for locally stored models. Serialized as a plain string ("WHISPER", "PIPER").
 *
 * Implemented as a data class (not an enum) so each module can define its own
 * type constant without modifying runtime-core — Open/Closed Principle.
 *
 * Each module declares its constants as companion-object extensions:
 * ```kotlin
 * val LocalModelType.Companion.WHISPER get() = LocalModelType("WHISPER")
 * val LocalModelType.Companion.PIPER   get() = LocalModelType("PIPER")
 * ```
 *
 * Serialized as a plain string so existing persisted metadata deserializes without migration.
 */
@Serializable(with = LocalModelTypeSerializer::class)
data class LocalModelType(val id: String) {
    companion object
}

private object LocalModelTypeSerializer : KSerializer<LocalModelType> {
    override val descriptor = PrimitiveSerialDescriptor("LocalModelType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalModelType) = encoder.encodeString(value.id)
    override fun deserialize(decoder: Decoder) = LocalModelType(decoder.decodeString())
}
