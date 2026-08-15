package dev.deviceai.llm.models

/**
 * Whether a model can run (and, for downloads, fit) given the device's *current*
 * free resources.
 *
 * The memory-footprint *estimate* is owned by the shared C engine
 * (`dai_llm_estimated_memory_bytes`) so every platform binding agrees; this
 * layer only holds the DTOs and the trivial verdict/ranking. Gate on
 * **available** RAM, not total — that's what determines whether a model loads
 * without thrashing.
 */
data class ModelCompatibility(
    val modelId: String,
    /** Fits within currently-available RAM. */
    val canRun: Boolean,
    /** Fits within currently-free storage (relevant before download). */
    val canFit: Boolean,
    val requiredMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val requiredStorageBytes: Long,
    val availableStorageBytes: Long,
) {
    /** True when the model both fits on disk and runs in memory. */
    val isCompatible: Boolean get() = canRun && canFit

    /** Human-readable explanation when the model can't run/fit, else null. */
    val reason: String?
        get() = when {
            !canRun -> "Needs ${formatBytes(requiredMemoryBytes)} of free memory; " +
                "${formatBytes(availableMemoryBytes)} available. Close other apps or pick a smaller model."
            !canFit -> "Needs ${formatBytes(requiredStorageBytes)} of free storage; " +
                "${formatBytes(availableStorageBytes)} available."
            else -> null
        }
}

/** A catalog model ranked for the current device. */
data class ModelRecommendation(
    val model: LlmModelInfo,
    val compatibility: ModelCompatibility,
    /** The single best-fitting model — the one to default to / badge as top pick. */
    val isTopPick: Boolean,
)

/**
 * Pure verdict from explicit numbers. The [requiredMemoryBytes] comes from the
 * shared native estimator; storage/available come from platform probes.
 */
fun compatibilityVerdict(
    modelId: String,
    requiredMemoryBytes: Long,
    requiredStorageBytes: Long,
    availableMemoryBytes: Long,
    availableStorageBytes: Long,
): ModelCompatibility = ModelCompatibility(
    modelId = modelId,
    canRun = availableMemoryBytes >= requiredMemoryBytes,
    canFit = availableStorageBytes >= requiredStorageBytes,
    requiredMemoryBytes = requiredMemoryBytes,
    availableMemoryBytes = availableMemoryBytes,
    requiredStorageBytes = requiredStorageBytes,
    availableStorageBytes = availableStorageBytes,
)

/**
 * Rank pre-scored (model, compatibility) pairs. Runnable models come first
 * (largest-that-fits = best quality the device can handle); the single
 * best-fitting model is flagged [ModelRecommendation.isTopPick].
 */
fun rankByCompatibility(
    scored: List<Pair<LlmModelInfo, ModelCompatibility>>,
): List<ModelRecommendation> {
    val topPickId = scored
        .filter { it.second.canRun }
        .maxByOrNull { it.first.sizeBytes }
        ?.first?.id
    return scored
        .sortedWith(
            compareByDescending<Pair<LlmModelInfo, ModelCompatibility>> { it.second.canRun }
                .thenByDescending { it.first.sizeBytes }
        )
        .map { (model, compat) ->
            ModelRecommendation(model, compat, isTopPick = model.id == topPickId)
        }
}

/** Compact byte formatter for reason strings (commonMain — no String.format). */
private fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b >= 1_000_000_000L -> "${(b / 100_000_000L) / 10.0} GB"
        b >= 1_000_000L -> "${b / 1_000_000L} MB"
        else -> "${b / 1_000L} KB"
    }
}
