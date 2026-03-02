# flutter/speech

> **Status: planned**

Flutter plugin for on-device Speech-to-Text and Text-to-Speech, backed by
[`android/speech`](../../android/speech) on Android and [`ios/speech`](../../ios/speech) on iOS.

## What it will provide

- `DeviceAISpeech` Dart class with `startListening()`, `stopListening()`, `speak()` APIs
- Stream-based transcription results
- Distributed via [pub.dev](https://pub.dev) as `deviceai_speech`

## Installation

```yaml
# pubspec.yaml
dependencies:
  deviceai_speech: ^0.1.0
```

## Contributing

Implementation PRs welcome. The plugin bridges Dart to the Kotlin SDK (`kmp/speech`) on Android
and the Swift Package (`ios/speech`) on iOS via Flutter's platform channel mechanism.
