/// Protocol for retrieval-augmented generation.
///
/// Implement to provide context chunks for LLM generation:
/// ```swift
/// let store = BM25RagStore(chunks: ["DeviceAI runs on-device."])
/// let session = try await DeviceAI.llm.chat(modelPath: path) {
///     $0.ragStore = store
/// }
/// ```
public protocol RagRetriever: Sendable {
    func retrieve(query: String, topK: Int) -> [RagChunk]
}
