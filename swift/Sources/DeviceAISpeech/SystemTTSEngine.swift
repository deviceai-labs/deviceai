import AVFoundation
import Foundation
import DeviceAI

/// System Text-to-Speech engine using Apple's built-in AVSpeechSynthesizer.
///
/// Zero setup, no model download. Works on all iOS/macOS devices instantly.
/// Supports all system voices and languages.
///
/// ```swift
/// let tts = SystemTTSEngine()
/// try await tts.speak("Hello from DeviceAI")
/// tts.stop()
/// ```
///
/// For higher quality neural voices with custom models, use ``TtsEngine`` instead.
public final class SystemTTSEngine: NSObject, @unchecked Sendable {
    private let synthesizer = AVSpeechSynthesizer()
    private var continuation: CheckedContinuation<Void, Error>?

    /// Current speech language (BCP 47 code, e.g. "en-US").
    public var language: String = "en-US"

    /// Speech rate multiplier (0.0 - 1.0, default 0.5).
    public var rate: Float = AVSpeechUtteranceDefaultSpeechRate

    /// Pitch multiplier (0.5 - 2.0, default 1.0).
    public var pitch: Float = 1.0

    /// Volume (0.0 - 1.0, default 1.0).
    public var volume: Float = 1.0

    /// Optional voice identifier. Nil uses the default voice for the language.
    public var voiceIdentifier: String?

    /// Whether the engine is currently speaking.
    public var isSpeaking: Bool { synthesizer.isSpeaking }

    /// All available system voice identifiers.
    public static var availableVoices: [AVSpeechSynthesisVoice] {
        AVSpeechSynthesisVoice.speechVoices()
    }

    public override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// Speak text aloud. Returns when speech completes.
    ///
    /// Audio plays directly through the device speaker. Use ``stop()`` to cancel.
    ///
    /// - Parameter text: The text to speak.
    /// - Throws: ``DeviceAIError/cancelled`` if stopped before completion.
    public func speak(_ text: String) async throws {
        #if os(iOS) || os(tvOS)
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default, options: [.duckOthers])
        try session.setActive(true)
        #endif

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = resolveVoice()
        utterance.rate = rate
        utterance.pitchMultiplier = pitch
        utterance.volume = volume
        utterance.preUtteranceDelay = 0
        utterance.postUtteranceDelay = 0

        let startMs = currentTimeMs()

        if continuation != nil {
            synthesizer.stopSpeaking(at: .immediate)
        }

        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            self.continuation = cont
            self.synthesizer.speak(utterance)
        }

        DeviceAI.shared.recordEvent(.inferenceComplete(
            module: "tts", modelId: "system-tts",
            latencyMs: currentTimeMs() - startMs,
            outputChars: text.count, finishReason: "stop"
        ))
    }

    /// Stop speaking immediately.
    public func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    // MARK: - Private

    private func resolveVoice() -> AVSpeechSynthesisVoice? {
        if let id = voiceIdentifier {
            return AVSpeechSynthesisVoice(identifier: id)
                ?? AVSpeechSynthesisVoice(language: id)
                ?? AVSpeechSynthesisVoice(language: language)
        }
        return AVSpeechSynthesisVoice(language: language)
    }

    private func currentTimeMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

// MARK: - AVSpeechSynthesizerDelegate

extension SystemTTSEngine: AVSpeechSynthesizerDelegate {
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        continuation?.resume()
        continuation = nil
    }

    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        continuation?.resume(throwing: DeviceAIError.cancelled)
        continuation = nil
    }
}
