import Foundation

/// Device registration session with JWT token.
public struct DeviceSession: Codable, Sendable {
    public let deviceId: String
    public let token: String
    public let expiresAtMs: Int64
    public let capabilityTier: String

    public var isExpired: Bool {
        Int64(Date().timeIntervalSince1970 * 1000) >= expiresAtMs
    }

    public var needsRefresh: Bool {
        let refreshWindow: Int64 = 7 * 24 * 60 * 60 * 1000
        return Int64(Date().timeIntervalSince1970 * 1000) >= (expiresAtMs - refreshWindow)
    }

    enum CodingKeys: String, CodingKey {
        case deviceId = "device_id"
        case token
        case expiresAtMs = "expires_at_ms"
        case capabilityTier = "capability_tier"
    }
}

/// Persists device session to disk (UserDefaults for simplicity; Keychain for production).
internal enum SessionStore {
    private static let key = "dev.deviceai.session"

    static func save(_ session: DeviceSession) {
        if let data = try? JSONEncoder().encode(session) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    static func load() -> DeviceSession? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(DeviceSession.self, from: data)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }
}
