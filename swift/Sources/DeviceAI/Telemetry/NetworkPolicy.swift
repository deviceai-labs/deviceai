/// Network-awareness configuration for telemetry delivery.
public struct NetworkPolicy: Sendable {
    /// Returns true if device is on Wi-Fi. Nil = treat as any network.
    public var isOnWifi: (@Sendable () -> Bool)? = nil
    /// Returns true if data saver is active. Nil = assume not active.
    public var isDataSaver: (@Sendable () -> Bool)? = nil
    /// Multiplier for flush threshold when data saver is active.
    public var dataSaverMultiplier: Int = 5

    /// Default policy — no network awareness.
    public static let `default` = NetworkPolicy()
}
