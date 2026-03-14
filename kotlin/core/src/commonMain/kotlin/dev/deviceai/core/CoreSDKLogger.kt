package dev.deviceai.core

import dev.deviceai.models.currentTimeMillis

/**
 * Severity levels for SDK log events.
 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A structured log event emitted by the DeviceAI SDK.
 *
 * @param level Severity of the event
 * @param tag Source tag, e.g. "ModelRegistry", "LlmBridge", "SpeechBridge"
 * @param message Human-readable description
 * @param throwable Optional exception attached to WARN/ERROR events
 * @param timestampMs Wall-clock time of the event in milliseconds
 */
data class LogEvent(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val timestampMs: Long
)

/**
 * Central logger for all DeviceAI SDK modules.
 *
 * By default, events are forwarded to the platform logger (Logcat on Android,
 * NSLog on iOS, println on JVM/desktop). Attach a custom handler to route
 * events to your own logging infrastructure (e.g. Firebase Crashlytics, Datadog).
 *
 * Usage:
 * ```kotlin
 * // Log level is set automatically by DeviceAIRuntime.configure().
 * // Attach your own handler to route events to external infrastructure:
 * CoreSDKLogger.setHandler { event ->
 *     MyAnalytics.log(event.tag, event.message)
 * }
 * ```
 *
 * Modules log via the convenience functions:
 * ```kotlin
 * CoreSDKLogger.info("ModelRegistry", "Model downloaded: llama-3.2-1b")
 * CoreSDKLogger.error("LlmBridge", "Init failed", exception)
 * ```
 */
object CoreSDKLogger {

    /**
     * Minimum level to emit. Events below this level are silently dropped.
     * Controlled by [DeviceAIRuntime.configure] — not directly settable by SDK consumers.
     */
    @kotlin.concurrent.Volatile
    var minLevel: LogLevel = LogLevel.INFO
        internal set

    @kotlin.concurrent.Volatile
    private var customHandler: ((LogEvent) -> Unit)? = null

    /**
     * Called by [DeviceAIRuntime.configure] to apply environment-driven defaults in one shot.
     * Keeps DeviceAIRuntime from reaching into CoreSDKLogger's internals directly.
     */
    internal fun configure(environment: Environment, handler: ((LogEvent) -> Unit)?) {
        minLevel = when (environment) {
            Environment.Development -> LogLevel.DEBUG
            Environment.Staging     -> LogLevel.DEBUG
            Environment.Production  -> LogLevel.WARN
        }
        customHandler = handler
    }

    /**
     * Attach a custom log handler. Replaces the default platform logger.
     * Pass null to restore default platform logging.
     */
    fun setHandler(handler: ((LogEvent) -> Unit)?) {
        customHandler = handler
    }

    fun debug(tag: String, message: String) =
        log(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) =
        log(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minLevel.ordinal) return
        val event = LogEvent(level, tag, message, throwable, currentTimeMillis())
        customHandler?.invoke(event) ?: platformLog(event)
    }
}

/**
 * Platform-specific log output.
 * - Android: android.util.Log
 * - iOS: NSLog
 * - JVM: println to stdout/stderr
 */
internal expect fun platformLog(event: LogEvent)
