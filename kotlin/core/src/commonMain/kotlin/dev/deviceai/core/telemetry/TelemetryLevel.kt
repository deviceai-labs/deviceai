package dev.deviceai.core.telemetry

/**
 * Controls the verbosity of SDK telemetry sent to the DeviceAI backend.
 *
 * Telemetry is **off by default**. Enable only after obtaining explicit user consent (GDPR/CCPA).
 *
 * What is **never** collected regardless of level:
 * - Prompt or response content
 * - Audio input or output
 * - Any personally identifiable information (PII)
 */
enum class TelemetryLevel {
    /**
     * No telemetry is buffered or sent. Default.
     * Use this if you handle your own analytics or require explicit user opt-in before enabling.
     */
    Off,

    /**
     * Minimal performance metrics only:
     * - Model load / unload (module, duration_ms, ram_delta_mb)
     * - Inference completion (module, latency_ms, tokens/sec for LLM)
     *
     * Suitable for production apps that have obtained general analytics consent.
     */
    Minimal,

    /**
     * All events including OTA download and manifest sync results.
     *
     * - All [Minimal] events
     * - OTA model downloads (start, complete, failure, size, duration)
     * - Manifest sync results (model count, success/failure)
     *
     * Use [Full] for debugging rollout issues or tracking OTA adoption rates.
     */
    Full,
}
