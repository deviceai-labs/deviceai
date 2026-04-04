package dev.deviceai.core

import platform.Foundation.NSUUID

actual fun generateSessionId(): String = NSUUID().UUIDString()
