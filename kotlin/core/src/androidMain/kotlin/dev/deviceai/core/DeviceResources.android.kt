package dev.deviceai.core

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

/**
 * Live device-resource probes for model-compatibility checks.
 *
 * These read *current* values (not captured at SDK init) because available
 * memory is dynamic — it's what actually determines whether a model loads
 * without thrashing. Android-specific helpers; callers on Android use them to
 * feed the platform-agnostic compatibility logic in the LLM module.
 */

/** Current free RAM in bytes (`ActivityManager.MemoryInfo.availMem`). 0 if unavailable. */
fun availableMemoryBytes(context: Any?): Long {
    val ctx = context as? Context ?: return 0L
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.availMem
}

/** Current free storage in bytes on the data partition. 0 if unavailable. */
fun availableStorageBytes(): Long = try {
    StatFs(Environment.getDataDirectory().path).availableBytes
} catch (_: Exception) {
    0L
}
