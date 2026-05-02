import Foundation
import DeviceAI

/// Text-to-Speech engine wrapping sherpa-onnx via dai_tts_* C API.
///
/// ```swift
/// let tts = try await TtsEngine(modelPath: path, tokensPath: tokens)
/// let audio = try await tts.synthesize("Hello from DeviceAI")
/// tts.shutdown()
/// ```
public final class TtsEngine: @unchecked Sendable {
    private let modelId: String
    private let config: TtsConfig
    private var isInitialized = false

    /// Initialize the TTS engine.
    ///
    /// - Parameters:
    ///   - modelPath: Path to .onnx model file.
    ///   - tokensPath: Path to tokens.txt vocabulary file.
    ///   - config: TTS configuration.
    public init(modelPath: String, tokensPath: String, config: TtsConfig = TtsConfig()) async throws {
        self.config = config
        self.modelId = (modelPath as NSString).lastPathComponent

        let startMs = Int64(Date().timeIntervalSince1970 * 1000)

        // TODO: Call dai_tts_init via C interop when XCFrameworks are available
        isInitialized = false

        let durationMs = Int64(Date().timeIntervalSince1970 * 1000) - startMs
        DeviceAI.shared.recordEvent(.modelLoad(module: "tts", modelId: modelId, durationMs: durationMs))
    }

    /// Synthesize text to audio samples.
    ///
    /// - Parameter text: Text to synthesize.
    /// - Returns: Int16 audio samples (mono, ~22050 Hz).
    public func synthesize(_ text: String) async throws -> [Int16] {
        let startMs = Int64(Date().timeIntervalSince1970 * 1000)

        // TODO: Call dai_tts_synthesize via C interop
        throw DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required")
    }

    /// Synthesize text directly to a WAV file.
    public func synthesizeToFile(_ text: String, outputPath: String) async throws -> Bool {
        // TODO: Call dai_tts_synthesize_to_file via C interop
        throw DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required")
    }

    /// Streaming synthesis with audio chunk callbacks.
    public func synthesizeStream(_ text: String) -> AsyncThrowingStream<[Int16], Error> {
        AsyncThrowingStream { continuation in
            // TODO: Call dai_tts_synthesize_stream via C interop
            continuation.finish(throwing: DeviceAIError.initFailed(reason: "Native engine not yet linked"))
        }
    }

    /// Cancel ongoing synthesis.
    public func cancel() {
        // TODO: dai_tts_cancel()
    }

    /// Release TTS resources and unload model.
    public func shutdown() {
        DeviceAI.shared.recordEvent(.modelUnload(module: "tts", modelId: modelId))
        // TODO: dai_tts_shutdown()
        isInitialized = false
    }
}
