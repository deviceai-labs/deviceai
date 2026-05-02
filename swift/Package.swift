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
        // ── Core module (entry point + cloud + telemetry) ────────────
        .target(
            name: "DeviceAI",
            path: "Sources/DeviceAI"
        ),

        // ── Speech module (STT + TTS) ────────────────────────────────
        .target(
            name: "DeviceAISpeech",
            dependencies: ["DeviceAI"],
            path: "Sources/DeviceAISpeech"
        ),

        // ── LLM module (chat + RAG) ─────────────────────────────────
        .target(
            name: "DeviceAILLM",
            dependencies: ["DeviceAI"],
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
