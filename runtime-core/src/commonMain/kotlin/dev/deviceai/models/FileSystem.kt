package dev.deviceai.models

/**
 * Platform-specific file system operations used by the SDK.
 *
 * Separating file operations from path resolution (see [StoragePaths]) lets
 * callers depend only on the capability they need and simplifies testing.
 */
interface FileSystem {
    fun ensureDirectoryExists(path: String): Boolean
    fun fileExists(path: String): Boolean
    fun deleteFile(path: String): Boolean
    fun moveFile(from: String, to: String): Boolean
    fun fileSize(path: String): Long
    fun readText(path: String): String?
    fun writeText(path: String, content: String): Boolean
}
