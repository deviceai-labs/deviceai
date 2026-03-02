package dev.deviceai.models

/**
 * Platform-specific storage implementation.
 *
 * Implements [StoragePaths] (directory resolution) and [FileSystem] (file I/O).
 * On Android, call [PlatformStorage.initialize] with a Context before use.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object PlatformStorage : StoragePaths, FileSystem
