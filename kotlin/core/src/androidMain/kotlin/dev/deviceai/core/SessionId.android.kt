package dev.deviceai.core

import java.util.UUID

actual fun generateSessionId(): String = UUID.randomUUID().toString()
