package dev.deviceai.llm

import dev.deviceai.llm.engine.LlmJniEngine
import dev.deviceai.llm.rag.RagAugmentor
import kotlinx.coroutines.flow.Flow

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object LlmCppBridge {
    actual fun initLlm(modelPath: String, config: LlmInitConfig) = LlmJniEngine.init(modelPath, config)
    actual fun shutdown() = LlmJniEngine.shutdown()
    actual fun generate(messages: List<LlmMessage>, config: LlmGenConfig) =
        LlmJniEngine.generate(
            if (config.ragStore != null) RagAugmentor.augment(messages, config) else messages,
            config,
        )
    actual fun generateStream(messages: List<LlmMessage>, config: LlmGenConfig): Flow<String> =
        LlmJniEngine.generateStream(
            if (config.ragStore != null) RagAugmentor.augment(messages, config) else messages,
            config,
        )
    actual fun cancelGeneration() = LlmJniEngine.cancelGeneration()
}
