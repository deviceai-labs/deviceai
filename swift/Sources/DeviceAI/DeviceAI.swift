import Foundation
import os

/// Primary entry point for the DeviceAI SDK.
///
/// Call ``initialize(apiKey:configure:)`` once at app startup:
/// ```swift
/// DeviceAI.initialize(apiKey: "dai_live_...") {
///     $0.telemetry = .minimal
/// }
/// ```
///
/// Then use ``llm`` and ``speech`` modules for inference.
public final class DeviceAI: Sendable {

    /// Shared singleton instance.
    public static let shared = DeviceAI()

    // ── State ────────────────────────────────────────────────────────

    private let _config = OSAllocatedUnfairLock<Configuration?>(initialState: nil)
    private let _session = OSAllocatedUnfairLock<DeviceSession?>(initialState: nil)
    private let _telemetryEngine = OSAllocatedUnfairLock<TelemetryEngine?>(initialState: nil)
    private let _backendClient = OSAllocatedUnfairLock<BackendClient?>(initialState: nil)

    /// Stable process session ID (not persisted across launches).
    public let processSessionId = UUID().uuidString

    private init() {}

    // ── Initialization ───────────────────────────────────────────────

    /// Initialize the DeviceAI SDK.
    ///
    /// Call **once** at app startup before using any module.
    ///
    /// - Parameters:
    ///   - apiKey: `dai_live_*` key from cloud.deviceai.dev. Nil for local mode.
    ///   - configure: Optional closure to configure telemetry, app version, etc.
    public static func initialize(
        apiKey: String? = nil,
        configure: ((inout Configuration) -> Void)? = nil
    ) {
        var cfg = Configuration()
        configure?(&cfg)

        // Auto-detect device capabilities
        cfg.deviceCapabilities = DeviceCapabilities.detect()
        cfg.deviceFingerprint = DeviceFingerprint.generate(apiKey: apiKey)

        let config = cfg // freeze as let for Sendable
        shared._config.withLock { $0 = config }

        Logger.shared.info("initialized — env=\(config.environment)")

        guard config.environment != .development, let apiKey else {
            Logger.shared.debug("Local mode — cloud calls disabled")
            return
        }

        // ── Managed mode ─────────────────────────────────────────────
        let client = BackendClient(baseUrl: config.resolvedBaseUrl, apiKey: apiKey)
        shared._backendClient.withLock { $0 = client }

        if config.telemetry != .off {
            let engine = TelemetryEngine(
                level: config.telemetry,
                policy: config.networkPolicy,
                sink: config.telemetrySink ?? BackendTelemetrySink(client: client),
                flushThreshold: config.environment == .production ? 100 : 5
            )
            shared._telemetryEngine.withLock { $0 = engine }
        }

        // Bootstrap in background
        let configCopy = config
        Task {
            await shared.bootstrapManagedMode(config: configCopy, client: client, apiKey: apiKey)
        }
    }

    // ── Public API ───────────────────────────────────────────────────

    /// The configured environment, or nil if not initialized.
    public var environment: Environment? {
        _config.withLock { $0?.environment }
    }

    /// True when running in local mode (no API key, no cloud).
    public var isDevelopment: Bool {
        environment == .development
    }

    /// True when running in managed mode (API key present, backend connected).
    public var isManaged: Bool {
        _session.withLock { $0 != nil }
    }

    /// Capability tier assigned by the backend.
    public var capabilityTier: String? {
        _session.withLock { $0?.capabilityTier }
    }

    /// Flush buffered telemetry events.
    public func flushTelemetry() async {
        await _telemetryEngine.withLock { $0 }?.flush()
    }

    /// Shut down the SDK — flushes telemetry, cancels background jobs.
    public func shutdown() async {
        await _telemetryEngine.withLock { $0 }?.flush()
        _telemetryEngine.withLock { $0 = nil }
        _backendClient.withLock { $0 = nil }
        _session.withLock { $0 = nil }
    }

    /// Record a telemetry event. Called by speech and LLM modules.
    /// Not intended for direct app developer use.
    public func recordEvent(_ event: TelemetryEvent) {
        _telemetryEngine.withLock { $0 }?.record(event)
    }

    /// Current device token (used by BackendTelemetrySink).
    internal var currentToken: String? {
        _session.withLock { $0?.token }
    }

    // ── Bootstrap ────────────────────────────────────────────────────

    private func bootstrapManagedMode(config: Configuration, client: BackendClient, apiKey: String) async {
        // 1. Restore or register device session
        let session: DeviceSession?
        if let cached = SessionStore.load(), !cached.isExpired {
            session = cached
        } else {
            session = try? await client.registerDevice(
                profile: config.capabilityProfile,
                fingerprint: config.deviceFingerprint
            )
            if let session { SessionStore.save(session) }
        }

        guard let session else {
            Logger.shared.warn("device registration failed — cloud features unavailable")
            return
        }

        _session.withLock { $0 = session }
        if let engine = _telemetryEngine.withLock({ $0 }) {
            await engine.setSession(token: session.token, sessionId: processSessionId)
        }

        Logger.shared.info("device registered — id=\(session.deviceId), tier=\(session.capabilityTier)")

        // 2. Fetch manifest
        do {
            let manifest = try await client.fetchManifest(token: session.token)
            Logger.shared.info("manifest synced — \(manifest.models.count) model(s), tier=\(manifest.tier)")
            recordEvent(.manifestSync(success: true, modelCount: manifest.models.count))
        } catch {
            Logger.shared.warn("manifest fetch failed: \(error.localizedDescription)")
            recordEvent(.manifestSync(success: false, errorCode: "network_error"))
        }
    }
}
