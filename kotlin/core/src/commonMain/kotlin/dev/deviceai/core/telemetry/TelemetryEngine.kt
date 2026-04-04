package dev.deviceai.core.telemetry

import dev.deviceai.core.CoreSDKLogger
import kotlinx.coroutines.*

/**
 * In-memory ring buffer for SDK telemetry events.
 *
 * - [record] is safe to call from any coroutine context.
 * - Buffer capacity is [BUFFER_CAPACITY] events. When full, the oldest event is dropped.
 * - Auto-flushes in the background when [FLUSH_THRESHOLD] events accumulate.
 * - Call [flush] explicitly before app backgrounding or on Wi-Fi availability.
 * - Call [close] at SDK shutdown to attempt a final flush.
 */
internal class TelemetryEngine(
    private val level: TelemetryLevel,
    private val flushFn: suspend (List<TelemetryEvent>) -> Unit,
) {
    private val buffer = ArrayDeque<TelemetryEvent>(BUFFER_CAPACITY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Record an event. Events that don't meet the current [TelemetryLevel] are silently
     * dropped without allocating. Thread-safe via synchronized access.
     */
    fun record(event: TelemetryEvent) {
        if (!shouldRecord(event)) return
        synchronized(buffer) {
            if (buffer.size >= BUFFER_CAPACITY) {
                buffer.removeFirst() // drop oldest to make room
            }
            buffer.addLast(event)
            buffer.size
        }.let { size ->
            if (size >= FLUSH_THRESHOLD) {
                scope.launch { flush() }
            }
        }
    }

    /**
     * Drain the buffer and send to the backend via [flushFn].
     * On failure, events are re-queued at the front of the buffer (up to remaining capacity).
     */
    suspend fun flush() {
        val batch = synchronized(buffer) {
            if (buffer.isEmpty()) return
            buffer.toList().also { buffer.clear() }
        }
        try {
            flushFn(batch)
            CoreSDKLogger.debug("TelemetryEngine", "flushed ${batch.size} events")
        } catch (e: Exception) {
            CoreSDKLogger.warn("TelemetryEngine", "flush failed — re-queuing: ${e.message}")
            // Re-add to front of buffer (best-effort, up to remaining capacity)
            synchronized(buffer) {
                val space = BUFFER_CAPACITY - buffer.size
                batch.takeLast(space).reversed().forEach { buffer.addFirst(it) }
            }
        }
    }

    /**
     * Best-effort final flush. Should be called at SDK shutdown or app termination.
     */
    fun close() {
        scope.launch {
            flush()
        }.invokeOnCompletion {
            scope.cancel()
        }
    }

    private fun shouldRecord(event: TelemetryEvent): Boolean = when (level) {
        TelemetryLevel.Off -> false
        TelemetryLevel.Minimal -> event is TelemetryEvent.ModelLoad
                || event is TelemetryEvent.ModelUnload
                || event is TelemetryEvent.InferenceComplete
        TelemetryLevel.Full -> true
    }

    companion object {
        /** Maximum events held in-memory between flushes. Oldest is dropped when exceeded. */
        const val BUFFER_CAPACITY = 256

        /** Auto-flush trigger threshold. */
        const val FLUSH_THRESHOLD = 100
    }
}
