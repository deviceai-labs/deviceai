import Foundation

/// Typed errors for DeviceAI SDK operations.
public enum DeviceAIError: Error, Sendable {
    case modelNotFound(path: String)
    case initFailed(reason: String)
    case inferenceFailed(reason: String)
    case downloadFailed(reason: String)
    case networkError(reason: String)
    case cancelled
}
