package dev.deviceai.core.telemetry

import kotlinx.serialization.json.*

/**
 * A single structured telemetry event emitted by the DeviceAI SDK.
 *
 * Events are buffered on-device via [TelemetryEngine] and sent to the backend
 * in batches. Each subtype maps to a specific runtime event.
 */
sealed class TelemetryEvent {
    abstract val type: String
    abstract val timestampMs: Long

    /**
     * A model was loaded into memory. Emitted for LLM, STT, and TTS modules.
     * Collected at [TelemetryLevel.Minimal] and above.
     */
    data class ModelLoad(
        override val timestampMs: Long,
        /** Module name: "llm", "stt", "tts" */
        val module: String,
        val modelId: String,
        val durationMs: Long,
        /** RAM increase in MB at load time. Null if measurement unavailable. */
        val ramDeltaMb: Float? = null,
    ) : TelemetryEvent() {
        override val type: String get() = "model_load"
    }

    /**
     * A model was unloaded / released from memory.
     * Collected at [TelemetryLevel.Minimal] and above.
     */
    data class ModelUnload(
        override val timestampMs: Long,
        val module: String,
        val modelId: String,
    ) : TelemetryEvent() {
        override val type: String get() = "model_unload"
    }

    /**
     * An inference request completed (or was cancelled).
     * Collected at [TelemetryLevel.Minimal] and above.
     */
    data class InferenceComplete(
        override val timestampMs: Long,
        val module: String,
        val modelId: String,
        val latencyMs: Long,
        /** LLM only: generation throughput in tokens/sec. */
        val tokensPerSec: Float? = null,
        /** STT only: duration of audio input in ms. */
        val inputLengthMs: Int? = null,
        /** TTS only: number of characters synthesized. */
        val outputChars: Int? = null,
        /** How the inference ended: "stop", "length", "cancel". */
        val finishReason: String? = null,
    ) : TelemetryEvent() {
        override val type: String get() = "inference_complete"
    }

    /**
     * An OTA model download completed or failed.
     * Collected at [TelemetryLevel.Full] only.
     */
    data class OtaDownload(
        override val timestampMs: Long,
        val modelId: String,
        val version: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val success: Boolean,
        /** Short error code on failure, e.g. "network_error", "checksum_mismatch". */
        val errorCode: String? = null,
    ) : TelemetryEvent() {
        override val type: String get() = "ota_download"
    }

    /**
     * A manifest sync request completed or failed.
     * Collected at [TelemetryLevel.Full] only.
     */
    data class ManifestSync(
        override val timestampMs: Long,
        val success: Boolean,
        val modelCount: Int = 0,
        val errorCode: String? = null,
    ) : TelemetryEvent() {
        override val type: String get() = "manifest_sync"
    }

    /** Converts this event to a [JsonObject] for the telemetry batch payload. */
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", type)
        put("timestamp_ms", timestampMs)
        when (this@TelemetryEvent) {
            is ModelLoad -> {
                put("module", module)
                put("model_id", modelId)
                put("duration_ms", durationMs)
                ramDeltaMb?.let { put("ram_delta_mb", it) }
            }
            is ModelUnload -> {
                put("module", module)
                put("model_id", modelId)
            }
            is InferenceComplete -> {
                put("module", module)
                put("model_id", modelId)
                put("latency_ms", latencyMs)
                tokensPerSec?.let { put("tokens_per_sec", it) }
                inputLengthMs?.let { put("input_length_ms", it) }
                outputChars?.let { put("output_chars", it) }
                finishReason?.let { put("finish_reason", it) }
            }
            is OtaDownload -> {
                put("model_id", modelId)
                put("version", version)
                put("size_bytes", sizeBytes)
                put("duration_ms", durationMs)
                put("success", success)
                errorCode?.let { put("error_code", it) }
            }
            is ManifestSync -> {
                put("success", success)
                put("model_count", modelCount)
                errorCode?.let { put("error_code", it) }
            }
        }
    }
}
