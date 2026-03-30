package dev.deviceai.models

/**
 * Downloads a sherpa-onnx TTS voice model and registers it in [MetadataStore].
 *
 * Files downloaded per voice:
 * - `model.onnx` (or `model.int8.onnx`) → stored at [LocalModel.modelPath]
 * - `tokens.txt`                          → stored at [LocalModel.configPath]
 * - `voices.bin` (Kokoro only)            → stored alongside modelPath (same directory)
 *
 * The voices.bin path is derived by convention: `modelPath.parent + "/voices.bin"`.
 * Pass this to [TtsConfig.voicesPath] when initialising [SpeechBridge].
 */
internal class TtsDownloadStrategy(
    private val http: HttpFileDownloader,
    private val fs: FileSystem,
    private val paths: StoragePaths,
    private val store: MetadataStore
) : ModelDownloadStrategy {

    override fun supports(model: ModelInfo): Boolean = model is TtsVoiceInfo

    override suspend fun download(
        model: ModelInfo,
        onProgress: (DownloadProgress) -> Unit
    ): LocalModel {
        model as TtsVoiceInfo

        val voiceDir = "${paths.getModelsDir()}/tts/${model.id}"
        fs.ensureDirectoryExists(voiceDir)

        val modelFile  = model.modelUrl.substringAfterLast('/')
        val modelPath  = "$voiceDir/$modelFile"
        val tokensPath = "$voiceDir/tokens.txt"

        // tokens.txt is tiny — download with no progress
        http.download(model.tokensUrl, tokensPath)

        // voices.bin (Kokoro) — also small relative to the model
        if (model.voicesUrl != null) {
            http.download(model.voicesUrl, "$voiceDir/voices.bin")
        }

        // Main ONNX model — this is the large file; report progress
        http.download(model.modelUrl, modelPath, onProgress)

        val localModel = LocalModel(
            modelId      = model.id,
            modelType    = LocalModelType.TTS,
            modelPath    = modelPath,
            configPath   = tokensPath,
            downloadedAt = currentTimeMillis()
        )
        store.addModel(localModel)
        return localModel
    }
}
