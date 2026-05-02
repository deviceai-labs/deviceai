/// Audio data from TTS synthesis.
public struct AudioSegment: Sendable {
    /// PCM samples normalized to [-1.0, 1.0].
    public let samples: [Float]
    /// Sample rate in Hz (typically 22050 for sherpa-onnx).
    public let sampleRate: Int
}
