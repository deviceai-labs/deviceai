package dev.deviceai.models

/**
 * State of a model download operation.
 */
enum class DownloadState {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Progress information emitted during model downloads.
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentComplete: Float,
    val state: DownloadState
) {
    companion object {
        fun pending() = DownloadProgress(0, 0, 0f, DownloadState.PENDING)
        fun completed(totalBytes: Long) = DownloadProgress(totalBytes, totalBytes, 100f, DownloadState.COMPLETED)
        fun failed() = DownloadProgress(0, 0, 0f, DownloadState.FAILED)
        fun cancelled() = DownloadProgress(0, 0, 0f, DownloadState.CANCELLED)
    }
}
