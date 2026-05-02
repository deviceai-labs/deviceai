import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Auto-detected device hardware capabilities.
public struct DeviceCapabilities: Sendable {
    public let ramGb: Double
    public let cpuCores: Int
    public let hasNeuralEngine: Bool
    public let socModel: String?
    public let storageAvailableMb: Int64?

    internal static let empty = DeviceCapabilities(
        ramGb: 0, cpuCores: 0, hasNeuralEngine: false,
        socModel: nil, storageAvailableMb: nil
    )

    /// Auto-detect from the current device.
    internal static func detect() -> DeviceCapabilities {
        let ramGb = Double(ProcessInfo.processInfo.physicalMemory) / (1024.0 * 1024.0 * 1024.0)
        let cpuCores = ProcessInfo.processInfo.processorCount

        // All arm64 Apple devices have a Neural Engine
        #if arch(arm64)
        let hasNE = true
        #else
        let hasNE = false
        #endif

        // SoC model from sysctlbyname
        var socModel: String? = nil
        var size: Int = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        if size > 0 {
            var machine = [CChar](repeating: 0, count: size)
            sysctlbyname("hw.machine", &machine, &size, nil, 0)
            socModel = String(cString: machine)
        }

        // Available storage
        let storageAvailableMb: Int64?
        if let attrs = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()),
           let freeSize = attrs[.systemFreeSize] as? Int64 {
            storageAvailableMb = freeSize / (1024 * 1024)
        } else {
            storageAvailableMb = nil
        }

        return DeviceCapabilities(
            ramGb: (ramGb * 10).rounded() / 10, // round to 1 decimal
            cpuCores: cpuCores,
            hasNeuralEngine: hasNE,
            socModel: socModel,
            storageAvailableMb: storageAvailableMb
        )
    }

    internal func toDictionary() -> [String: Any] {
        var dict: [String: Any] = [
            "ram_gb": ramGb,
            "cpu_cores": cpuCores,
            "has_neural_engine": hasNeuralEngine,
        ]
        if let socModel { dict["soc_model"] = socModel }
        if let storageAvailableMb { dict["storage_available_mb"] = storageAvailableMb }
        return dict
    }
}
