package dev.deviceai.core

/**
 * Environment the SDK is running in.
 * Controls default log verbosity — set this to match your build variant.
 */
enum class Environment {
    /**
     * Local development / debug builds.
     * No API key required. All cloud calls are skipped — models are loaded from
     * an explicit local path. Full log verbosity (DEBUG and above).
     */
    Development,

    /**
     * Staging environment. Points to staging.api.deviceai.dev.
     * Requires an API key. Use for pre-release integration testing.
     * Full log verbosity (DEBUG and above).
     */
    Staging,

    /**
     * Production / release builds. Points to api.deviceai.dev.
     * Requires an API key. WARN and ERROR logs only — no noise in the console.
     */
    Production
}

/**
 * Central entry point for the DeviceAI SDK.
 *
 * Call [configure] **once** at app startup before using any module.
 *
 * ### Android
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         DeviceAIRuntime.configure(Environment.Development)   // or PRODUCTION
 *         PlatformStorage.initialize(this)
 *     }
 * }
 * ```
 *
 * ### iOS
 * ```kotlin
 * fun MainViewController(): UIViewController {
 *     DeviceAIRuntime.configure(Environment.Development)
 *     return ComposeUIViewController { App() }
 * }
 * ```
 *
 * ### Desktop
 * ```kotlin
 * fun main() = application {
 *     DeviceAIRuntime.configure(Environment.Development)
 *     Window(...) { App() }
 * }
 * ```
 */
object DeviceAIRuntime {

    /** The environment this SDK instance was configured with. */
    var environment: Environment = Environment.Production
        private set

    private var configured = false

    /**
     * Configure the SDK. **Must be called exactly once** at app startup before
     * using any DeviceAI module. Calling it a second time throws [IllegalStateException].
     *
     * @param environment Drives default log verbosity:
     *   - [Environment.Development] → DEBUG and above (everything)
     *   - [Environment.Production]  → WARN and above (warnings + errors only)
     * @param logHandler Optional custom log sink. Replaces the default platform
     *   logger (Logcat / NSLog / println). Pass `null` to keep platform default.
     *   Useful for routing SDK logs to Crashlytics, Datadog, Sentry, etc.
     */
    fun configure(
        environment: Environment,
        logHandler: ((LogEvent) -> Unit)? = null
    ) {
        check(!configured) {
            "DeviceAIRuntime.configure() has already been called. " +
                "Call it once at app startup (Application.onCreate / main / MainViewController)."
        }
        this.environment = environment
        this.configured = true
        CoreSDKLogger.configure(environment, logHandler)
        CoreSDKLogger.debug("DeviceAIRuntime", "SDK initialised — env=$environment")
    }

    /** `true` when running in [Environment.Development] (no backend, local models). */
    val isDevelopment: Boolean get() = environment == Environment.Development

    /** `true` when running in [Environment.Staging] or [Environment.Production]. */
    val isCloud: Boolean get() = environment == Environment.Staging || environment == Environment.Production
}
