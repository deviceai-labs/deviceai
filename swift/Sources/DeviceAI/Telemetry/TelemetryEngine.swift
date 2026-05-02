import Foundation

/// Actor-based telemetry buffering with three-priority delivery.
///
/// Same design as Kotlin's TelemetryEngineImpl but using Swift actor model
/// instead of C++ threads/mutexes.
internal actor TelemetryEngine {
    private let level: TelemetryLevel
    private let policy: NetworkPolicy
    private let sink: TelemetrySink
    private let flushThreshold: Int

    private var normalBuffer: [TelemetryEvent] = []
    private var wifiBuffer: [TelemetryEvent] = []
    private var criticalBuffer: [TelemetryEvent] = []

    private var deviceToken: String?
    private var sessionId: String?

    private let bufferCapacity = 256
    private let criticalCapacity = 32
    private let wifiFlushThreshold = 50

    init(level: TelemetryLevel, policy: NetworkPolicy, sink: TelemetrySink, flushThreshold: Int = 100) {
        self.level = level
        self.policy = policy
        self.sink = sink
        self.flushThreshold = flushThreshold
    }

    func setSession(token: String, sessionId: String) {
        self.deviceToken = token
        self.sessionId = sessionId
    }

    nonisolated func record(_ event: TelemetryEvent) {
        Task { await enqueueIfAllowed(event) }
    }

    private func enqueueIfAllowed(_ event: TelemetryEvent) async {
        guard shouldRecord(event) else { return }
        await enqueue(event)
    }

    private func enqueue(_ event: TelemetryEvent) async {
        let priority = eventPriority(event)

        switch priority {
        case .critical:
            if criticalBuffer.count >= criticalCapacity { criticalBuffer.removeFirst() }
            criticalBuffer.append(event)
            await flushCritical()
        case .wifiPreferred:
            if wifiBuffer.count >= bufferCapacity { wifiBuffer.removeFirst() }
            wifiBuffer.append(event)
            if wifiBuffer.count >= wifiFlushThreshold && isOnWifi() {
                await flushWifi()
            }
        case .normal:
            if normalBuffer.count >= bufferCapacity { normalBuffer.removeFirst() }
            normalBuffer.append(event)
            if normalBuffer.count >= normalFlushThreshold {
                await flushNormal()
            }
        }
    }

    func flush() async {
        await flushCritical()
        if isOnWifi() { await flushWifi() }
        await flushNormal()
    }

    // ── Flush helpers ────────────────────────────────────────────────

    private func flushCritical() async {
        guard !criticalBuffer.isEmpty else { return }
        let batch = criticalBuffer; criticalBuffer.removeAll()
        await sendWithBackoff(batch, tag: "critical")
    }

    private func flushWifi() async {
        guard !wifiBuffer.isEmpty else { return }
        let batch = wifiBuffer; wifiBuffer.removeAll()
        await sendWithBackoff(batch, tag: "wifi")
    }

    private func flushNormal() async {
        guard !normalBuffer.isEmpty else { return }
        let batch = normalBuffer; normalBuffer.removeAll()
        await sendWithBackoff(batch, tag: "normal")
    }

    private func sendWithBackoff(_ batch: [TelemetryEvent], tag: String) async {
        for attempt in 1...3 {
            do {
                try await sink.ingest(batch)
                Logger.shared.debug("[\(tag)] flushed \(batch.count) events")
                return
            } catch {
                if attempt == 3 {
                    Logger.shared.warn("[\(tag)] giving up after 3 retries")
                    // Re-queue non-critical to normal buffer
                    if tag != "critical" {
                        let space = bufferCapacity - normalBuffer.count
                        normalBuffer.insert(contentsOf: batch.suffix(space), at: 0)
                    }
                    return
                }
                let delayMs = 1000 * (1 << (attempt - 1)) // 1s, 2s, 4s
                try? await Task.sleep(nanoseconds: UInt64(delayMs) * 1_000_000)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private var normalFlushThreshold: Int {
        let isDataSaver = policy.isDataSaver?() ?? false
        return isDataSaver ? flushThreshold * policy.dataSaverMultiplier : flushThreshold
    }

    private func isOnWifi() -> Bool {
        policy.isOnWifi?() ?? true
    }

    private func shouldRecord(_ event: TelemetryEvent) -> Bool {
        switch level {
        case .off: return false
        case .full: return true
        case .minimal:
            switch event {
            case .modelLoad, .modelUnload, .inferenceComplete, .controlPlaneAlert: return true
            default: return false
            }
        }
    }

    private enum Priority { case normal, wifiPreferred, critical }

    private func eventPriority(_ event: TelemetryEvent) -> Priority {
        switch event {
        case .controlPlaneAlert: return .critical
        case .otaDownload, .manifestSync: return .wifiPreferred
        default: return .normal
        }
    }
}
