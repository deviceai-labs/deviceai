# DeviceAI Runtime

**On-device AI runtime for Kotlin, iOS, Flutter, and React Native. Ship speech recognition, synthesis, and LLM inference on Android, iOS, and Desktop — no cloud required, no latency, no privacy risk.**

[![Build](https://github.com/deviceai-labs/deviceai/actions/workflows/ci.yml/badge.svg)](https://github.com/deviceai-labs/deviceai/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/dev.deviceai/speech)](https://central.sonatype.com/artifact/dev.deviceai/speech)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Kotlin_Multiplatform-Android%20%7C%20iOS%20%7C%20Desktop-blue)](https://www.jetbrains.com/kotlin-multiplatform/)

---

## What's available

| Module | Language | Distribution | Status |
|--------|----------|--------------|--------|
| `kotlin/core` | Kotlin (Android + KMP) | Maven Central `dev.deviceai:core` | ✅ Available |
| `kotlin/speech` | Kotlin (Android + KMP) | Maven Central `dev.deviceai:speech` | ✅ Available |
| `kotlin/llm` | Kotlin (Android + KMP) | Maven Central `dev.deviceai:llm` | ✅ Available |
| `ios/speech` | Swift | Swift Package Index | 🗓 Planned |
| `flutter/speech` | Dart | pub.dev `deviceai_speech` | 🗓 Planned |
| `react-native/speech` | TypeScript | npm `react-native-deviceai-speech` | 🗓 Planned |

Each SDK is **independent and native to its platform** — they all call the same C++ engines (whisper.cpp, sherpa-onnx, llama.cpp) directly, with no cross-language bridging.

---

## Repository structure

```
deviceai/
├── kotlin/
│   ├── core/       dev.deviceai:core    ✅  model management, storage, logging
│   ├── speech/     dev.deviceai:speech  ✅  STT (Whisper) + TTS (sherpa-onnx) + VAD
│   └── llm/        dev.deviceai:llm     ✅  LLM inference via llama.cpp + offline RAG
├── ios/
│   └── speech/     Swift Package            🗓  Swift async/await wrapper
├── flutter/
│   └── speech/     pub.dev: deviceai_speech 🗓  Flutter plugin
├── react-native/
│   └── speech/     npm: react-native-deviceai-speech  🗓  TurboModule
└── samples/
    ├── composeApp/ Compose Multiplatform demo  ✅
    └── iosApp/     native iOS shell            ✅
```

---

## Integration — Kotlin (Android, KMP, Desktop)

### Step 1 — Add dependencies

```kotlin
// build.gradle.kts
implementation("dev.deviceai:core:0.2.0-alpha02")
implementation("dev.deviceai:speech:0.2.0-alpha02")   // STT + TTS + VAD
implementation("dev.deviceai:llm:0.2.0-alpha02")      // LLM inference + RAG
```

No extra repository config needed — all artifacts are on Maven Central.

---

### Step 2 — Initialize the SDK

Call `DeviceAI.initialize()` **once** at app startup before using any module.

#### Android

```kotlin
import dev.deviceai.core.DeviceAI
import dev.deviceai.core.Environment
import dev.deviceai.models.PlatformStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformStorage.initialize(this)
        DeviceAI.initialize(context = this) {
            environment = Environment.Development
        }
        setContent { App() }
    }
}
```

#### iOS (Kotlin side of a KMP project)

```kotlin
import dev.deviceai.core.DeviceAI
import dev.deviceai.core.Environment

private val sdkInit by lazy {
    DeviceAI.initialize { environment = Environment.Development }
}

fun MainViewController(): UIViewController {
    sdkInit
    return ComposeUIViewController { App() }
}
```

#### Desktop

```kotlin
import dev.deviceai.core.DeviceAI
import dev.deviceai.core.Environment

fun main() = application {
    DeviceAI.initialize { environment = Environment.Development }
    Window(onCloseRequest = ::exitApplication, title = "My App") { App() }
}
```

#### With cloud backend (Staging / Production)

```kotlin
DeviceAI.initialize(context = this, apiKey = "dai_live_...") {
    environment   = Environment.Production
    telemetry     = Telemetry.Enabled
    appVersion    = BuildConfig.VERSION_NAME
    appAttributes = mapOf("user_tier" to "premium")
}
```

---

### Step 3 — Download a model

`ModelRegistry` fetches the catalog from HuggingFace and downloads models to local storage. Downloads are resumable on interruption.

```kotlin
import dev.deviceai.models.ModelRegistry

val model = ModelRegistry.getOrDownload("ggml-tiny.en.bin") { progress ->
    println("${progress.percentComplete.toInt()}% — ${progress.bytesDownloaded / 1_000_000}MB")
}
```

> **whisper-tiny.en** (75 MB) runs 7× faster than real-time on mid-range Android hardware.

---

### Step 4 — Transcribe speech

```kotlin
import dev.deviceai.SpeechBridge
import dev.deviceai.SttConfig

SpeechBridge.initStt(model.modelPath, SttConfig(language = "en", useGpu = true))

val text: String = SpeechBridge.transcribeAudio(samples) // FloatArray, 16kHz mono PCM
// or
val text: String = SpeechBridge.transcribe("/path/to/audio.wav")

SpeechBridge.shutdownStt()
```

---

### Step 5 — Synthesize speech (optional)

```kotlin
import dev.deviceai.SpeechBridge
import dev.deviceai.TtsConfig

SpeechBridge.initTts(
    modelPath  = voice.modelPath,
    tokensPath = voice.tokensPath,
    config     = TtsConfig(speechRate = 1.0f)
)

val pcm: ShortArray = SpeechBridge.synthesize("Hello from DeviceAI.")
// Play with AudioTrack (Android), AVAudioEngine (iOS), or javax.sound (Desktop)

SpeechBridge.shutdownTts()
```

---

### Step 6 — Run a local LLM

```kotlin
import dev.deviceai.core.DeviceAI
import dev.deviceai.llm.llm

// Create a chat session — model loads once, history is automatic
val session = DeviceAI.llm.chat("/path/to/model.gguf") {
    systemPrompt = "You are a helpful assistant."
    maxTokens    = 512
    temperature  = 0.7f
    useGpu       = true
}

// Streaming (recommended for UI)
session.send("What is Kotlin Multiplatform?")
    .collect { token -> print(token) }

// Multi-turn — history managed automatically
session.send("Give me a code example.").collect { print(it) }

// Blocking (scripts / tests)
val reply = session.sendBlocking("Summarise in one line.")

// Lifecycle
session.cancel()       // abort in-progress generation
session.clearHistory() // fresh conversation, model stays loaded
session.close()        // unload model, free resources
```

---

### Step 7 — Offline RAG (optional)

Attach a `BM25RagStore` to inject local documents as context — no embedding model required.

```kotlin
import dev.deviceai.llm.rag.BM25RagStore

val store = BM25RagStore(rawChunks = listOf(
    "DeviceAI supports Android, iOS, and Desktop.",
    "LLM inference uses llama.cpp with Metal on Apple Silicon."
))

val session = DeviceAI.llm.chat("/path/to/model.gguf") {
    ragStore = store
}

session.send("Which platforms does DeviceAI support?").collect { print(it) }
```

---

## Environments

| Environment | API key | Backend | Log level | Use for |
|-------------|---------|---------|-----------|---------|
| `Development` | not required | none — local model path | DEBUG | local dev, unit tests |
| `Staging` | required | staging.api.deviceai.dev | DEBUG | pre-release QA |
| `Production` | required | api.deviceai.dev | WARN | release builds |

---

## Architecture

```
Your App
    │
    ▼
DeviceAI.initialize(context, apiKey) { environment = Environment.Development }
    │
    ├── kotlin/core   (dev.deviceai:core)
    │       DeviceAI           — unified SDK entry point
    │       CoreSDKLogger       — structured, environment-aware logging
    │       ModelRegistry       — model discovery, download, local management
    │       PlatformStorage     — cross-platform file I/O
    │
    ├── kotlin/speech  (dev.deviceai:speech)
    │       SpeechBridge        — unified STT + TTS Kotlin API
    │           │
    │           ├── Android / Desktop  →  JNI → libdeviceai_speech_jni.so/.dylib
    │           └── iOS  →  C Interop → libspeech_merged.a
    │                           ├── whisper.cpp   (STT)
    │                           └── sherpa-onnx   (TTS + VAD)
    │
    └── kotlin/llm  (dev.deviceai:llm)
            DeviceAI.llm.chat()   — creates a ChatSession
            ChatSession            — stateful conversation, streaming Flow<String>
            BM25RagStore           — offline retrieval-augmented generation
                │
                ├── Android / Desktop  →  JNI → libdeviceai_llm_jni.so/.dylib
                └── iOS  →  C Interop → libllm_merged.a
                                └── llama.cpp (Metal + CoreML)
```

---

## Features

| Feature | Status |
|---------|--------|
| Speech-to-Text (Whisper) | ✅ Android, iOS, Desktop |
| Text-to-Speech (sherpa-onnx VITS / Kokoro) | ✅ Android, iOS, Desktop |
| Voice Activity Detection (Silero VAD) | ✅ Android, iOS, Desktop |
| LLM inference (llama.cpp) | ✅ Android, iOS, Desktop |
| Offline RAG (BM25) | ✅ Android, iOS, Desktop |
| Streaming LLM generation (`Flow<String>`) | ✅ Android, iOS, Desktop |
| Stateful `ChatSession` with auto history | ✅ |
| Auto model download (HuggingFace) | ✅ |
| GPU acceleration (Metal / Vulkan) | ✅ |
| Cloud backend — OTA models, telemetry | 🚧 In progress |
| Swift SDK | 🗓 Planned |
| Flutter plugin | 🗓 Planned |
| React Native module | 🗓 Planned |
| Tool calling / voice agents | 🗓 Planned |

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
| SmolLM2-1.7B-Instruct (Q4) | ~1 GB | Balanced |
| Qwen2.5-0.5B-Instruct (Q4) | ~400 MB | Multilingual, compact |
| Llama-3.2-1B-Instruct (Q4) | ~700 MB | Strong reasoning |

Browse all available models via `LlmCatalog`.

---

## Platform support

| Platform | STT | TTS | LLM | Sample App |
|----------|-----|-----|-----|------------|
| Android (API 26+) | ✅ | ✅ | ✅ | ✅ |
| iOS 17+ | ✅ | ✅ | ✅ | ✅ |
| macOS Desktop | ✅ | ✅ | ✅ | ✅ |

---

## Benchmarks

| Device | Chip | Model | Audio | Inference | RTF |
|--------|------|-------|-------|-----------|-----|
| Redmi Note 9 Pro | Snapdragon 720G | whisper-tiny | 5.4s | 746ms | **0.14x** |

> RTF < 1.0 = faster than real-time. 0.14x = ~7× faster than real-time on a mid-range Android phone.

---

## Building from source

**Prerequisites:** CMake 3.22+, Android NDK r26+, Xcode 26+ (iOS), Kotlin 2.2+

```bash
git clone --recursive https://github.com/deviceai-labs/deviceai.git
cd deviceai

# Compile checks
./gradlew :kotlin:core:compileKotlinJvm
./gradlew :kotlin:speech:compileKotlinJvm
./gradlew :kotlin:llm:compileKotlinJvm

# Run the desktop sample
./gradlew :samples:composeApp:run
```

---

## Roadmap

- [x] Kotlin SDK — speech, LLM, RAG, streaming
- [x] `DeviceAI` unified entry point with `Environment` + `CloudConfig` DSL
- [x] `ChatSession` — stateful multi-turn LLM conversations
- [ ] Backend integration — device registration, OTA model assignment, telemetry
- [ ] Swift SDK — native iOS/macOS package
- [ ] Flutter SDK
- [ ] React Native SDK
- [ ] Tool calling / voice agents (`DeviceAI.agent`)

---

## Sample App

`samples/composeApp/` is a working Compose Multiplatform demo. Runs on Android, iOS, and Desktop.

```bash
# Desktop
./gradlew :samples:composeApp:run

# Android — open in Android Studio and run on device/emulator

# iOS — open samples/iosApp/iosApp.xcodeproj in Xcode and run
```

---

## Contributing

Issues and PRs welcome. Platform wrapper contributions (`ios/`, `flutter/`, `react-native/`) are especially welcome — each stub directory contains a README with the expected API surface.
