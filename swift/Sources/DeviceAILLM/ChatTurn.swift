/// A completed conversation exchange (user message + assistant reply).
public struct ChatTurn: Sendable {
    public let user: String
    public let assistant: String
}
