package dev.deviceai.models

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches and parses available Whisper GGML models from HuggingFace.
 *
 * Source repo: ggerganov/whisper.cpp
 * API: GET https://huggingface.co/api/models/ggerganov/whisper.cpp → siblings[]
 * Download: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/{filename}
 */
internal class WhisperCatalog(
    private val client: HttpClient,
    private val config: RegistryConfig
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class HfModelResponse(val siblings: List<HfSibling> = emptyList())

    @Serializable
    private data class HfSibling(val rfilename: String)

    /**
     * Regex to parse whisper GGML filenames:
     *   ggml-tiny.bin, ggml-base.en.bin, ggml-medium-q5_1.bin, ggml-large-v3-turbo.bin
     */
    private val filenameRegex = Regex(
        """ggml-(tiny|base|small|medium|large-v1|large-v2|large-v3-turbo|large-v3)(?:\.(en))?(?:-(q5_1|q8_0))?\.bin"""
    )

    // Known approximate sizes (bytes) for non-quantized models.
    // We'll get the real size via HEAD request on download.
    private val approximateSizes: Map<String, Long> = mapOf(
        "tiny" to 77_691_713L,
        "base" to 147_951_465L,
        "small" to 487_601_617L,
        "medium" to 1_533_774_081L,
        "large-v1" to 3_094_623_201L,
        "large-v2" to 3_094_623_201L,
        "large-v3" to 3_094_623_201L,
        "large-v3-turbo" to 1_622_089_793L
    )

    private var cachedModels: List<WhisperModelInfo>? = null
    private var cacheTimestamp: Long = 0

    suspend fun fetchModels(): List<WhisperModelInfo> {
        // Return cached if still fresh
        val now = currentTimeMillis()
        cachedModels?.let { cached ->
            if (now - cacheTimestamp < config.catalogCacheDurationMs) return cached
        }

        // Try fetching from HF, fall back to cached catalog on disk
        val models = try {
            fetchFromApi()
        } catch (e: Exception) {
            loadCachedFromDisk() ?: throw e
        }

        cachedModels = models
        cacheTimestamp = now
        saveCacheToDisk(models)
        return models
    }

    private suspend fun fetchFromApi(): List<WhisperModelInfo> {
        val response: HttpResponse = client.get(
            "${config.huggingFaceBaseUrl}/api/models/ggerganov/whisper.cpp"
        )
        val body: String = response.body()
        val hfModel = json.decodeFromString<HfModelResponse>(body)

        return hfModel.siblings
            .mapNotNull { sibling -> parseFilename(sibling.rfilename) }
            .sortedBy { it.sizeBytes }
    }

    internal fun parseFilename(filename: String): WhisperModelInfo? {
        val match = filenameRegex.matchEntire(filename) ?: return null
        val sizeStr = match.groupValues[1]
        val isEnglish = match.groupValues[2] == "en"
        val quantStr = match.groupValues[3].ifEmpty { null }

        val whisperSize = WhisperSize.fromString(sizeStr) ?: return null
        val quantization = quantStr?.let { Quantization.fromString(it) }

        // Build display name
        val displayParts = mutableListOf<String>()
        displayParts.add("Whisper ${sizeStr.replaceFirstChar { it.uppercase() }}")
        if (isEnglish) displayParts.add("(English)")
        else displayParts.add("(Multilingual)")
        if (quantization != null) displayParts.add("[${quantization.label}]")

        // Estimate size — quantized models are roughly 40-60% of full size
        val baseSize = approximateSizes[sizeStr] ?: 0L
        val estimatedSize = when (quantization) {
            Quantization.Q5_1 -> (baseSize * 0.45).toLong()
            Quantization.Q8_0 -> (baseSize * 0.65).toLong()
            null -> baseSize
        }

        val downloadUrl = "${config.huggingFaceBaseUrl}/ggerganov/whisper.cpp/resolve/main/$filename"

        return WhisperModelInfo(
            id = filename,
            displayName = displayParts.joinToString(" "),
            sizeBytes = estimatedSize,
            size = whisperSize,
            isEnglishOnly = isEnglish,
            quantization = quantization,
            downloadUrl = downloadUrl
        )
    }

    private fun loadCachedFromDisk(): List<WhisperModelInfo>? {
        val cachePath = "${PlatformStorage.getModelsDir()}/whisper_catalog_cache.json"
        val content = PlatformStorage.readText(cachePath) ?: return null
        return try {
            val cached = json.decodeFromString<CachedWhisperCatalog>(content)
            cached.models.map { it.toModelInfo(config.huggingFaceBaseUrl) }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCacheToDisk(models: List<WhisperModelInfo>) {
        PlatformStorage.ensureDirectoryExists(PlatformStorage.getModelsDir())
        val cachePath = "${PlatformStorage.getModelsDir()}/whisper_catalog_cache.json"
        val cached = CachedWhisperCatalog(
            models = models.map { CachedWhisperEntry.fromModelInfo(it) },
            cachedAt = currentTimeMillis()
        )
        PlatformStorage.writeText(cachePath, json.encodeToString(CachedWhisperCatalog.serializer(), cached))
    }

    fun clearCache() {
        cachedModels = null
        cacheTimestamp = 0
        val cachePath = "${PlatformStorage.getModelsDir()}/whisper_catalog_cache.json"
        PlatformStorage.deleteFile(cachePath)
    }
}

@Serializable
private data class CachedWhisperCatalog(
    val models: List<CachedWhisperEntry>,
    val cachedAt: Long
)

@Serializable
private data class CachedWhisperEntry(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val size: String,
    val isEnglishOnly: Boolean,
    val quantization: String?
) {
    fun toModelInfo(baseUrl: String) = WhisperModelInfo(
        id = id,
        displayName = displayName,
        sizeBytes = sizeBytes,
        size = WhisperSize.fromString(size) ?: WhisperSize.TINY,
        isEnglishOnly = isEnglishOnly,
        quantization = quantization?.let { Quantization.fromString(it) },
        downloadUrl = "$baseUrl/ggerganov/whisper.cpp/resolve/main/$id"
    )

    companion object {
        fun fromModelInfo(m: WhisperModelInfo) = CachedWhisperEntry(
            id = m.id,
            displayName = m.displayName,
            sizeBytes = m.sizeBytes,
            size = m.size.label,
            isEnglishOnly = m.isEnglishOnly,
            quantization = m.quantization?.label
        )
    }
}

