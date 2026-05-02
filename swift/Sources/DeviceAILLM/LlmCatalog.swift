/// Curated LLM models available for on-device inference.
public enum LlmCatalog {

    public struct ModelInfo: Sendable {
        public let id: String
        public let name: String
        public let repoId: String
        public let filename: String
        public let sizeBytes: Int64
        public let quantization: String
        public let parameters: String
        public let description: String
    }

    public static let all: [ModelInfo] = [
        ModelInfo(
            id: "smollm2-135m-q4", name: "SmolLM2 135M",
            repoId: "bartowski/SmolLM2-135M-Instruct-GGUF", filename: "SmolLM2-135M-Instruct-Q4_K_M.gguf",
            sizeBytes: 104_000_000, quantization: "Q4_K_M", parameters: "135M",
            description: "Smallest, fastest. Good for simple tasks."
        ),
        ModelInfo(
            id: "smollm2-360m-q4", name: "SmolLM2 360M",
            repoId: "bartowski/SmolLM2-360M-Instruct-GGUF", filename: "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            sizeBytes: 220_000_000, quantization: "Q4_K_M", parameters: "360M",
            description: "Mobile-first. Fast with decent quality."
        ),
        ModelInfo(
            id: "qwen2.5-0.5b-q4", name: "Qwen2.5 0.5B",
            repoId: "bartowski/Qwen2.5-0.5B-Instruct-GGUF", filename: "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            sizeBytes: 400_000_000, quantization: "Q4_K_M", parameters: "0.5B",
            description: "Multilingual, compact."
        ),
        ModelInfo(
            id: "llama-3.2-1b-q4", name: "Llama 3.2 1B",
            repoId: "bartowski/Llama-3.2-1B-Instruct-GGUF", filename: "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes: 700_000_000, quantization: "Q4_K_M", parameters: "1B",
            description: "Strong reasoning. Best quality/size balance."
        ),
        ModelInfo(
            id: "smollm2-1.7b-q4", name: "SmolLM2 1.7B",
            repoId: "bartowski/SmolLM2-1.7B-Instruct-GGUF", filename: "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            sizeBytes: 1_000_000_000, quantization: "Q4_K_M", parameters: "1.7B",
            description: "Balanced performance and quality."
        ),
    ]
}
