/// Configuration for an LLM chat session.
public struct ChatConfig: Sendable {
    public var systemPrompt: String = "You are a helpful assistant."
    public var maxTokens: Int = 512
    public var temperature: Float = 0.7
    public var topP: Float = 0.9
    public var topK: Int = 40
    public var repeatPenalty: Float = 1.1
    public var threads: Int = 4
    public var useGpu: Bool = true

    public init() {}
}
