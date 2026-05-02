import DeviceAI

/// LLM inference namespace. Access via ``DeviceAI/DeviceAI/llm``.
public struct LlmModule: Sendable {

    /// Create a new chat session.
    ///
    /// ```swift
    /// let session = try await DeviceAI.shared.llm.chat(modelPath: "/path/to/model.gguf") {
    ///     $0.systemPrompt = "You are a helpful assistant."
    ///     $0.temperature = 0.8
    /// }
    /// ```
    public func chat(modelPath: String, configure: ((inout ChatConfig) -> Void)? = nil) async throws -> ChatSession {
        try await ChatSession(modelPath: modelPath, configure: configure)
    }
}

extension DeviceAI {
    /// Access the LLM inference module.
    public var llm: LlmModule { LlmModule() }
}
