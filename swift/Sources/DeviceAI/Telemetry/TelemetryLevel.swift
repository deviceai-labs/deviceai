/// Telemetry verbosity level.
public enum TelemetryLevel: Int, Sendable {
    /// Nothing sent (default). Explicit opt-in required.
    case off = 0
    /// Model load/unload + inference metrics only.
    case minimal = 1
    /// All events including OTA downloads and manifest syncs.
    case full = 2
}
