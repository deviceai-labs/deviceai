package dev.deviceai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import dev.deviceai.core.DeviceAI
import dev.deviceai.core.Environment
import dev.deviceai.models.PlatformStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize DeviceAI SDK. Switch to Environment.Production + apiKey for release builds.
        DeviceAI.initialize(context = this) {
            environment = Environment.Development
        }

        // Must run before ModelRegistry.initialize() which is triggered lazily
        // inside SpeechViewModel.initialize() from HomeScreen's LaunchedEffect.
        PlatformStorage.initialize(this)

        // Request microphone access upfront so it's ready when the user taps Record.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
        }

        setContent { App() }
    }
}
