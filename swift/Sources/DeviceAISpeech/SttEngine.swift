import Foundation
import DeviceAI

/// Speech-to-Text engine wrapping whisper.cpp via dai_stt_* C API.
///
/// ```swift
/// let engine = try await SttEngine(modelPath: path, config: .init(language: "en"))
/// let text = try await engine.transcribe(samples: audioBuffer)
/// engine.shutdown()
/// ```
public final class SttEngine: @unchecked Sendable {
    private let modelId: String
    private let config: SttConfig
    private var isInitialized = false

    /// Initialize the STT engine with a whisper model.
    ///
    /// - Parameters:
    ///   - modelPath: Absolute path to .bin model file (ggml format).
    ///   - config: STT configuration.
    /// - Throws: `DeviceAIError.initFailed` if model loading fails.
    public init(modelPath: String, config: SttConfig = SttConfig()) async throws {
        self.config = config
        self.modelId = (modelPath as NSString).lastPathComponent

        let startMs = Int64(Date().timeIntervalSince1970 * 1000)

        // TODO: Call dai_stt_init via C interop when XCFrameworks are available
        // dai_stt_init(modelPath, config.language, config.translateToEnglish,
        //              config.maxThreads, config.useGpu, config.useVad,
        //              config.singleSegment, config.noContext)
        isInitialized = false // Will be true when native engine is linked

        let durationMs = Int64(Date().timeIntervalSince1970 * 1000) - startMs
        DeviceAI.shared.recordEvent(.modelLoad(module: "stt", modelId: modelId, durationMs: durationMs))
    }

    /// Transcribe raw PCM audio samples.
    ///
    /// - Parameter samples: Float array of audio samples (16kHz, mono, normalized -1.0 to 1.0).
    /// - Returns: Transcribed text.
    /// - Throws: `DeviceAIError.inferenceFailed` on failure.
    public func transcribe(samples: [Float]) async throws -> String {
        let startMs = Int64(Date().timeIntervalSince1970 * 1000)
        let audioDurationMs = Int(Float(samples.count) / 16000.0 * 1000.0)

        // TODO: Call dai_stt_transcribe_audio via C interop
        // let result = dai_stt_transcribe_audio(samples, samples.count)
        throw DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required")
    }

    /// Transcribe a WAV file.
    ///
    /// - Parameter audioPath: Path to WAV file (16kHz, mono, 16-bit PCM).
    /// - Returns: Transcribed text.
    public func transcribe(audioPath: String) async throws -> String {
        // TODO: Call dai_stt_transcribe via C interop
        throw DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required")
    }

    /// Transcribe with detailed results including segments and timestamps.
    public func transcribeDetailed(audioPath: String) async throws -> TranscriptionResult {
        // TODO: Call dai_stt_transcribe_detailed via C interop
        throw DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required")
    }

    /// Transcribe with streaming partial results.
    public func transcribeStream(samples: [Float]) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            // TODO: Call dai_stt_transcribe_stream via C interop
            continuation.finish(throwing: DeviceAIError.initFailed(reason: "Native engine not yet linked"))
        }
    }

    /// Cancel ongoing transcription.
    public func cancel() {
        // TODO: dai_stt_cancel()
    }

    /// Release STT resources and unload model.
    public func shutdown() {
        DeviceAI.shared.recordEvent(.modelUnload(module: "stt", modelId: modelId))
        // TODO: dai_stt_shutdown()
        isInitialized = false
    }
}
