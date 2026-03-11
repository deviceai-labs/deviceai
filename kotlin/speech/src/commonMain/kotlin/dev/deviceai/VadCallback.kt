package dev.deviceai

/**
 * Callbacks for real-time voice activity detection stream.
 *
 * Use with [SpeechBridge.processVadStream] to gate microphone recording:
 * start STT when [onSpeechStart] fires, stop when [onSpeechEnd] fires.
 */
interface VadCallback {
    /** Called when speech onset is detected. */
    fun onSpeechStart()

    /** Called when end of speech is detected (~300ms after last voiced frame). */
    fun onSpeechEnd()
}
