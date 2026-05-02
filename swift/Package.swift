// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DeviceAI",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "DeviceAI", targets: ["DeviceAI"]),
        .library(name: "DeviceAISpeech", targets: ["DeviceAISpeech"]),
        .library(name: "DeviceAILLM", targets: ["DeviceAILLM"]),
    ],
    targets: [
        // ── C interop module (bridges to native C++ engines) ─────────
        // When XCFrameworks are built, replace this with binaryTargets:
        //   .binaryTarget(name: "CWhisper", path: "Binaries/CWhisper.xcframework"),
        //   .binaryTarget(name: "CLlama", path: "Binaries/CLlama.xcframework"),
        .target(
            name: "CDeviceAI",
            path: "Sources/CDeviceAI",
            publicHeadersPath: "include"
        ),

        // ── Core module (entry point + cloud + telemetry) ────────────
        .target(
            name: "DeviceAI",
            path: "Sources/DeviceAI"
        ),

        // ── Speech module (STT + TTS) ────────────────────────────────
        .target(
            name: "DeviceAISpeech",
            dependencies: ["DeviceAI", "CDeviceAI"],
            path: "Sources/DeviceAISpeech"
        ),

        // ── LLM module (chat + RAG) ─────────────────────────────────
        .target(
            name: "DeviceAILLM",
            dependencies: ["DeviceAI", "CDeviceAI"],
            path: "Sources/DeviceAILLM"
        ),

        // ── Tests ────────────────────────────────────────────────────
        .testTarget(
            name: "DeviceAITests",
            dependencies: ["DeviceAI"],
            path: "Tests/DeviceAITests"
        ),
        .testTarget(
            name: "DeviceAISpeechTests",
            dependencies: ["DeviceAISpeech"],
            path: "Tests/DeviceAISpeechTests"
        ),
        .testTarget(
            name: "DeviceAILLMTests",
            dependencies: ["DeviceAILLM"],
            path: "Tests/DeviceAILLMTests"
        ),
    ]
)
