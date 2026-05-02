import Foundation
import DeviceAI

/// A stateful LLM conversation session.
///
/// ```swift
/// let session = try await ChatSession(modelPath: path) {
///     $0.systemPrompt = "You are a helpful assistant."
///     $0.temperature = 0.7
/// }
///
/// // Streaming
/// for try await token in session.send("What is Swift?") {
///     print(token, terminator: "")
/// }
///
/// // Lifecycle
/// session.cancel()
/// session.clearHistory()
/// session.close()
/// ```
public final class ChatSession: @unchecked Sendable {
    private let modelId: String
    private let config: ChatConfig
    private var _history: [LlmMessage] = []
    private var isInitialized = false

    /// True if the model loaded successfully.
    public private(set) var isReady: Bool = false

    /// Read-only conversation history as completed exchanges.
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

    /// Create a chat session. Model loads during initialization.
    ///
    /// - Parameters:
    ///   - modelPath: Absolute path to a GGUF model file.
    ///   - configure: Optional closure to configure the session.
    public init(modelPath: String, configure: ((inout ChatConfig) -> Void)? = nil) async throws {
        var cfg = ChatConfig()
        configure?(&cfg)
        self.config = cfg
        self.modelId = (modelPath as NSString).lastPathComponent

        // TODO: Call dai_llm_init via C interop when XCFrameworks are available
        // For now, mark as not ready — inference requires native engine
        self.isReady = false

        DeviceAI.shared.recordEvent(.modelLoad(
            module: "llm",
            modelId: modelId,
            durationMs: 0 // Will be measured when C interop is wired
        ))
    }

    /// Send a user message and receive a streaming response.
    ///
    /// Conversation history is updated automatically:
    /// - User message added immediately
    /// - Assistant reply appended on completion
    /// - User message rolled back on error (clean retry)
    public func send(_ text: String) -> AsyncThrowingStream<String, Error> {
        precondition(!text.trimmingCharacters(in: .whitespaces).isEmpty, "Message must not be blank")

        _history.append(LlmMessage(role: .user, content: text))

        var messages = [LlmMessage(role: .system, content: config.systemPrompt)] + _history

        // RAG augmentation
        // TODO: Wire ragStore when ChatConfig supports it

        return AsyncThrowingStream { continuation in
            // TODO: Call dai_llm_generate_stream via C interop
            // For now, yield a placeholder
            continuation.finish(throwing: DeviceAIError.initFailed(reason: "Native engine not yet linked — XCFrameworks required"))
        }
    }

    /// Send a user message and block until the full response is available.
    public func sendBlocking(_ text: String) async throws -> String {
        var result = ""
        for try await token in send(text) {
            result += token
        }
        return result
    }

    /// Abort any in-progress generation.
    public func cancel() {
        // TODO: Call dai_llm_cancel() via C interop
    }

    /// Clear conversation history. Model stays loaded.
    public func clearHistory() {
        _history.removeAll()
    }

    /// Unload the model and release all resources.
    public func close() {
        DeviceAI.shared.recordEvent(.modelUnload(module: "llm", modelId: modelId))
        // TODO: Call dai_llm_shutdown() via C interop
    }
}
