package dev.deviceai.demo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSError

@OptIn(ExperimentalForeignApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AudioRecorder actual constructor() {

    private val engine = AVAudioEngine()
    private val samples = mutableListOf<Float>()
    private var recording = false

    actual fun startRecording() {
        if (recording) return

        memScoped {
            val sessionErr = alloc<ObjCObjectVar<NSError?>>()
            val ok = AVAudioSession.sharedInstance()
                .setCategory(AVAudioSessionCategoryRecord, error = sessionErr.ptr)
            if (!ok) {
                println("[AudioRecorder] AVAudioSession setCategory failed")
                return
            }
        }

        // Permission is requested from Swift (iOSApp.swift) on first launch.
        // By the time the user taps the mic the dialog has already been shown.
        // If still not granted, bail out so the caller gets an empty FloatArray
        // and can show a clear "check microphone permission" error.
        if (AVAudioSession.sharedInstance().recordPermission
            != AVAudioSessionRecordPermissionGranted
        ) {
            println("[AudioRecorder] Mic permission not granted")
            return
        }

        val inputNode = engine.inputNode
        samples.clear()
        recording = true

        // nil format → AVAudioEngine uses the hardware native rate automatically.
        // We read the real rate from each buffer and decimate to 16 kHz for Whisper.
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 4096u,
            format = null
        ) { buffer, _ ->
            buffer ?: return@installTapOnBus
            val channelData = buffer.floatChannelData ?: return@installTapOnBus
            val frameLength = buffer.frameLength.toInt()
            val channel = channelData[0] ?: return@installTapOnBus
            val bufRate = buffer.format.sampleRate.toFloat()
            if (bufRate == 16000f) {
                for (i in 0 until frameLength) samples.add(channel[i])
            } else {
                val step = bufRate / 16000f
                var pos = 0f
                while (pos < frameLength) {
                    samples.add(channel[pos.toInt()])
                    pos += step
                }
            }
        }

        engine.prepare()

        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val started = engine.startAndReturnError(err.ptr)
            if (!started) {
                recording = false
                inputNode.removeTapOnBus(0u)
                println("[AudioRecorder] AVAudioEngine failed to start")
            }
        }
    }

    actual fun stopRecording(): FloatArray {
        if (!recording) return FloatArray(0)
        recording = false
        engine.inputNode.removeTapOnBus(0u)
        engine.stop()
        return samples.toFloatArray()
    }
}
