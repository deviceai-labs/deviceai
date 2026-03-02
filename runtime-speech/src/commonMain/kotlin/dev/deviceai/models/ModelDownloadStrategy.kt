package dev.deviceai.models

/**
 * Strategy for downloading a specific type of model and registering it in [MetadataStore].
 *
 * Implement this interface to add support for a new model type without modifying
 * [ModelDownloader] — Open/Closed Principle. Register the strategy in
 * [ModelRegistry.initialize] alongside the built-in Whisper and Piper strategies.
 */
internal interface ModelDownloadStrategy {
    /** Returns true when this strategy can handle [model]. */
    fun supports(model: ModelInfo): Boolean

    /** Downloads [model], persists it in [MetadataStore], and returns the [LocalModel]. */
    suspend fun download(model: ModelInfo, onProgress: (DownloadProgress) -> Unit): LocalModel
}
