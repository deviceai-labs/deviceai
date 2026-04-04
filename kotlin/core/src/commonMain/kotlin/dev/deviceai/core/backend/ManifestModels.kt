package dev.deviceai.core.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from `GET /v1/manifest`. */
@Serializable
data class ManifestResponse(
    @SerialName("device_id")  val deviceId: String,
    @SerialName("app_id")     val appId: String,
    val tier: String,
    val models: List<ManifestEntry>,
    val signature: String,
)

/** A single model assignment in the manifest. */
@Serializable
data class ManifestEntry(
    /** SDK module this model serves: "llm", "speech", "tts" */
    val module: String,
    @SerialName("model_id")   val modelId: String,
    val version: String,
    val sha256: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    /** Cloudflare R2 object key — resolve via `/v1/artifacts/{sha256}`. */
    @SerialName("cdn_path")   val cdnPath: String,
    @SerialName("rollout_id") val rolloutId: String,
)
