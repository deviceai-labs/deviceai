package dev.deviceai.core

internal actual fun platformLog(event: LogEvent) {
    val prefix = "[DeviceAI][${event.level}][${event.tag}]"
    val msg = "$prefix ${event.message}"
    if (event.level == LogLevel.ERROR || event.level == LogLevel.WARN) {
        System.err.println(msg)
        event.throwable?.printStackTrace(System.err)
    } else {
        println(msg)
    }
}
