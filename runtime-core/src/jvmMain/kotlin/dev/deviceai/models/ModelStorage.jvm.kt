package dev.deviceai.models

import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object PlatformStorage : StoragePaths, FileSystem {

    override fun getModelsDir(): String =
        File(System.getProperty("user.home"), ".speechkmp/models").absolutePath

    override fun getTempDir(): String =
        File(System.getProperty("java.io.tmpdir"), "speechkmp_tmp").absolutePath

    override fun ensureDirectoryExists(path: String): Boolean =
        File(path).let { it.exists() || it.mkdirs() }

    override fun fileExists(path: String): Boolean =
        File(path).exists()

    override fun deleteFile(path: String): Boolean =
        File(path).let { if (it.isDirectory) it.deleteRecursively() else it.delete() }

    override fun moveFile(from: String, to: String): Boolean =
        File(from).renameTo(File(to))

    override fun fileSize(path: String): Long =
        File(path).let { if (it.exists()) it.length() else -1L }

    override fun readText(path: String): String? =
        try { File(path).takeIf { it.exists() }?.readText() } catch (_: Exception) { null }

    override fun writeText(path: String, content: String): Boolean =
        try { File(path).writeText(content); true } catch (_: Exception) { false }
}
