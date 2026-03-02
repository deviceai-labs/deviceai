package dev.deviceai.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext

/**
 * Dispatches model downloads to the appropriate [ModelDownloadStrategy].
 *
 * Adding support for a new model type does not require modifying this class —
 * register a new [ModelDownloadStrategy] in [ModelRegistry.initialize] instead.
 */
internal class ModelDownloader(
    private val strategies: List<ModelDownloadStrategy>
) {
    private val activeDownloads = mutableMapOf<String, Job>()

    suspend fun download(
        model: ModelInfo,
        onProgress: (DownloadProgress) -> Unit = {}
    ): LocalModel {
        val strategy = strategies.find { it.supports(model) }
            ?: throw IllegalArgumentException(
                "No download strategy registered for model type: ${model::class.simpleName}"
            )

        val job = coroutineContext[Job]
        if (job != null) activeDownloads[model.id] = job

        return try {
            strategy.download(model, onProgress)
        } catch (e: CancellationException) {
            onProgress(DownloadProgress.cancelled())
            throw e
        } catch (e: Exception) {
            onProgress(DownloadProgress.failed())
            throw e
        } finally {
            activeDownloads.remove(model.id)
        }
    }

    fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
    }
}
