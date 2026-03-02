package dev.deviceai.demo

import androidx.compose.ui.window.ComposeUIViewController
import dev.deviceai.core.DeviceAIRuntime
import dev.deviceai.core.Environment
import platform.UIKit.UIViewController

// Initialised once per process — guards against SwiftUI recreating the
// UIViewControllerRepresentable (e.g. scene lifecycle / dark-mode transitions).
private val runtimeInit by lazy { DeviceAIRuntime.configure(Environment.DEVELOPMENT) }

fun MainViewController(): UIViewController {
    runtimeInit // ensure configure() runs exactly once
    return ComposeUIViewController { App() }
}
