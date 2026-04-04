package dev.deviceai.core.backend

import dev.deviceai.core.CoreSDKLogger
import dev.deviceai.core.telemetry.TelemetryEvent
import dev.deviceai.models.currentTimeMillis
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * HTTP client for the DeviceAI control-plane API.
 *
 * Handles all three device-facing endpoints:
 * - `POST /v1/devices/register` — device registration (API key auth)
 * - `GET  /v1/manifest`         — model manifest (device JWT auth)
 * - `POST /v1/telemetry/batch`  — telemetry ingest (device JWT auth)
 * - `POST /v1/devices/refresh`  — token refresh (device JWT auth)
 */
internal class BackendClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val jsonCodec = Json { ignoreUnknownKeys = true }

    /**
     * Register this device with the control plane.
     *
     * @param capabilityProfile Map of device capabilities (ram_gb, cpu_cores, has_npu, etc.)
     *   sent to the backend for capability tier scoring and cohort targeting.
     * @return A [DeviceSession] with the device_id and a 30-day JWT.
     */
    suspend fun registerDevice(capabilityProfile: Map<String, Any?>): DeviceSession {
        val profileJson = capabilityProfile.toJsonObject()
        val response = http.post("$baseUrl/v1/devices/register") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("capability_profile", profileJson)
            })
        }
        val body = response.body<JsonObject>()
        return DeviceSession(
            deviceId = body["device_id"]?.jsonPrimitive?.content ?: "",
            token = body["token"]?.jsonPrimitive?.content ?: "",
            expiresAtMs = currentTimeMillis() + TOKEN_LIFETIME_MS,
            capabilityTier = body["capability_tier"]?.jsonPrimitive?.content ?: "mid",
        )
    }

    /**
     * Fetch the model manifest for this device.
     *
     * @param deviceToken The JWT from [DeviceSession.token].
     * @return The current [ManifestResponse] for this device's cohort.
     */
    suspend fun fetchManifest(deviceToken: String): ManifestResponse {
        val response = http.get("$baseUrl/v1/manifest") {
            bearerAuth(deviceToken)
        }
        val raw = response.body<String>()
        return jsonCodec.decodeFromString<ManifestResponse>(raw)
    }

    /**
     * Send a batch of telemetry events to the backend.
     *
     * Fire-and-forget — logs a warning on failure but does not throw.
     * Max 500 events per batch (backend limit).
     *
     * @param deviceToken The JWT from [DeviceSession.token].
     * @param sessionId   A client-generated UUID stable for the app session lifetime.
     * @param events      Events to send. Batches >500 are silently truncated.
     */
    suspend fun ingestTelemetry(
        deviceToken: String,
        sessionId: String,
        events: List<TelemetryEvent>,
    ) {
        val batch = events.take(500)
        val body = buildJsonObject {
            put("session_id", sessionId)
            put("events", buildJsonArray {
                batch.forEach { add(it.toJsonObject()) }
            })
        }
        http.post("$baseUrl/v1/telemetry/batch") {
            bearerAuth(deviceToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /**
     * Refresh the device token when [DeviceSession.needsRefresh] is `true`.
     *
     * @return Updated [DeviceSession] with a new token, or `null` on failure.
     */
    suspend fun refreshToken(session: DeviceSession): DeviceSession? {
        return try {
            val response = http.post("$baseUrl/v1/devices/refresh") {
                bearerAuth(session.token)
            }
            val body = response.body<JsonObject>()
            val newToken = body["token"]?.jsonPrimitive?.content ?: return null
            session.copy(
                token = newToken,
                expiresAtMs = currentTimeMillis() + TOKEN_LIFETIME_MS,
            )
        } catch (e: Exception) {
            CoreSDKLogger.warn("BackendClient", "token refresh failed: ${e.message}")
            null
        }
    }

    fun close() = http.close()

    companion object {
        private const val TOKEN_LIFETIME_MS = 30L * 24 * 60 * 60 * 1000
    }
}

// Helper to convert Map<String, Any?> → JsonObject for capability_profile
private fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (k, v) ->
        when (v) {
            is String  -> put(k, v)
            is Boolean -> put(k, v)
            is Int     -> put(k, v)
            is Long    -> put(k, v)
            is Float   -> put(k, v)
            is Double  -> put(k, v)
            else       -> v?.let { put(k, it.toString()) }
        }
    }
}
