package dev.deviceai.demo

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Android microphone recorder.
 * Records 16 kHz mono PCM audio and returns normalized float samples.
 */
class AudioRecorder {

    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    private val collectedSamples = mutableListOf<Short>()
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false

    fun startRecording() {
        if (isRecording) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer < 0) sampleRate * 2 else minBuffer * 2

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            return
        }

        collectedSamples.clear()
        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(collectedSamples) {
                        for (i in 0 until read) collectedSamples.add(buffer[i])
                    }
                }
            }
        }.also { it.start() }
    }

    fun stopRecording(): FloatArray {
        if (!isRecording) return FloatArray(0)
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        return synchronized(collectedSamples) {
            FloatArray(collectedSamples.size) { i -> collectedSamples[i] / 32768f }
        }
    }
}
