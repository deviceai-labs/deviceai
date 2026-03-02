# react-native/speech

> **Status: planned**

React Native module for on-device Speech-to-Text and Text-to-Speech, backed by
[`android/speech`](../../android/speech) on Android and [`ios/speech`](../../ios/speech) on iOS.

## What it will provide

- `startListening()`, `stopListening()`, `speak()` JavaScript APIs
- Event-based transcription callbacks
- Distributed via npm as `react-native-deviceai-speech`

## Installation

```sh
npm install react-native-deviceai-speech
# or
yarn add react-native-deviceai-speech
```

## Contributing

Implementation PRs welcome. The module uses React Native's New Architecture (TurboModules / JSI)
to bridge JS calls to the Kotlin SDK (`kmp/speech`) on Android and the Swift Package (`ios/speech`)
on iOS.
