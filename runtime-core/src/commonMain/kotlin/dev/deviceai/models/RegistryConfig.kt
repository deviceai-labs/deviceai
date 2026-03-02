package dev.deviceai.models

/**
 * Configuration for ModelRegistry initialization.
 */
data class RegistryConfig(
    /**
     * How long to cache the catalog data before re-fetching, in milliseconds.
     * Default: 24 hours.
     */
    val catalogCacheDurationMs: Long = 24 * 60 * 60 * 1000L,

    /**
     * Custom base URL for HuggingFace API (for testing or proxying).
     */
    val huggingFaceBaseUrl: String = "https://huggingface.co",

    /**
     * Download buffer size in bytes.
     */
    val downloadBufferSize: Int = 8 * 1024
)
