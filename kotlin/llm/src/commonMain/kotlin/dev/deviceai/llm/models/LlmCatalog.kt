package dev.deviceai.llm.models

/**
 * Curated catalog of GGUF models recommended for on-device inference.
 *
 * All models are hosted on HuggingFace and use Q4_K_M quantization by default
 * for a good balance of quality and size on mobile hardware.
 *
 * Usage:
 * ```kotlin
 * val model = LlmCatalog.LLAMA_3_2_1B_INSTRUCT_Q4
 * // download via LlmRegistry, then:
 * LlmCppBridge.initLlm(localModel.modelPath)
 * ```
 */
object LlmCatalog {

    // ══════════════════════════════════════════════════════════════
    //                   SmolLM2 (HuggingFace) — tiny / quick demo
    // ══════════════════════════════════════════════════════════════

    /** 135M — ~105 MB, ultra-fast, minimal RAM, good for demos */
    val SMOLLM2_135M_INSTRUCT_Q4 = LlmModelInfo(
        id = "smollm2-135m-instruct-q4_k_m",
        name = "SmolLM2 135M Instruct Q4_K_M",
        repoId = "bartowski/SmolLM2-135M-Instruct-GGUF",
        filename = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        sizeBytes = 110_100_480L,
        quantization = "Q4_K_M",
        parameters = "135M",
        description = "Smallest practical chat model. Extremely fast on any device. Great for testing."
    )

    /** 360M — ~271 MB, fast inference, good quality for size */
    val SMOLLM2_360M_INSTRUCT_Q4 = LlmModelInfo(
        id = "smollm2-360m-instruct-q4_k_m",
        name = "SmolLM2 360M Instruct Q4_K_M",
        repoId = "bartowski/SmolLM2-360M-Instruct-GGUF",
        filename = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
        sizeBytes = 271_000_000L,
        quantization = "Q4_K_M",
        parameters = "360M",
        description = "Small and fast on-device model. Good balance of quality and download size."
    )

    // ══════════════════════════════════════════════════════════════
    //                   Qwen 2.5 (Alibaba)
    // ══════════════════════════════════════════════════════════════

    /** 0.5B — ~397 MB, strong reasoning for its size */
    val QWEN2_5_0_5B_INSTRUCT_Q4 = LlmModelInfo(
        id = "qwen2.5-0.5b-instruct-q4_k_m",
        name = "Qwen2.5 0.5B Instruct Q4_K_M",
        repoId = "bartowski/Qwen2.5-0.5B-Instruct-GGUF",
        filename = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        sizeBytes = 419_430_400L,
        quantization = "Q4_K_M",
        parameters = "0.5B",
        description = "Alibaba's compact model. Strong reasoning and multilingual support."
    )

    // ══════════════════════════════════════════════════════════════
    //                   Llama 3.2 (Meta)
    // ══════════════════════════════════════════════════════════════

    /** 1B — ~742 MB, best quality in the sub-1GB range */
    val LLAMA_3_2_1B_INSTRUCT_Q4 = LlmModelInfo(
        id = "llama-3.2-1b-instruct-q4_k_m",
        name = "Llama 3.2 1B Instruct Q4_K_M",
        repoId = "bartowski/Llama-3.2-1B-Instruct-GGUF",
        filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        sizeBytes = 742_000_000L,
        quantization = "Q4_K_M",
        parameters = "1B",
        description = "Meta's smallest Llama 3.2 model. Good quality for simple tasks."
    )

    /** 3B — ~1.9 GB, strong reasoning, needs ~2 GB RAM */
    val LLAMA_3_2_3B_INSTRUCT_Q4 = LlmModelInfo(
        id = "llama-3.2-3b-instruct-q4_k_m",
        name = "Llama 3.2 3B Instruct Q4_K_M",
        repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
        filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        sizeBytes = 1_890_000_000L,
        quantization = "Q4_K_M",
        parameters = "3B",
        description = "Strong reasoning and instruction-following. Best balance for most phones."
    )

    // ══════════════════════════════════════════════════════════════
    //                   Gemma 2 (Google)
    // ══════════════════════════════════════════════════════════════

    /** 2B — ~1.6 GB, strong multilingual support */
    val GEMMA_2_2B_INSTRUCT_Q4 = LlmModelInfo(
        id = "gemma-2-2b-instruct-q4_k_m",
        name = "Gemma 2 2B Instruct Q4_K_M",
        repoId = "bartowski/gemma-2-2b-it-GGUF",
        filename = "gemma-2-2b-it-Q4_K_M.gguf",
        sizeBytes = 1_630_000_000L,
        quantization = "Q4_K_M",
        parameters = "2B",
        description = "Google's compact model. Strong multilingual and code performance."
    )

    // ══════════════════════════════════════════════════════════════
    //                   Phi-3.5 (Microsoft)
    // ══════════════════════════════════════════════════════════════

    /** 3.8B — ~2.4 GB, punches above its weight for reasoning */
    val PHI_3_5_MINI_INSTRUCT_Q4 = LlmModelInfo(
        id = "phi-3.5-mini-instruct-q4_k_m",
        name = "Phi-3.5 Mini Instruct Q4_K_M",
        repoId = "bartowski/Phi-3.5-mini-instruct-GGUF",
        filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
        sizeBytes = 2_390_000_000L,
        quantization = "Q4_K_M",
        parameters = "3.8B",
        description = "Microsoft's efficient model. Excellent reasoning relative to size."
    )

    // ══════════════════════════════════════════════════════════════
    //                   Convenience list
    // ══════════════════════════════════════════════════════════════

    /** All curated models, ordered by size ascending */
    val all: List<LlmModelInfo> = listOf(
        SMOLLM2_135M_INSTRUCT_Q4,
        SMOLLM2_360M_INSTRUCT_Q4,
        QWEN2_5_0_5B_INSTRUCT_Q4,
        LLAMA_3_2_1B_INSTRUCT_Q4,
        GEMMA_2_2B_INSTRUCT_Q4,
        LLAMA_3_2_3B_INSTRUCT_Q4,
        PHI_3_5_MINI_INSTRUCT_Q4
    )
}
