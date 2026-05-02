/// Configuration for speech-to-text.
public struct SttConfig: Sendable {
    public var language: String = "en"
    public var translateToEnglish: Bool = false
    public var maxThreads: Int = 4
    public var useGpu: Bool = true
    public var useVad: Bool = true
    public var singleSegment: Bool = true
    public var noContext: Bool = true

    public init(
        language: String = "en",
        translateToEnglish: Bool = false,
        maxThreads: Int = 4,
        useGpu: Bool = true,
        useVad: Bool = true,
        singleSegment: Bool = true,
        noContext: Bool = true
    ) {
        self.language = language
        self.translateToEnglish = translateToEnglish
        self.maxThreads = maxThreads
        self.useGpu = useGpu
        self.useVad = useVad
        self.singleSegment = singleSegment
        self.noContext = noContext
    }
}
