/// Injects retrieved context chunks into the system prompt before generation.
internal enum RagAugmentor {

    static func augment(
        messages: [LlmMessage],
        ragStore: RagRetriever,
        topK: Int
    ) -> [LlmMessage] {
        // Find last user message for the query
        guard let lastUser = messages.last(where: { $0.role == .user }) else {
            return messages
        }

        let chunks = ragStore.retrieve(query: lastUser.content, topK: topK)
        guard !chunks.isEmpty else { return messages }

        // Build context string
        let context = chunks.enumerated()
            .map { "[\($0.offset + 1)] \($0.element.text)" }
            .joined(separator: "\n")

        // Prepend to system prompt
        var result = messages
        if let systemIdx = result.firstIndex(where: { $0.role == .system }) {
            let original = result[systemIdx].content
            result[systemIdx] = LlmMessage(
                role: .system,
                content: "\(original)\n\nRelevant context:\n\(context)"
            )
        } else {
            result.insert(
                LlmMessage(role: .system, content: "Relevant context:\n\(context)"),
                at: 0
            )
        }

        return result
    }
}
