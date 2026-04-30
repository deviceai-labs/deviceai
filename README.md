# DeviceAI

**On-device AI for Android — speech recognition, text-to-speech, and LLM chat. Zero cloud latency, zero privacy risk. Optional cloud backend for OTA model updates, telemetry, and device management.**

[![Build](https://github.com/deviceai-labs/deviceai/actions/workflows/ci.yml/badge.svg)](https://github.com/deviceai-labs/deviceai/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/dev.deviceai/core)](https://central.sonatype.com/artifact/dev.deviceai/core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android-green)](https://developer.android.com)

---

## Install

```kotlin
// build.gradle.kts
implementation("dev.deviceai:core:0.3.0-alpha01")
implementation("dev.deviceai:speech:0.3.0-alpha01")   // STT + TTS
implementation("dev.deviceai:llm:0.3.0-alpha01")      // LLM + RAG
```

---

## Initialize

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlatformStorage.initialize(this)
        DeviceAI.initialize(context = this)
    }
}
```

That's it. The SDK runs fully on-device with no backend required.

### With cloud backend (optional)

```kotlin
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    telemetry = TelemetryLevel.Minimal
    appVersion = BuildConfig.VERSION_NAME
}
```

The API key connects the SDK to the DeviceAI cloud backend. Device hardware (RAM, CPU, SoC) is detected automatically — no manual configuration needed.

---

## Speech-to-Text

```kotlin
SpeechBridge.initStt(modelPath, SttConfig(language = "en", useGpu = true))

// From raw audio samples
val text = SpeechBridge.transcribeAudio(samples)  // FloatArray, 16kHz mono

// From a WAV file
val textFromFile = SpeechBridge.transcribe("/path/to/audio.wav")

SpeechBridge.shutdownStt()
```

Powered by [whisper.cpp](https://github.com/ggerganov/whisper.cpp). Runs 7× faster than real-time on mid-range Android hardware.

## Text-to-Speech

```kotlin
SpeechBridge.initTts(modelPath, tokensPath, TtsConfig(speechRate = 1.0f))

val pcm: ShortArray = SpeechBridge.synthesize("Hello from DeviceAI.")
// Play with AudioTrack

SpeechBridge.shutdownTts()
```

Powered by [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Supports VITS and Kokoro voice models.

## LLM Chat

```kotlin
val session = DeviceAI.llm.chat("/path/to/model.gguf") {
    systemPrompt = "You are a helpful assistant."
    maxTokens = 512
    temperature = 0.7f
    useGpu = true
}

// Streaming (recommended for UI)
session.send("What is Kotlin?").collect { token -> print(token) }

// Multi-turn — history managed automatically
session.send("Give me an example.").collect { print(it) }

// Lifecycle
session.cancel()        // abort generation
session.clearHistory()  // fresh conversation
session.close()         // unload model
```

Powered by [llama.cpp](https://github.com/ggerganov/llama.cpp). Supports any GGUF model with Vulkan GPU acceleration.

## Offline RAG

```kotlin
val store = BM25RagStore(rawChunks = listOf(
    "DeviceAI supports Android and iOS.",
    "LLM inference uses llama.cpp with Vulkan GPU."
))
val session = DeviceAI.llm.chat("/path/to/model.gguf") { ragStore = store }
session.send("What GPU does DeviceAI use?").collect { print(it) }
```

No embedding model needed — BM25 keyword retrieval runs entirely on-device.

---

## Telemetry

When telemetry is enabled, the SDK automatically tracks performance metrics for all modules:

### What's collected

| Module | Metrics |
|--------|---------|
| **STT** | Model load time, transcription latency, audio duration (input_length_ms) |
| **TTS** | Model load time, synthesis latency, text length (output_chars) |
| **LLM** | Model load time, inference latency, time-to-first-token, tokens/sec, token counts, finish reason |

### What's NEVER collected

- Prompt or response text content
- Audio recordings or transcript content
- PII by default

> Apps should avoid putting PII in `appAttributes`, since developer-provided attributes are sent in the capability profile.

### Telemetry levels

```kotlin
// Off (default) — nothing sent
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    telemetry = TelemetryLevel.Off
}

// Minimal — model load/unload + inference metrics
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    telemetry = TelemetryLevel.Minimal
}

