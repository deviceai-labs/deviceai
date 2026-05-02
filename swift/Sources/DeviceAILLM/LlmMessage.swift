/// A single message in an LLM conversation.
public struct LlmMessage: Sendable {
    public let role: LlmRole
    public let content: String

    public init(role: LlmRole, content: String) {
        self.role = role
        self.content = content
    }
}

/// Role of a message in a conversation.
public enum LlmRole: String, Sendable {
    case system
    case user
    case assistant
}
