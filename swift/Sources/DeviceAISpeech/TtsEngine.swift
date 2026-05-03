import Foundation
import DeviceAI
import CDeviceAI

/// Text-to-Speech engine wrapping sherpa-onnx via dai_tts_* C API.
public final class TtsEngine: @unchecked Sendable {
    private let modelId: String
    private var isInitialized = false

    public init(modelPath: String, tokensPath: String, config: TtsConfig = TtsConfig()) async throws {
        self.modelId = (modelPath as NSString).lastPathComponent
        let startMs = currentTimeMs()

        let ok = dai_tts_init(
            modelPath, tokensPath, config.dataDir, config.voicesPath,
            Int32(config.speakerId ?? -1), config.speechRate
        )
        guard ok else {
            throw DeviceAIError.initFailed(reason: "Failed to load TTS model: \(modelPath)")
        }
        isInitialized = true
        DeviceAI.shared.recordEvent(.modelLoad(module: "tts", modelId: modelId, durationMs: currentTimeMs() - startMs))
    }

    public func synthesize(_ text: String) async throws -> [Int16] {
        guard isInitialized else { throw DeviceAIError.initFailed(reason: "TTS not initialized") }
        let startMs = currentTimeMs()
        var outLen: Int32 = 0
        let result = dai_tts_synthesize(text, &outLen)
        let latencyMs = currentTimeMs() - startMs

        guard let result, outLen > 0 else {
            DeviceAI.shared.recordEvent(.inferenceComplete(module: "tts", modelId: modelId, latencyMs: latencyMs, outputChars: text.count, finishReason: "empty"))
            return []
        }
        let samples = Array(UnsafeBufferPointer(start: result, count: Int(outLen)))
        dai_tts_free_audio(result)
        DeviceAI.shared.recordEvent(.inferenceComplete(module: "tts", modelId: modelId, latencyMs: latencyMs, outputChars: text.count, finishReason: "stop"))
        return samples
    }

    public func synthesizeToFile(_ text: String, outputPath: String) async throws -> Bool {
        guard isInitialized else { throw DeviceAIError.initFailed(reason: "TTS not initialized") }
        let startMs = currentTimeMs()
        let ok = dai_tts_synthesize_to_file(text, outputPath)
        DeviceAI.shared.recordEvent(.inferenceComplete(module: "tts", modelId: modelId, latencyMs: currentTimeMs() - startMs, outputChars: text.count, finishReason: ok ? "stop" : "error"))
        return ok
    }

    public func cancel() { dai_tts_cancel() }

    public func shutdown() {
        guard isInitialized else { return }
        DeviceAI.shared.recordEvent(.modelUnload(module: "tts", modelId: modelId))
        dai_tts_shutdown()
        isInitialized = false
    }

    private func currentTimeMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
