import Testing
@testable import DeviceAILLM

@Test func testChatConfig() {
    let config = ChatConfig()
    #expect(config.temperature == 0.7)
    #expect(config.maxTokens == 512)
}
