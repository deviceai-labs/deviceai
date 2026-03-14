package dev.deviceai.llm

import dev.deviceai.core.DeviceAI

/**
 * LLM inference namespace. Access via [DeviceAI.llm].
 *
 * Do not instantiate directly.
 */
object LlmModule {

    /**
     * Create a new [ChatSession].
     *
     * Provide the absolute path to a GGUF model file. In Development mode this
     * is a local path you manage. In Staging / Production (Phase 2), the path
     * will be resolved automatically from the backend manifest.
     *
     * ```kotlin
     * // Minimal
     * val session = DeviceAI.llm.chat("/data/.../model.gguf")
     * session.send("Hello").collect { print(it) }
     *
     * // Configured
     * val session = DeviceAI.llm.chat("/data/.../model.gguf") {
     *     systemPrompt = "You are a helpful assistant."
     *     temperature  = 0.8f
     *     maxTokens    = 512
     *     gpuLayers    = 99
     * }
     * ```
     *
     * @param modelPath Absolute path to a GGUF model file.
     * @param block     Optional DSL to configure the session. See [ChatConfig].
     */
    fun chat(modelPath: String, block: ChatConfig.() -> Unit = {}): ChatSession {
        val config = ChatConfig().apply(block)
        return ChatSession(modelPath, config)
    }

    // TODO: Phase 2 — fun chat(block: ChatConfig.() -> Unit = {}): ChatSession
    //   No modelPath needed — assigned by the backend manifest.
}

/**
 * Access the LLM inference module.
 *
 * ```kotlin
 * val session = DeviceAI.llm.chat("/path/to/model.gguf")
 * ```
 */
val DeviceAI.llm: LlmModule get() = LlmModule
