package dev.deviceai.llm.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelCompatibilityTest {

    private fun model(id: String, sizeBytes: Long) = LlmModelInfo(
        id = id,
        name = id,
        repoId = "repo/$id",
        filename = "$id.gguf",
        sizeBytes = sizeBytes,
        quantization = "Q4_K_M",
        parameters = "?",
        description = "",
    )

    // ── compatibilityVerdict ────────────────────────────────────────────────

    @Test
    fun compatible_whenBothRamAndStorageSuffice() {
        val c = compatibilityVerdict(
            modelId = "m",
            requiredMemoryBytes = 1_000,
            requiredStorageBytes = 2_000,
            availableMemoryBytes = 1_500,
            availableStorageBytes = 3_000,
        )
        assertTrue(c.canRun)
        assertTrue(c.canFit)
        assertTrue(c.isCompatible)
        assertNull(c.reason)
    }

    @Test
    fun cannotRun_whenMemoryShort_reportsMemoryReason() {
        val c = compatibilityVerdict(
            modelId = "m",
            requiredMemoryBytes = 2_000,
            requiredStorageBytes = 1_000,
            availableMemoryBytes = 1_000, // short on RAM
            availableStorageBytes = 5_000,
        )
        assertFalse(c.canRun)
        assertFalse(c.isCompatible)
        assertTrue(c.reason!!.contains("memory"))
    }

    @Test
    fun runsButCannotFit_reportsStorageReason() {
        val c = compatibilityVerdict(
            modelId = "m",
            requiredMemoryBytes = 1_000,
            requiredStorageBytes = 4_000,
            availableMemoryBytes = 2_000,
            availableStorageBytes = 1_000, // short on disk
        )
        assertTrue(c.canRun)
        assertFalse(c.canFit)
        assertFalse(c.isCompatible)
        assertTrue(c.reason!!.contains("storage"))
    }

    @Test
    fun boundaryIsInclusive_availableEqualsRequired() {
        val c = compatibilityVerdict(
            modelId = "m",
            requiredMemoryBytes = 1_000,
            requiredStorageBytes = 1_000,
            availableMemoryBytes = 1_000,
            availableStorageBytes = 1_000,
        )
        assertTrue(c.isCompatible) // >= boundary
    }

    // ── rankByCompatibility ─────────────────────────────────────────────────

    private fun verdict(canRun: Boolean, canFit: Boolean) = ModelCompatibility(
        modelId = "x",
        canRun = canRun,
        canFit = canFit,
        requiredMemoryBytes = 0,
        availableMemoryBytes = 0,
        requiredStorageBytes = 0,
        availableStorageBytes = 0,
    )

    @Test
    fun ranking_ordersCompatibleThenRunnableThenRest_largestFirst() {
        val small = model("small", 100)
        val medium = model("medium", 200)
        val large = model("large", 900)   // runs but won't fit
        val huge = model("huge", 2_000)    // can't run

        val scored = listOf(
            small to verdict(canRun = true, canFit = true),
            large to verdict(canRun = true, canFit = false),
            medium to verdict(canRun = true, canFit = true),
            huge to verdict(canRun = false, canFit = false),
        )

        val order = rankByCompatibility(scored).map { it.model.id }
        // fully-compatible first (largest→smallest): medium, small;
        // then runnable-but-won't-fit: large; then the rest: huge.
        assertEquals(listOf("medium", "small", "large", "huge"), order)
    }

    @Test
    fun topPick_isLargestFullyCompatible_notMerelyRunnable() {
        val fitsSmall = model("fits-small", 100)
        val fitsBig = model("fits-big", 300)
        val runsButHuge = model("runs-huge", 5_000) // canRun but !canFit

        val scored = listOf(
            fitsSmall to verdict(canRun = true, canFit = true),
            runsButHuge to verdict(canRun = true, canFit = false),
            fitsBig to verdict(canRun = true, canFit = true),
        )

        val recs = rankByCompatibility(scored)
        val topPick = recs.single { it.isTopPick }
        // Largest that both runs AND fits — NOT the bigger, undownloadable one.
        assertEquals("fits-big", topPick.model.id)
    }

    @Test
    fun topPick_isNull_whenNoModelIsCompatible() {
        val runsButHuge = model("runs-huge", 5_000)
        val tooBig = model("too-big", 9_000)

        val scored = listOf(
            runsButHuge to verdict(canRun = true, canFit = false),
            tooBig to verdict(canRun = false, canFit = false),
        )

        val recs = rankByCompatibility(scored)
        assertTrue(recs.none { it.isTopPick })
    }
}
