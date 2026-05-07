import Foundation
import DeviceAI
import CDeviceAI

/// Speech-to-Text engine wrapping whisper.cpp via dai_stt_* C API.
///
/// ```swift
/// let engine = try await SttEngine(modelPath: path, config: .init(language: "en"))
/// let text = try await engine.transcribe(samples: audioBuffer)
/// engine.shutdown()
/// ```
public final class SttEngine: @unchecked Sendable {
    private let modelId: String
    private var isInitialized = false

    /// Initialize the STT engine with a whisper model.
    public init(modelPath: String, config: SttConfig = SttConfig()) async throws {
        self.modelId = (modelPath as NSString).lastPathComponent

        let startMs = currentTimeMs()

        let ok = dai_stt_init(
            modelPath,
            config.language,
            config.translateToEnglish,
            Int32(config.maxThreads),
            config.useGpu,
            config.useVad,
            config.singleSegment,
            config.noContext
        )

        guard ok else {
            throw DeviceAIError.initFailed(reason: "Failed to load whisper model: \(modelPath)")
        }

        isInitialized = true
        let durationMs = currentTimeMs() - startMs
        DeviceAI.shared.recordEvent(.modelLoad(module: "stt", modelId: modelId, durationMs: durationMs))
    }

    /// Transcribe raw PCM audio samples.
    public func transcribe(samples: [Float]) async throws -> String {
        guard isInitialized else { throw DeviceAIError.initFailed(reason: "STT not initialized") }

        let startMs = currentTimeMs()
        let audioDurationMs = Int(Float(samples.count) / 16000.0 * 1000.0)

        let result = samples.withUnsafeBufferPointer { ptr -> UnsafeMutablePointer<CChar>? in
            dai_stt_transcribe_audio(ptr.baseAddress, Int32(samples.count))
        }

        let latencyMs = currentTimeMs() - startMs

        guard let result else {
            DeviceAI.shared.recordEvent(.inferenceComplete(
                module: "stt", modelId: modelId, latencyMs: latencyMs,
                inputLengthMs: audioDurationMs, finishReason: "empty"
            ))
            return ""
        }

        let text = String(cString: result)
        dai_stt_free_string(result)

        DeviceAI.shared.recordEvent(.inferenceComplete(
            module: "stt", modelId: modelId, latencyMs: latencyMs,
            inputLengthMs: audioDurationMs, finishReason: "stop"
        ))

        return text
    }

    /// Transcribe a WAV file.
    public func transcribe(audioPath: String) async throws -> String {
        guard isInitialized else { throw DeviceAIError.initFailed(reason: "STT not initialized") }

        let startMs = currentTimeMs()
        let result = dai_stt_transcribe(audioPath)
        let latencyMs = currentTimeMs() - startMs

        guard let result else {
            DeviceAI.shared.recordEvent(.inferenceComplete(
                module: "stt", modelId: modelId, latencyMs: latencyMs, finishReason: "empty"
            ))
            return ""
        }
        let text = String(cString: result)
        dai_stt_free_string(result)

        DeviceAI.shared.recordEvent(.inferenceComplete(
            module: "stt", modelId: modelId, latencyMs: latencyMs, finishReason: "stop"
        ))
        return text
    }

    /// Cancel ongoing transcription.
    public func cancel() { dai_stt_cancel() }

    /// Release STT resources and unload model.
    public func shutdown() {
        guard isInitialized else { return }
        DeviceAI.shared.recordEvent(.modelUnload(module: "stt", modelId: modelId))
        dai_stt_shutdown()
        isInitialized = false
    }

    private func currentTimeMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
