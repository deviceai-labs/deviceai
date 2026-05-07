import Foundation

/// URLSession-based backend client for the DeviceAI control plane.
internal actor BackendClient {
    private let baseUrl: String
    private let apiKey: String
    private let session = URLSession.shared
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    init(baseUrl: String, apiKey: String) throws {
        guard URL(string: baseUrl) != nil else {
            throw DeviceAIError.networkError(reason: "Invalid base URL: \(baseUrl)")
        }
        self.baseUrl = baseUrl
        self.apiKey = apiKey
    }

    // ── Register Device ──────────────────────────────────────────────

    func registerDevice(profile: [String: Any], fingerprint: String) async throws -> DeviceSession {
        var body: [String: Any] = ["capability_profile": profile]
        if !fingerprint.isEmpty { body["device_fingerprint"] = fingerprint }

        let data = try await post("/v1/devices/register", body: body, bearerToken: apiKey)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]

        let tokenLifetimeMs: Int64 = 30 * 24 * 60 * 60 * 1000
        return DeviceSession(
            deviceId: json["device_id"] as? String ?? "",
            token: json["token"] as? String ?? "",
            expiresAtMs: Int64(Date().timeIntervalSince1970 * 1000) + tokenLifetimeMs,
            capabilityTier: json["capability_tier"] as? String ?? "mid"
        )
    }

    // ── Fetch Manifest ───────────────────────────────────────────────

    func fetchManifest(token: String) async throws -> ManifestResponse {
        let data = try await get("/v1/manifest", bearerToken: token)
        return try decoder.decode(ManifestResponse.self, from: data)
    }

    // ── Ingest Telemetry ─────────────────────────────────────────────

    func ingestTelemetry(token: String, sessionId: String, events: [[String: Any]]) async throws {
        let body: [String: Any] = [
            "session_id": sessionId,
            "events": Array(events.prefix(500)),
        ]
        _ = try await post("/v1/telemetry/batch", body: body, bearerToken: token)
    }

    // ── Refresh Token ────────────────────────────────────────────────

    func refreshToken(session: DeviceSession) async throws -> DeviceSession? {
        let data = try await post("/v1/devices/refresh", body: [:], bearerToken: session.token)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        guard let newToken = json["token"] as? String else { return nil }
        let tokenLifetimeMs: Int64 = 30 * 24 * 60 * 60 * 1000
        return DeviceSession(
            deviceId: session.deviceId,
            token: newToken,
            expiresAtMs: Int64(Date().timeIntervalSince1970 * 1000) + tokenLifetimeMs,
            capabilityTier: session.capabilityTier
        )
    }

    // ── HTTP helpers ─────────────────────────────────────────────────

    private func post(_ path: String, body: [String: Any], bearerToken: String) async throws -> Data {
        var request = URLRequest(url: URL(string: "\(baseUrl)\(path)")! /* validated in init */)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        try checkResponse(response, data)
        return data
    }

    private func get(_ path: String, bearerToken: String) async throws -> Data {
        var request = URLRequest(url: URL(string: "\(baseUrl)\(path)")! /* validated in init */)
        request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        try checkResponse(response, data)
        return data
    }

    private func checkResponse(_ response: URLResponse, _ data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw DeviceAIError.networkError(reason: "Invalid response")
        }
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw DeviceAIError.networkError(reason: "HTTP \(http.statusCode): \(body)")
        }
    }
}

/// Default telemetry sink that sends to the DeviceAI backend.
internal struct BackendTelemetrySink: TelemetrySink {
    let client: BackendClient

    func ingest(_ events: [TelemetryEvent]) async throws {
        // Get session from DeviceAI singleton
        guard let token = DeviceAI.shared.currentToken else { return }
        let sessionId = DeviceAI.shared.processSessionId

        let jsonEvents = events.map { $0.toJSON() }
        try await client.ingestTelemetry(token: token, sessionId: sessionId, events: jsonEvents)
    }
}
