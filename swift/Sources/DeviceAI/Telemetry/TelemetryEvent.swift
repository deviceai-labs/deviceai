import Foundation

/// A single telemetry event emitted by the SDK.
public enum TelemetryEvent: Sendable {
    case modelLoad(module: String, modelId: String, durationMs: Int64)
    case modelUnload(module: String, modelId: String)
    case inferenceComplete(
        module: String, modelId: String, latencyMs: Int64,
        ttftMs: Int64? = nil, tokensPerSec: Float? = nil,
        inputTokenCount: Int? = nil, outputTokenCount: Int? = nil,
        inputLengthMs: Int? = nil, outputChars: Int? = nil,
        finishReason: String? = nil
    )
    case otaDownload(modelId: String, version: String, sizeBytes: Int64, durationMs: Int64, success: Bool, errorCode: String? = nil)
    case manifestSync(success: Bool, modelCount: Int = 0, errorCode: String? = nil)
    case controlPlaneAlert(alertType: String, modelId: String? = nil, rolloutId: String? = nil)

    internal var type: String {
        switch self {
        case .modelLoad:          return "model_load"
        case .modelUnload:        return "model_unload"
        case .inferenceComplete:  return "inference_complete"
        case .otaDownload:        return "ota_download"
        case .manifestSync:       return "manifest_sync"
        case .controlPlaneAlert:  return "control_plane_alert"
        }
    }

    internal func toJSON() -> [String: Any] {
        var dict: [String: Any] = [
            "type": type,
            "timestamp_ms": Int64(Date().timeIntervalSince1970 * 1000),
        ]
        switch self {
        case .modelLoad(let module, let modelId, let durationMs):
            dict["module"] = module; dict["model_id"] = modelId; dict["duration_ms"] = durationMs
        case .modelUnload(let module, let modelId):
            dict["module"] = module; dict["model_id"] = modelId
        case .inferenceComplete(let module, let modelId, let latencyMs, let ttftMs, let tokensPerSec, let inputTokenCount, let outputTokenCount, let inputLengthMs, let outputChars, let finishReason):
            dict["module"] = module; dict["model_id"] = modelId; dict["latency_ms"] = latencyMs
            if let ttftMs { dict["ttft_ms"] = ttftMs }
            if let tokensPerSec { dict["tokens_per_sec"] = tokensPerSec }
            if let inputTokenCount { dict["input_token_count"] = inputTokenCount }
            if let outputTokenCount { dict["output_token_count"] = outputTokenCount }
            if let inputLengthMs { dict["input_length_ms"] = inputLengthMs }
            if let outputChars { dict["output_chars"] = outputChars }
            if let finishReason { dict["finish_reason"] = finishReason }
        case .otaDownload(let modelId, let version, let sizeBytes, let durationMs, let success, let errorCode):
            dict["model_id"] = modelId; dict["version"] = version; dict["size_bytes"] = sizeBytes
            dict["duration_ms"] = durationMs; dict["success"] = success
            if let errorCode { dict["error_code"] = errorCode }
        case .manifestSync(let success, let modelCount, let errorCode):
            dict["success"] = success; dict["model_count"] = modelCount
            if let errorCode { dict["error_code"] = errorCode }
        case .controlPlaneAlert(let alertType, let modelId, let rolloutId):
            dict["alert_type"] = alertType
            if let modelId { dict["model_id"] = modelId }
            if let rolloutId { dict["rollout_id"] = rolloutId }
        }
        return dict
    }
}
