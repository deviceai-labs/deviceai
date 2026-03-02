package dev.deviceai.demo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.deviceai.core.DeviceAIRuntime
import dev.deviceai.core.Environment

fun main() = application {
    // Configure SDK environment at startup. Switch to PRODUCTION for release builds.
    DeviceAIRuntime.configure(Environment.DEVELOPMENT)
    Window(
        onCloseRequest = ::exitApplication,
        title = "DeviceAI",
        state = rememberWindowState(width = 480.dp, height = 760.dp)
    ) {
        App()
    }
}
