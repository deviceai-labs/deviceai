package dev.deviceai.models

/**
 * Downloads Piper TTS voice models (.onnx + .onnx.json) from HuggingFace and registers them.
 */
internal class PiperDownloadStrategy(
    private val http: HttpFileDownloader,
    private val fs: FileSystem,
    private val paths: StoragePaths,
    private val store: MetadataStore
) : ModelDownloadStrategy {

    override fun supports(model: ModelInfo): Boolean = model is PiperVoiceInfo

    override suspend fun download(
        model: ModelInfo,
        onProgress: (DownloadProgress) -> Unit
    ): LocalModel {
        model as PiperVoiceInfo
        val voiceDir = "${paths.getModelsDir()}/piper/${model.id}"
        fs.ensureDirectoryExists(voiceDir)

        val modelPath  = "$voiceDir/${model.modelUrl.substringAfterLast('/')}"
        val configPath = "$voiceDir/${model.configUrl.substringAfterLast('/')}"

        // Config is small — no progress needed
        http.download(model.configUrl, configPath)
        // Model is large — report progress
        http.download(model.modelUrl, modelPath, onProgress)

        val localModel = LocalModel(
            modelId      = model.id,
            modelType    = LocalModelType.PIPER,
            modelPath    = modelPath,
            configPath   = configPath,
            downloadedAt = currentTimeMillis()
        )
        store.addModel(localModel)
        return localModel
    }
}
