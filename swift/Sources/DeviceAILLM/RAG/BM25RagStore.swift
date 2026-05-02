import Foundation

/// Offline BM25 keyword retriever. Same algorithm as Kotlin SDK.
///
/// ```swift
/// let store = BM25RagStore(chunks: [
///     "DeviceAI supports Android and iOS.",
///     "LLM inference uses llama.cpp."
/// ])
/// let results = store.retrieve(query: "What does LLM use?", topK: 3)
/// ```
public final class BM25RagStore: RagRetriever, @unchecked Sendable {
    private let chunks: [String]
    private let sources: [String?]
    private lazy var index: BM25Index = buildIndex()

    private let k1: Float = 1.5
    private let b: Float = 0.75

    public init(chunks: [String], sources: [String?] = []) {
        self.chunks = chunks
        self.sources = sources
    }

    public func retrieve(query: String, topK: Int) -> [RagChunk] {
        let queryTerms = tokenize(query)
        if queryTerms.isEmpty { return [] }

        var scores: [(Int, Float)] = []
        for (i, doc) in index.docs.enumerated() {
            var score: Float = 0
            for term in queryTerms {
                guard let idf = index.idf[term],
                      let tf = doc.termFreqs[term] else { continue }
                let numerator = Float(tf) * (k1 + 1)
                let denominator = Float(tf) + k1 * (1 - b + b * Float(doc.length) / index.avgdl)
                score += idf * numerator / denominator
            }
            if score > 0 { scores.append((i, score)) }
        }

        scores.sort { $0.1 > $1.1 }
        return scores.prefix(topK).map { (i, score) in
            RagChunk(
                text: chunks[i],
                source: i < sources.count ? sources[i] : nil,
                score: score
            )
        }
    }

    // ── Index ────────────────────────────────────────────────────────

    private struct IndexedDoc {
        let termFreqs: [String: Int]
        let length: Int
    }

    private struct BM25Index {
        let docs: [IndexedDoc]
        let idf: [String: Float]
        let avgdl: Float
    }

    private func buildIndex() -> BM25Index {
        let n = chunks.count
        var docs: [IndexedDoc] = []
        var docFreq: [String: Int] = [:]

        for chunk in chunks {
            let terms = tokenize(chunk)
            var tf: [String: Int] = [:]
            for term in terms { tf[term, default: 0] += 1 }
            docs.append(IndexedDoc(termFreqs: tf, length: terms.count))
            for term in tf.keys { docFreq[term, default: 0] += 1 }
        }

        let avgdl = Float(docs.reduce(0) { $0 + $1.length }) / max(Float(n), 1)

        var idf: [String: Float] = [:]
        for (term, df) in docFreq {
            idf[term] = log((Float(n) - Float(df) + 0.5) / (Float(df) + 0.5) + 1)
        }

        return BM25Index(docs: docs, idf: idf, avgdl: avgdl)
    }

    private func tokenize(_ text: String) -> [String] {
        text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count > 2 }
    }
}
