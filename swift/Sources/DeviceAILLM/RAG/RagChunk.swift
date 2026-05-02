/// A single retrieved context chunk.
public struct RagChunk: Sendable {
    public let text: String
    public let source: String?
    public let score: Float

    public init(text: String, source: String? = nil, score: Float = 0) {
        self.text = text
        self.source = source
        self.score = score
    }
}
