package dev.deviceai.models

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object PlatformStorage : StoragePaths, FileSystem {

    actual override fun getModelsDir(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        )
        val documentsDir = paths.firstOrNull() as? String
            ?: throw IllegalStateException("Cannot find NSDocumentDirectory")
        return "$documentsDir/speechkmp_models"
    }

    actual override fun getTempDir(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        )
        val cachesDir = paths.firstOrNull() as? String
            ?: throw IllegalStateException("Cannot find NSCachesDirectory")
        return "$cachesDir/speechkmp_tmp"
    }

    actual override fun ensureDirectoryExists(path: String): Boolean {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(path)) return true
        return try {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        } catch (_: Exception) {
            false
        }
    }

    actual override fun fileExists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual override fun deleteFile(path: String): Boolean =
        try {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        } catch (_: Exception) {
            false
        }

    actual override fun moveFile(from: String, to: String): Boolean =
        try {
            NSFileManager.defaultManager.moveItemAtPath(from, toPath = to, error = null)
        } catch (_: Exception) {
            false
        }

    actual override fun fileSize(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
            ?: return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longValue ?: -1L
    }

    actual override fun readText(path: String): String? =
        try {
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) {
            null
        }

    actual override fun writeText(path: String, content: String): Boolean =
        try {
            (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) {
            false
        }
}
