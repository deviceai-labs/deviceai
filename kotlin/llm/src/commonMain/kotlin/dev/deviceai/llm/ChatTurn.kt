package dev.deviceai.llm

/**
 * A single exchange in a [ChatSession] conversation.
 *
 * Exposed via [ChatSession.history] as a clean pair of strings.
 * Internal [LlmMessage] types are never surfaced to callers.
 */
data class ChatTurn(
    /** The user's message. */
    val user: String,
    /** The assistant's response. */
    val assistant: String,
)
