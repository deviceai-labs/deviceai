import Testing
@testable import DeviceAISpeech

@Test func testSttConfig() {
    let config = SttConfig()
    #expect(config.language == "en")
    #expect(config.useVad == true)
}
