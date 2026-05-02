import Foundation

/// Configuration for the DeviceAI SDK.
///
/// ```swift
/// DeviceAI.initialize(apiKey: "dai_live_...") {
///     $0.telemetry = .minimal
///     $0.appVersion = Bundle.main.appVersion
/// }
/// ```
public struct Configuration: Sendable {

    /// Target environment.
    public var environment: Environment = .production

    /// Telemetry reporting level. Defaults to `.off` — explicit opt-in required (GDPR/CCPA).
    public var telemetry: TelemetryLevel = .off

    /// Custom telemetry sink. When set, events route here instead of the DeviceAI backend.
    public var telemetrySink: TelemetrySink? = nil

    /// Network-awareness for telemetry delivery.
    public var networkPolicy: NetworkPolicy = .default

    /// App version string for cohort targeting.
    public var appVersion: String? = nil

    /// Custom attributes for cohort targeting (e.g. "user_tier": "premium").
    public var appAttributes: [String: String] = [:]

    /// Override the backend base URL. Nil = auto-resolved from environment.
    public var baseUrl: String? = nil

    // ── Internal (auto-populated) ────────────────────────────────────

    internal var deviceCapabilities: DeviceCapabilities = .empty
    internal var deviceFingerprint: String = ""

    /// Resolved base URL based on environment.
    internal var resolvedBaseUrl: String {
        baseUrl ?? environment.baseUrl
    }

    /// Full capability profile sent at registration.
    internal var capabilityProfile: [String: Any] {
        var profile = deviceCapabilities.toDictionary()
        profile["sdk_version"] = "0.3.0-alpha01"
        profile["platform"] = "ios"
        if let appVersion { profile["app_version"] = appVersion }
        for (key, value) in appAttributes { profile[key] = value }
        return profile
    }
}

/// SDK environment.
public enum Environment: String, Sendable {
    case development
    case staging
    case production

    internal var baseUrl: String {
        switch self {
        case .development: return "http://localhost:8080"
        case .staging:     return "https://staging.api.deviceai.dev"
        case .production:  return "https://api.deviceai.dev"
        }
    }
}
