package dev.deviceai.core

/**
 * Environment the SDK is running in.
 * Controls default log verbosity — set this to match your build variant.
 */
enum class Environment {
    /**
     * Development / debug builds.
     * Emits DEBUG, INFO, WARN, and ERROR — full verbosity.
     */
    DEVELOPMENT,

    /**
     * Production / release builds.
     * Emits WARN and ERROR only — no noise in production console logs.
     */
    PRODUCTION
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
 *         DeviceAIRuntime.configure(Environment.DEVELOPMENT)   // or PRODUCTION
 *         PlatformStorage.initialize(this)
 *     }
 * }
 * ```
 *
 * ### iOS
 * ```kotlin
 * fun MainViewController(): UIViewController {
 *     DeviceAIRuntime.configure(Environment.DEVELOPMENT)
 *     return ComposeUIViewController { App() }
 * }
 * ```
 *
 * ### Desktop
 * ```kotlin
 * fun main() = application {
 *     DeviceAIRuntime.configure(Environment.DEVELOPMENT)
 *     Window(...) { App() }
 * }
 * ```
 */
object DeviceAIRuntime {

    /** The environment this SDK instance was configured with. */
    var environment: Environment = Environment.PRODUCTION
        private set

    private var configured = false

    /**
     * Configure the SDK. **Must be called exactly once** at app startup before
     * using any DeviceAI module. Calling it a second time throws [IllegalStateException].
     *
     * @param environment Drives default log verbosity:
     *   - [Environment.DEVELOPMENT] → DEBUG and above (everything)
     *   - [Environment.PRODUCTION]  → WARN and above (warnings + errors only)
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

    /** `true` when running in [Environment.DEVELOPMENT]. */
    val isDevelopment: Boolean get() = environment == Environment.DEVELOPMENT
}