// Full — includes OTA downloads + manifest syncs
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    telemetry = TelemetryLevel.Full
}
```

Events are batched on-device and delivered efficiently — respects Wi-Fi preference, data-saver mode, and flushes automatically when the app goes to background.

### Custom telemetry sink

Route events to your own analytics instead of the DeviceAI backend:

```kotlin
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    telemetry = TelemetryLevel.Minimal
    telemetrySink = object : TelemetrySink {
        override suspend fun ingest(events: List<TelemetryEvent>) {
            myAnalytics.track(events)
        }
    }
}
```

---

## Cloud Backend

The SDK optionally connects to a cloud control plane. When an API key is provided:

| Feature | What happens |
|---|---|
| **Device registration** | Automatic — hardware profile sent, capability tier assigned |
| **Model manifest** | Backend assigns the right model for each device tier, synced every 6h |
| **OTA updates** | Push new models with canary rollouts and instant kill-switch |
| **Telemetry** | Performance metrics batched and delivered (when enabled) |
| **Device identity** | Stable across reinstalls — same device always gets the same ID |

No cloud calls are made without an API key. Local mode works fully offline.

---

## Models

### Whisper (STT)

| Model | Size | Speed | Best for |
|-------|------|-------|----------|
| `ggml-tiny.en.bin` | 75 MB | 7× real-time | English, mobile-first |
| `ggml-base.bin` | 142 MB | Fast | Multilingual, balanced |
| `ggml-small.bin` | 466 MB | Medium | Higher accuracy |

### LLM (GGUF via llama.cpp)

| Model | Size | Best for |
|-------|------|----------|
| SmolLM2-360M-Instruct (Q4) | ~220 MB | Fastest, mobile-first |
| Qwen2.5-0.5B-Instruct (Q4) | ~400 MB | Multilingual, compact |
| Llama-3.2-1B-Instruct (Q4) | ~700 MB | Strong reasoning |
| SmolLM2-1.7B-Instruct (Q4) | ~1 GB | Balanced |

Browse LLM models with `LlmCatalog`. Download Whisper/TTS models via `ModelRegistry`.

---

## Features

| Feature | Status |
|---------|--------|
| Speech-to-Text (whisper.cpp) | ✅ |
| Text-to-Speech (sherpa-onnx VITS / Kokoro) | ✅ |
| Voice Activity Detection | ✅ |
| LLM inference (llama.cpp, GGUF) | ✅ |
| Streaming generation (`Flow<String>`) | ✅ |
| Stateful multi-turn chat | ✅ |
| Offline RAG (BM25) | ✅ |
| Auto model download (HuggingFace) | ✅ |
| GPU acceleration (Vulkan) | ✅ |
| Cloud backend (registration, manifest, telemetry) | ✅ |
| Auto hardware detection | ✅ |
| Stable device identity (survives reinstall) | ✅ |
| STT/TTS/LLM telemetry | ✅ |
| Custom telemetry sink | ✅ |
| OTA model rollouts + kill switch | ✅ |
| Swift SDK (iOS / macOS) | 🚧 In progress |
| Flutter plugin | 🗓 Planned |
| React Native module | 🗓 Planned |
| Developer dashboard | 🗓 Planned |

---

## Platform support

| Platform | STT | TTS | LLM | Status |
|----------|-----|-----|-----|--------|
| Android (API 26+) | ✅ | ✅ | ✅ | Available |
| iOS / macOS | — | — | — | Swift SDK in progress |
| Flutter | — | — | — | Planned |
| React Native | — | — | — | Planned |

---

## Benchmarks

| Device | SoC | Model | Audio | Inference | RTF |
|--------|-----|-------|-------|-----------|-----|
| Redmi Note 9 Pro | Snapdragon 720G | whisper-tiny.en | 5.4s | 746ms | **0.14x** |

> RTF < 1.0 = faster than real-time. 0.14x = ~7× faster than real-time.

---

## Building from source

```bash
git clone https://github.com/deviceai-labs/deviceai.git
cd deviceai
make setup
./gradlew :kotlin:core:compileDebugKotlinAndroid
./gradlew :kotlin:speech:compileDebugKotlinAndroid
./gradlew :kotlin:llm:compileDebugKotlinAndroid
```

---

## Sample App

```bash
# Open samples/androidApp/ in Android Studio and run on device/emulator
```

---

## Contributing

Issues and PRs welcome. Platform SDK contributions (`swift/`, `flutter/`, `react-native/`) are especially welcome.

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
