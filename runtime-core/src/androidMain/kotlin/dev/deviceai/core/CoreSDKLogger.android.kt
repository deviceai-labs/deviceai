package dev.deviceai.core

import android.util.Log

internal actual fun platformLog(event: LogEvent) {
    val msg = if (event.throwable != null)
        "${event.message}\n${event.throwable.stackTraceToString()}"
    else event.message

    when (event.level) {
        LogLevel.DEBUG -> Log.d(event.tag, msg)
        LogLevel.INFO  -> Log.i(event.tag, msg)
        LogLevel.WARN  -> Log.w(event.tag, msg)
        LogLevel.ERROR -> Log.e(event.tag, msg)
    }
}
