package dev.deviceai

/**
 * Parses the JSON response from the native speech C API into a [TranscriptionResult].
 *
 * Extracted from [SpeechBridge] so the bridge focuses solely on C interop calls.
 *
 * Expected JSON format:
 * ```json
 * {"text":"...", "language":"en", "durationMs":1234,
 *  "segments":[{"text":"...","startMs":0,"endMs":500}]}
 * ```
 */
internal object TranscriptionJsonParser {

    fun parse(json: String): TranscriptionResult {
        return try {
            val text       = extractString(json, "text") ?: ""
            val language   = extractString(json, "language") ?: "en"
            val durationMs = extractLong(json, "durationMs") ?: 0L
            val segments   = parseSegments(json)
            TranscriptionResult(text, segments, language, durationMs)
        } catch (_: Exception) {
            TranscriptionResult("", emptyList(), "en", 0L)
        }
    }

    private fun extractString(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.getOrNull(1)

    private fun extractLong(json: String, key: String): Long? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun parseSegments(json: String): List<Segment> {
        val segmentsMatch = Regex("\"segments\"\\s*:\\s*\\[([^\\]]*)\\]").find(json)
            ?: return emptyList()
        val segmentsStr = segmentsMatch.groupValues.getOrNull(1) ?: return emptyList()

        return buildList {
            Regex(
                "\\{[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"[^}]*" +
                    "\"startMs\"\\s*:\\s*(\\d+)[^}]*\"endMs\"\\s*:\\s*(\\d+)[^}]*\\}"
            ).findAll(segmentsStr).forEach { match ->
                add(
                    Segment(
                        text    = match.groupValues.getOrNull(1) ?: "",
                        startMs = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L,
                        endMs   = match.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
                    )
                )
            }
        }
    }
}
