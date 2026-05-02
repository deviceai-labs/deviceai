import Foundation
import os

/// OSLog-backed structured logger for DeviceAI SDK.
internal final class Logger: Sendable {
    static let shared = Logger()

    private let logger = os.Logger(subsystem: "dev.deviceai", category: "SDK")

    func debug(_ message: String) {
        logger.debug("\(message)")
    }

    func info(_ message: String) {
        logger.info("\(message)")
    }

    func warn(_ message: String) {
        logger.warning("\(message)")
    }

    func error(_ message: String) {
        logger.error("\(message)")
    }
}
