import Foundation

/// Result from a blocking LLM generation call.
public struct LlmResult: Sendable {
    public let text: String
    public let tokenCount: Int
    public let promptTokenCount: Int?
    public let finishReason: FinishReason
    public let generationTimeMs: Int64
}

/// How the generation ended.
public enum FinishReason: String, Sendable {
    case stop
    case maxTokens = "max_tokens"
    case cancelled
    case error
}
