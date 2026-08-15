package dev.deviceai.llm.models

import dev.deviceai.core.availableMemoryBytes
import dev.deviceai.core.availableStorageBytes
import dev.deviceai.llm.engine.LlmJniEngine

/**
 * Rank the catalog for THIS device — the one-call entry point for Android
 * consumers. Combines the shared native memory estimator
 * ([LlmJniEngine.estimatedMemoryBytes]) with live device probes.
 *
 * The [ModelRecommendation] flagged `isTopPick` is the best model the device can
 * actually run; use it as the default. Entries where `compatibility.canRun` is
 * false should be shown with a warning ([ModelCompatibility.reason]) rather than
 * offered for silent download.
 *
 * @param context Android Context (for the ActivityManager memory probe).
 */
fun LlmCatalog.recommendedForDevice(context: Any?): List<ModelRecommendation> {
    val availableMemory = availableMemoryBytes(context)
    val availableStorage = availableStorageBytes()
    val scored = all.map { model ->
        model to compatibilityVerdict(
            modelId = model.id,
            requiredMemoryBytes = LlmJniEngine.estimatedMemoryBytes(model.sizeBytes),
            requiredStorageBytes = model.sizeBytes,
            availableMemoryBytes = availableMemory,
            availableStorageBytes = availableStorage,
        )
    }
    return rankByCompatibility(scored)
}

/** Compatibility of a single model on THIS device, using the native estimator + live probes. */
fun LlmModelInfo.checkCompatibilityForDevice(context: Any?): ModelCompatibility =
    compatibilityVerdict(
        modelId = id,
        requiredMemoryBytes = LlmJniEngine.estimatedMemoryBytes(sizeBytes),
        requiredStorageBytes = sizeBytes,
        availableMemoryBytes = availableMemoryBytes(context),
        availableStorageBytes = availableStorageBytes(),
    )
