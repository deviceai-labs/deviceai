import Foundation
import CryptoKit
#if canImport(UIKit)
import UIKit
#endif
#if canImport(IOKit)
import IOKit
#endif

/// Generates a stable device fingerprint that survives app reinstalls.
///
/// Uses `UIDevice.identifierForVendor` on iOS (persists across reinstalls,
/// resets on uninstall of all vendor apps). Hashed with API key so different
/// apps get different fingerprints.
internal enum DeviceFingerprint {

    static func generate(apiKey: String?) -> String {
        guard let apiKey else { return "" }

        let vendorId: String
        #if canImport(UIKit) && !os(watchOS)
        vendorId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        #else
        // macOS: use hardware UUID
        vendorId = macOSHardwareUUID() ?? UUID().uuidString
        #endif

        let raw = "\(vendorId):\(apiKey)"
        let hash = SHA256.hash(data: Data(raw.utf8))
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    #if os(macOS)
    private static func macOSHardwareUUID() -> String? {
        let service = IOServiceGetMatchingService(kIOMainPortDefault, IOServiceMatching("IOPlatformExpertDevice"))
        guard service != 0 else { return nil }
        defer { IOObjectRelease(service) }
        let uuidRef = IORegistryEntryCreateCFProperty(service, "IOPlatformUUID" as CFString, kCFAllocatorDefault, 0)
        return uuidRef?.takeRetainedValue() as? String
    }
    #endif
}
