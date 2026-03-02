package dev.deviceai.core

import platform.Foundation.NSLog

internal actual fun platformLog(event: LogEvent) {
    val prefix = "[DeviceAI][${event.level}][${event.tag}]"
    NSLog("$prefix ${event.message}")
    event.throwable?.let { NSLog("$prefix ${it.message}") }
}
