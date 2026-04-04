package dev.deviceai.core.backend

import dev.deviceai.models.PlatformStorage
import dev.deviceai.models.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A registered device and its current device JWT. Persisted across app launches. */
@Serializable
data class DeviceSession(
    val deviceId: String,
    val token: String,
    /** Unix epoch milliseconds when the token expires (30-day rolling window). */
    val expiresAtMs: Long,
    val capabilityTier: String,
) {
    /** `true` if the token has already expired. Device must re-register. */
    val isExpired: Boolean get() = currentTimeMillis() > expiresAtMs

    /**
     * `true` when within 7 days of expiry — eligible for silent token refresh.
     * Call `POST /v1/devices/refresh` to extend the token window.
     */
    val needsRefresh: Boolean get() = currentTimeMillis() > (expiresAtMs - REFRESH_WINDOW_MS)

    companion object {
        private const val REFRESH_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** Persists and loads [DeviceSession] using [PlatformStorage]. */
internal object SessionStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val SESSION_FILE = "deviceai_session.json"

    fun load(): DeviceSession? {
        return try {
            val path = "${PlatformStorage.getModelsDir()}/$SESSION_FILE"
            val content = PlatformStorage.readText(path) ?: return null
            json.decodeFromString<DeviceSession>(content)
        } catch (_: Exception) {
            null
        }
    }

    fun save(session: DeviceSession) {
        try {
            PlatformStorage.ensureDirectoryExists(PlatformStorage.getModelsDir())
            val path = "${PlatformStorage.getModelsDir()}/$SESSION_FILE"
            PlatformStorage.writeText(path, json.encodeToString(DeviceSession.serializer(), session))
        } catch (_: Exception) {
            // Non-fatal — session is re-fetched on next cold start
        }
    }

    fun clear() {
        try {
            val path = "${PlatformStorage.getModelsDir()}/$SESSION_FILE"
            PlatformStorage.deleteFile(path)
        } catch (_: Exception) {}
    }
}
