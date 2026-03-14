package dev.deviceai.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Cloud / control-plane configuration for the DeviceAI SDK.
 *
 * Passed to [DeviceAI.initialize] via a DSL block:
 * ```kotlin
 * DeviceAI.initialize(context, apiKey = "dai_live_...") {
 *     environment = Environment.Staging
 *     telemetry   = Telemetry.Enabled
 *     wifiOnly    = true
 *     appVersion  = BuildConfig.VERSION_NAME
 *     appAttributes = mapOf("user_tier" to "premium")
 * }
 * ```
 *
 * In [Environment.Development] no API key is required and all cloud calls
 * are skipped — the SDK runs fully offline against a local model path.
 */
class CloudConfig private constructor(
    val environment: Environment,
    val apiKey: String?,
    val baseUrl: String,
    val telemetry: Telemetry,
    val wifiOnly: Boolean,
    val manifestSyncInterval: Duration,
    val appVersion: String?,
    val appAttributes: Map<String, String>,
) {

    class Builder internal constructor(private val apiKey: String? = null) {

        /** Target environment. Defaults to [Environment.Production]. */
        var environment: Environment = Environment.Production

        /**
         * Override the backend base URL. Leave null to use the default for the
         * selected [environment]:
         * - Development → `http://localhost:8080`
         * - Staging     → `https://staging.api.deviceai.dev`
         * - Production  → `https://api.deviceai.dev`
         */
        var baseUrl: String? = null

        /**
         * Telemetry reporting. Defaults to [Telemetry.Disabled].
         * Set to [Telemetry.Enabled] only after obtaining user consent (GDPR).
         */
        var telemetry: Telemetry = Telemetry.Disabled

        /**
         * When `true` the SDK defers model downloads until a Wi-Fi connection
         * is available. Defaults to `true`.
         */
        var wifiOnly: Boolean = true

        /**
         * How often the SDK re-fetches the manifest in the background.
         * Shorter intervals mean faster propagation of kill switches and
         * model updates at the cost of more API calls.
         * Defaults to 6 hours.
         */
        var manifestSyncInterval: Duration = 6.hours

        /**
         * The app version string to include in the device capability profile
         * sent to the backend. Typically `BuildConfig.VERSION_NAME` on Android
         * or `Bundle.main.infoDictionary["CFBundleShortVersionString"]` on iOS.
         */
        var appVersion: String? = null

        /**
         * Arbitrary key-value attributes sent in the device capability profile.
         * Use this to pass app-level context that the backend can use for cohort
         * targeting (e.g. `"user_tier" to "premium"`, `"locale" to "en-US"`).
         */
        var appAttributes: Map<String, String> = emptyMap()

        internal fun build(): CloudConfig {
            val resolvedUrl = baseUrl ?: when (environment) {
                Environment.Development -> "http://localhost:8080"
                Environment.Staging     -> "https://staging.api.deviceai.dev"
                Environment.Production  -> "https://api.deviceai.dev"
            }
            return CloudConfig(
                environment           = environment,
                apiKey                = apiKey,
                baseUrl               = resolvedUrl,
                telemetry             = telemetry,
                wifiOnly              = wifiOnly,
                manifestSyncInterval  = manifestSyncInterval,
                appVersion            = appVersion,
                appAttributes         = appAttributes,
            )
        }
    }
}

/**
 * Controls whether the SDK sends telemetry events to the DeviceAI backend.
 *
 * Telemetry is **disabled by default** to comply with GDPR and similar regulations.
 * Enable it only after obtaining explicit user consent.
 *
 * What is collected when enabled:
 * - Model load / unload events (duration, RAM delta)
 * - Inference events (latency, tokens/sec, finish reason)
 * - OTA download events (start, complete, failure)
 * - Manifest sync events
 *
 * What is **never** collected:
 * - Prompt or response content
 * - Any personally identifiable information
 */
enum class Telemetry {
    /** No telemetry is buffered or sent. Default. */
    Disabled,

    /** Telemetry is buffered on-device and flushed in batches on Wi-Fi + charging. */
    Enabled,
}
