package dev.deviceai.models

/**
 * Downloads Whisper GGML models (.bin) from HuggingFace and registers them.
 */
internal class WhisperDownloadStrategy(
    private val http: HttpFileDownloader,
    private val fs: FileSystem,
    private val paths: StoragePaths,
    private val store: MetadataStore
) : ModelDownloadStrategy {

    override fun supports(model: ModelInfo): Boolean = model is WhisperModelInfo

    override suspend fun download(
        model: ModelInfo,
        onProgress: (DownloadProgress) -> Unit
    ): LocalModel {
        model as WhisperModelInfo
        val modelDir = "${paths.getModelsDir()}/whisper"
        fs.ensureDirectoryExists(modelDir)
        val destPath = "$modelDir/${model.id}"

        http.download(model.downloadUrl, destPath, onProgress)

        val localModel = LocalModel(
            modelId      = model.id,
            modelType    = LocalModelType.WHISPER,
            modelPath    = destPath,
            downloadedAt = currentTimeMillis()
        )
        store.addModel(localModel)
        return localModel
    }
}
