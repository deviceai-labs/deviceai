package dev.deviceai.models

private const val HF = "https://huggingface.co"

/**
 * Hardcoded catalog of sherpa-onnx TTS voices available for download.
 *
 * All models are hosted on HuggingFace and can be downloaded as individual files
 * (no tar.bz2 extraction required).
 *
 * Kokoro models: model.onnx + tokens.txt + voices.bin
 * VITS models:   model.onnx + tokens.txt
 *
 * New voices can be added here without any other changes — the [TtsDownloadStrategy]
 * and [ModelRegistry.getTtsVoices] will automatically pick them up.
 */
internal object TtsCatalog {

    fun getVoices(): List<TtsVoiceInfo> = voices

    fun getVoices(languageCode: String): List<TtsVoiceInfo> =
        voices.filter { it.languageCode == languageCode }

    private val voices: List<TtsVoiceInfo> = listOf(

        // ── Kokoro English (multi-speaker, 54 voices) ──────────────────────
        TtsVoiceInfo(
            id           = "kokoro-en-v0_19",
            displayName  = "Kokoro English (54 voices)",
            sizeBytes    = 305_000_000L,
            languageCode = "en",
            modelType    = TtsModelType.KOKORO,
            modelUrl     = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/kokoro-en-v0_19.onnx",
            tokensUrl    = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/tokens.txt",
            voicesUrl    = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/voices.bin",
            numSpeakers  = 54
        ),

        // ── Kokoro English int8 (smaller, faster) ──────────────────────────
        TtsVoiceInfo(
            id           = "kokoro-en-v0_19-int8",
            displayName  = "Kokoro English int8 (54 voices, faster)",
            sizeBytes    = 92_000_000L,
            languageCode = "en",
            modelType    = TtsModelType.KOKORO,
            modelUrl     = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/kokoro-en-v0_19.int8.onnx",
            tokensUrl    = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/tokens.txt",
            voicesUrl    = "$HF/csukuangfj/kokoro-en-v0_19/resolve/main/voices.bin",
            numSpeakers  = 54
        ),

        // ── VITS Chinese (no espeak-ng needed) ────────────────────────────
        TtsVoiceInfo(
            id           = "vits-zh-aishell3",
            displayName  = "VITS Chinese AiShell3 (174 speakers)",
            sizeBytes    = 117_000_000L,
            languageCode = "zh",
            modelType    = TtsModelType.VITS,
            modelUrl     = "$HF/csukuangfj/vits-zh-aishell3/resolve/main/vits-aishell3.onnx",
            tokensUrl    = "$HF/csukuangfj/vits-zh-aishell3/resolve/main/tokens.txt",
            numSpeakers  = 174
        ),
    )
}
