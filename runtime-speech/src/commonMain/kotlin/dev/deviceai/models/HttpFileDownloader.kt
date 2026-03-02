package dev.deviceai.models

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single file over HTTP with resume support and progress reporting.
 *
 * - Streams bytes to a `.tmp` file, then atomically moves it to [destPath].
 * - Partial downloads resume automatically via HTTP Range headers.
 * - Uses [FileSystem] for directory/file management; byte-appending during download
 *   goes through a platform `appendToFile` helper (expect/actual).
 */
internal class HttpFileDownloader(
    private val client: HttpClient,
    private val config: RegistryConfig,
    private val fs: FileSystem
) {
    suspend fun download(
        url: String,
        destPath: String,
        onProgress: (DownloadProgress) -> Unit = {}
    ) {
        val destDir = destPath.substringBeforeLast('/')
        fs.ensureDirectoryExists(destDir)

        val tempPath = "$destPath.tmp"
        val existingBytes = fs.fileSize(tempPath).let { if (it > 0) it else 0L }

        onProgress(DownloadProgress.pending())

        client.prepareGet(url) {
            if (existingBytes > 0) {
                header(HttpHeaders.Range, "bytes=$existingBytes-")
            }
        }.execute { httpResponse ->
            val isResuming = httpResponse.status.value == 206 && existingBytes > 0
            val contentLength = httpResponse.contentLength() ?: 0L
            val totalBytes = if (isResuming) contentLength + existingBytes else contentLength

            val channel: ByteReadChannel = httpResponse.bodyAsChannel()
            val buffer = ByteArray(config.downloadBufferSize)
            var bytesDownloaded = if (isResuming) existingBytes else 0L

            if (!isResuming && existingBytes > 0) {
                fs.deleteFile(tempPath)
            }

            while (!channel.isClosedForRead) {
                coroutineContext.ensureActive()

                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                appendToFile(tempPath, buffer, bytesRead)
                bytesDownloaded += bytesRead

                val percent = if (totalBytes > 0) {
                    (bytesDownloaded.toFloat() / totalBytes * 100f).coerceIn(0f, 100f)
                } else 0f

                onProgress(
                    DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        percentComplete = percent,
                        state = DownloadState.DOWNLOADING
                    )
                )
            }
        }

        fs.deleteFile(destPath)
        if (!fs.moveFile(tempPath, destPath)) {
            throw RuntimeException("Failed to move downloaded file to $destPath")
        }

        onProgress(DownloadProgress.completed(fs.fileSize(destPath)))
    }
}
