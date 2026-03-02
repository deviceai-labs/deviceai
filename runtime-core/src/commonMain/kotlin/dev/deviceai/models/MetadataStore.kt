package dev.deviceai.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the registry of downloaded models to a JSON metadata file on disk.
 *
 * Depends on [FileSystem] for I/O and [StoragePaths] for resolving the storage
 * root — both injected at construction, enabling testing without a real filesystem.
 */
class MetadataStore(
    private val fs: FileSystem,
    private val paths: StoragePaths
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun metadataPath(): String =
        "${paths.getModelsDir()}/registry_metadata.json"

    fun loadDownloadedModels(): List<LocalModel> {
        val content = fs.readText(metadataPath()) ?: return emptyList()
        return try {
            json.decodeFromString<List<LocalModel>>(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDownloadedModels(models: List<LocalModel>) {
        fs.ensureDirectoryExists(paths.getModelsDir())
        fs.writeText(metadataPath(), json.encodeToString(models))
    }

    fun addModel(model: LocalModel) {
        val models = loadDownloadedModels().toMutableList()
        models.removeAll { it.modelId == model.modelId }
        models.add(model)
        saveDownloadedModels(models)
    }

    fun removeModel(modelId: String) {
        val models = loadDownloadedModels().toMutableList()
        models.removeAll { it.modelId == modelId }
        saveDownloadedModels(models)
    }

    fun getModel(modelId: String): LocalModel? =
        loadDownloadedModels().find { it.modelId == modelId }
}
