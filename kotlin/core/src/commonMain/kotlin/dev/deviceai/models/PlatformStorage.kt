package dev.deviceai.models

/**
 * Platform-specific storage implementation.
 *
 * Implements [StoragePaths] (directory resolution) and [FileSystem] (file I/O).
 * On Android, call [PlatformStorage.initialize] with a Context before use.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object PlatformStorage : StoragePaths, FileSystem {
    override fun getModelsDir(): String
    override fun getTempDir(): String
    override fun ensureDirectoryExists(path: String): Boolean
    override fun fileExists(path: String): Boolean
    override fun deleteFile(path: String): Boolean
    override fun moveFile(from: String, to: String): Boolean
    override fun fileSize(path: String): Long
    override fun readText(path: String): String?
    override fun writeText(path: String, content: String): Boolean
}
