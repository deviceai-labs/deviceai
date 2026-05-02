/// Protocol for custom telemetry delivery.
///
/// Implement to route events to your own analytics (Amplitude, Datadog, etc.):
/// ```swift
/// struct MyAnalyticsSink: TelemetrySink {
///     func ingest(_ events: [TelemetryEvent]) async throws {
///         myAnalytics.track(events)
///     }
/// }
/// ```
public protocol TelemetrySink: Sendable {
    func ingest(_ events: [TelemetryEvent]) async throws
}
