package dev.deviceai

/**
 * Configuration for Voice Activity Detection (Silero VAD).
 */
data class VadConfig(
    /**
     * Speech probability threshold [0.0, 1.0].
     * A window is considered speech if Silero's output probability >= threshold.
     * Lower values = more sensitive (catches quiet speech, more false positives).
     * Higher values = more conservative (misses quiet speech, fewer false positives).
     */
    val threshold: Float = 0.5f,

    /**
     * Input audio sample rate. Silero VAD supports 8000 Hz and 16000 Hz only.
     * STT pipeline always works at 16000 Hz — keep default unless feeding 8kHz audio.
     */
    val sampleRate: Int = 16000
)
