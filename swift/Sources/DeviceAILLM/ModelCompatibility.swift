import Foundation
import CDeviceAI

// Device model-compatibility assessment (iOS/macOS).
//
// The memory-footprint *estimate* comes from the shared C engine
// (`dai_llm_estimated_memory_bytes`) — the same heuristic Android uses — so the
// two platforms can never diverge. This layer only holds the DTOs, the trivial
// verdict/ranking, and the Apple device probes. Gate on AVAILABLE memory, not
// total: that's what determines whether a model loads without thrashing.
public extension LlmCatalog {

    /// Whether a model can run (and, for downloads, fit) on the current device.
    struct Compatibility: Sendable {
        public let modelId: String
        public let canRun: Bool
        public let canFit: Bool
        public let requiredMemoryBytes: Int64
        public let availableMemoryBytes: Int64
        public let requiredStorageBytes: Int64
        public let availableStorageBytes: Int64

        public var isCompatible: Bool { canRun && canFit }

        /// Human-readable explanation when the model can't run/fit, else nil.
        public var reason: String? {
            if !canRun {
                return "Needs \(Self.format(requiredMemoryBytes)) of free memory; "
                    + "\(Self.format(availableMemoryBytes)) available. Close other apps or pick a smaller model."
            }
            if !canFit {
                return "Needs \(Self.format(requiredStorageBytes)) of free storage; "
                    + "\(Self.format(availableStorageBytes)) available."
            }
            return nil
        }

        private static func format(_ bytes: Int64) -> String {
            let b = max(0, bytes)
            if b >= 1_000_000_000 { return String(format: "%.1f GB", Double(b) / 1_000_000_000) }
            if b >= 1_000_000 { return "\(b / 1_000_000) MB" }
            return "\(b / 1_000) KB"
        }
    }

    /// A catalog model ranked for the current device.
    struct Recommendation: Sendable {
        public let model: ModelInfo
        public let compatibility: Compatibility
        /// The single best-fitting model — default to / badge as top pick.
        public let isTopPick: Bool
    }

    /// Shared native footprint estimate (`dai_llm_estimated_memory_bytes`).
    static func estimatedMemoryBytes(sizeBytes: Int64) -> Int64 {
        dai_llm_estimated_memory_bytes(sizeBytes)
    }

    /// Compatibility of one model on THIS device.
    static func checkCompatibilityForDevice(_ model: ModelInfo) -> Compatibility {
        verdict(model, availableMemoryBytes(), availableStorageBytes())
    }

    /// Rank the catalog for THIS device; `isTopPick` is the best model it can run.
    static func recommendedForDevice() -> [Recommendation] {
        let availMem = availableMemoryBytes()
        let availStorage = availableStorageBytes()
        let scored = all.map { ($0, verdict($0, availMem, availStorage)) }
        let topPickId = scored.filter { $0.1.canRun }.max { $0.0.sizeBytes < $1.0.sizeBytes }?.0.id
        return scored
            .sorted { a, b in
                if a.1.canRun != b.1.canRun { return a.1.canRun && !b.1.canRun }
                return a.0.sizeBytes > b.0.sizeBytes
            }
            .map { Recommendation(model: $0.0, compatibility: $0.1, isTopPick: $0.0.id == topPickId) }
    }

    // MARK: - Internals

    private static func verdict(_ model: ModelInfo, _ availMem: Int64, _ availStorage: Int64) -> Compatibility {
        let reqMem = dai_llm_estimated_memory_bytes(model.sizeBytes)
        return Compatibility(
            modelId: model.id,
            canRun: availMem >= reqMem,
            canFit: availStorage >= model.sizeBytes,
            requiredMemoryBytes: reqMem,
            availableMemoryBytes: availMem,
            requiredStorageBytes: model.sizeBytes,
            availableStorageBytes: availStorage
        )
    }

    /// Available memory in bytes. On iOS this is what the app can allocate before
    /// jetsam (`os_proc_available_memory`, iOS 13+) — the metric that actually
    /// predicts a load; on macOS that API is unavailable, so fall back to total RAM.
    private static func availableMemoryBytes() -> Int64 {
        #if os(iOS) || os(tvOS) || os(watchOS)
        let avail = os_proc_available_memory()
        if avail > 0 { return Int64(avail) }
        #endif
        return Int64(ProcessInfo.processInfo.physicalMemory)
    }

    private static func availableStorageBytes() -> Int64 {
        guard let attrs = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()),
              let free = attrs[.systemFreeSize] as? NSNumber else { return 0 }
        return free.int64Value
    }
}
