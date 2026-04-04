package dev.deviceai.core

import dev.deviceai.core.telemetry.TelemetryLevel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Cloud / control-plane configuration for the DeviceAI SDK.
 *
 * Passed to [DeviceAI.initialize] via a DSL block:
 * ```kotlin
 * DeviceAI.initialize(context, apiKey = "dai_live_...") {
 *     environment = Environment.Production
 *     telemetry   = TelemetryLevel.Minimal
 *     wifiOnly    = true
 *     appVersion  = BuildConfig.VERSION_NAME
 *     capabilityProfile = mapOf("ram_gb" to 8.0, "cpu_cores" to 8, "has_npu" to true)
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
    val telemetry: TelemetryLevel,
    val wifiOnly: Boolean,
    val manifestSyncInterval: Duration,
    val capabilityProfile: Map<String, Any?>,
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
         * Telemetry reporting level. Defaults to [TelemetryLevel.Off].
         * Enable only after obtaining explicit user consent (GDPR/CCPA).
         *
         * - [TelemetryLevel.Off]     — nothing sent (default)
         * - [TelemetryLevel.Minimal] — latency + model load/unload only
         * - [TelemetryLevel.Full]    — all events including OTA and manifest sync
         */
        var telemetry: TelemetryLevel = TelemetryLevel.Off

        /**
         * When `true` the SDK defers model downloads and telemetry flushes until
         * a Wi-Fi connection is available. Defaults to `true`.
         */
        var wifiOnly: Boolean = true

        /**
         * How often the SDK re-fetches the manifest in the background.
         * Shorter intervals mean faster propagation of kill switches and
         * model updates at the cost of more API calls. Defaults to 6 hours.
         */
        var manifestSyncInterval: Duration = 6.hours

        /**
         * Device hardware capabilities sent to the backend for capability tier scoring
         * and model cohort targeting.
         *
         * Recognised keys (backend scoring):
         * - `"ram_gb"`   — Float: total device RAM in GB (e.g. `8.0`)
         * - `"cpu_cores"` — Int: logical CPU core count (e.g. `8`)
         * - `"has_npu"`  — Boolean: whether a dedicated NPU/ANE is present
         *
         * You may include additional keys — they are stored as-is for custom targeting.
         */
        var capabilityProfile: Map<String, Any?> = emptyMap()

        /**
         * The app version string included in the capability profile.
         * Typically `BuildConfig.VERSION_NAME` on Android or
         * `Bundle.main.infoDictionary["CFBundleShortVersionString"]` on iOS.
         */
        var appVersion: String? = null

        /**
         * Arbitrary string attributes for cohort targeting
         * (e.g. `"user_tier" to "premium"`, `"locale" to "en-US"`).
         */
        var appAttributes: Map<String, String> = emptyMap()

        internal fun build(): CloudConfig {
            val resolvedUrl = baseUrl ?: when (environment) {
                Environment.Development -> "http://localhost:8080"
                Environment.Staging     -> "https://staging.api.deviceai.dev"
                Environment.Production  -> "https://api.deviceai.dev"
            }
            val fullProfile = buildMap<String, Any?> {
                putAll(capabilityProfile)
                appVersion?.let { put("app_version", it) }
                putAll(appAttributes)
            }
            return CloudConfig(
                environment          = environment,
                apiKey               = apiKey,
                baseUrl              = resolvedUrl,
                telemetry            = telemetry,
                wifiOnly             = wifiOnly,
                manifestSyncInterval = manifestSyncInterval,
                capabilityProfile    = fullProfile,
                appVersion           = appVersion,
                appAttributes        = appAttributes,
            )
        }
    }
}
