package dev.deviceai.models

/**
 * Resolves platform-specific directory paths for model storage.
 *
 * Separating path resolution from file operations (see [FileSystem]) lets
 * callers depend only on the capability they need.
 */
interface StoragePaths {
    fun getModelsDir(): String
    fun getTempDir(): String
}
