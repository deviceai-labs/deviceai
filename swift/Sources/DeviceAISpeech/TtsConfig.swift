/// Configuration for text-to-speech.
public struct TtsConfig: Sendable {
    public var speakerId: Int? = nil
    public var speechRate: Float = 1.0
    public var dataDir: String = ""
    public var voicesPath: String = ""

    public init(
        speakerId: Int? = nil,
        speechRate: Float = 1.0,
        dataDir: String = "",
        voicesPath: String = ""
    ) {
        self.speakerId = speakerId
        self.speechRate = speechRate
        self.dataDir = dataDir
        self.voicesPath = voicesPath
    }
}
