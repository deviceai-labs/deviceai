import Foundation

/// Response from GET /v1/manifest.
public struct ManifestResponse: Codable, Sendable {
    public let deviceId: String
    public let appId: String
    public let tier: String
    public let issuedAt: String
    public let expiresAt: String
    public let models: [ManifestEntry]
    public let signature: String

    enum CodingKeys: String, CodingKey {
        case deviceId = "device_id"
        case appId = "app_id"
        case tier
        case issuedAt = "issued_at"
        case expiresAt = "expires_at"
        case models
        case signature
    }
}

/// A single model assignment in the manifest.
public struct ManifestEntry: Codable, Sendable {
    public let module: String
    public let modelId: String
    public let version: String
    public let sha256: String
    public let sizeBytes: Int64
    public let cdnPath: String
    public let rolloutId: String

    enum CodingKeys: String, CodingKey {
        case module
        case modelId = "model_id"
        case version
        case sha256
        case sizeBytes = "size_bytes"
        case cdnPath = "cdn_path"
        case rolloutId = "rollout_id"
    }
}
