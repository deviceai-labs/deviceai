import Foundation
import DeviceAI
import CDeviceAI

/// A stateful LLM conversation session.
///
/// ```swift
/// let session = try await ChatSession(modelPath: path) {
///     $0.systemPrompt = "You are a helpful assistant."
/// }
/// for try await token in session.send("What is Swift?") {
///     print(token, terminator: "")
/// }
/// session.close()
/// ```
public final class ChatSession: @unchecked Sendable {
    private let modelId: String
    private let config: ChatConfig
    private var _history: [LlmMessage] = []

    public private(set) var isReady: Bool = false

    public var history: [ChatTurn] {
        var turns: [ChatTurn] = []
        var i = 0
        while i + 1 < _history.count {
            if _history[i].role == .user && _history[i + 1].role == .assistant {
                turns.append(ChatTurn(user: _history[i].content, assistant: _history[i + 1].content))
            }
            i += 2
        }
        return turns
    }

    public init(modelPath: String, configure: ((inout ChatConfig) -> Void)? = nil) async throws {
        var cfg = ChatConfig()
        configure?(&cfg)
        self.config = cfg
        self.modelId = (modelPath as NSString).lastPathComponent

        let startMs = currentTimeMs()
        let ok = dai_llm_init(modelPath, Int32(cfg.threads), cfg.useGpu)
        guard ok else {
            throw DeviceAIError.initFailed(reason: "Failed to load model: \(modelPath)")
        }
        isReady = true
        DeviceAI.shared.recordEvent(.modelLoad(module: "llm", modelId: modelId, durationMs: currentTimeMs() - startMs))
    }

    /// Send a user message and stream the response token by token.
    public func send(_ text: String) -> AsyncThrowingStream<String, Error> {
        precondition(!text.trimmingCharacters(in: .whitespaces).isEmpty)

        _history.append(LlmMessage(role: .user, content: text))
        let messages = [LlmMessage(role: .system, content: config.systemPrompt)] + _history

        let cfg = self.config
        let modelId = self.modelId
        let inferenceStartMs = currentTimeMs()

        return AsyncThrowingStream { continuation in
            let roles = messages.map { $0.role.rawValue }
            let contents = messages.map { $0.content }

            var reply = ""
            var tokenCount = 0
            var ttftMs: Int64? = nil

            // Build C string arrays
            let cRoles = roles.map { strdup($0) }
            let cContents = contents.map { strdup($0) }
            defer {
                cRoles.forEach { free($0) }
                cContents.forEach { free($0) }
            }

            var cRolePtrs: [UnsafePointer<CChar>?] = cRoles.map { UnsafePointer($0) }
            var cContentPtrs: [UnsafePointer<CChar>?] = cContents.map { UnsafePointer($0) }

            // Use blocking generate — streaming requires C function pointer interop
            // which is complex in Swift 6. TODO: wire streaming via dai_llm_generate_stream
            let cResult = cRolePtrs.withUnsafeMutableBufferPointer { rolesPtr -> UnsafeMutablePointer<CChar>? in
                cContentPtrs.withUnsafeMutableBufferPointer { contentsPtr in
                    dai_llm_generate(
                        rolesPtr.baseAddress, contentsPtr.baseAddress, Int32(messages.count),
                        Int32(cfg.maxTokens), cfg.temperature, cfg.topP, Int32(cfg.topK), cfg.repeatPenalty
                    )
                }
            }

            let latencyMs = currentTimeMs() - inferenceStartMs

            if let cResult {
                reply = String(cString: cResult)
                dai_llm_free_string(cResult)
                // Yield all at once (TODO: true streaming when C callback interop is resolved)
                continuation.yield(reply)
                tokenCount = reply.split(separator: " ").count // approximate
            }

            DeviceAI.shared.recordEvent(.inferenceComplete(
                module: "llm", modelId: modelId, latencyMs: latencyMs,
                tokensPerSec: latencyMs > 0 ? Float(tokenCount) * 1000.0 / Float(latencyMs) : nil,
                outputTokenCount: tokenCount, finishReason: reply.isEmpty ? "empty" : "stop"
            ))

            if !reply.isEmpty {
                self._history.append(LlmMessage(role: .assistant, content: reply))
            } else {
                self._history.removeLast()
            }

            continuation.finish()
        }
    }

    public func sendBlocking(_ text: String) async throws -> String {
        var result = ""
        for try await token in send(text) { result += token }
        return result
    }

    public func cancel() { dai_llm_cancel() }
    public func clearHistory() { _history.removeAll() }

    public func close() {
        DeviceAI.shared.recordEvent(.modelUnload(module: "llm", modelId: modelId))
        dai_llm_shutdown()
        isReady = false
    }

    private func currentTimeMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

private func currentTimeMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
