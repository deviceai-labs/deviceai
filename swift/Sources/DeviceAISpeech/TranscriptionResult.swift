import Foundation

/// Detailed STT transcription result.
public struct TranscriptionResult: Sendable {
    public let text: String
    public let segments: [Segment]
    public let language: String
    public let durationMs: Int64
}

/// A single transcription segment with timestamps.
public struct Segment: Sendable {
    public let text: String
    public let startMs: Int64
    public let endMs: Int64
}
