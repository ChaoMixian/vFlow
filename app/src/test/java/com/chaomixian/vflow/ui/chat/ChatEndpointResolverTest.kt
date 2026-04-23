package com.chaomixian.vflow.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatEndpointResolverTest {

    @Test
    fun resolvesOpenAiDefaultBaseUrlToChatCompletions() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ChatEndpointResolver.resolve(
                provider = ChatProvider.OPENAI,
                rawBaseUrl = "https://api.openai.com/v1",
                mode = ChatEndpointMode.CHAT_COMPLETIONS,
            )
        )
    }

    @Test
    fun resolvesOpenAiCompatiblePathWithoutInjectingV1() {
        assertEquals(
            "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions",
            ChatEndpointResolver.resolve(
                provider = ChatProvider.OPENAI,
                rawBaseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
                mode = ChatEndpointMode.CHAT_COMPLETIONS,
            )
        )
    }

    @Test
    fun preservesExplicitEndpointWhenHashSuffixIsUsed() {
        assertEquals(
            "https://example.com/custom/chat/completions",
            ChatEndpointResolver.resolve(
                provider = ChatProvider.OPENAI,
                rawBaseUrl = "https://example.com/custom/chat/completions#",
                mode = ChatEndpointMode.CHAT_COMPLETIONS,
            )
        )
    }

    @Test
    fun resolvesOpenRouterToApiV1ChatCompletions() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ChatEndpointResolver.resolve(
                provider = ChatProvider.OPENROUTER,
                rawBaseUrl = "https://openrouter.ai",
                mode = ChatEndpointMode.CHAT_COMPLETIONS,
            )
        )
    }

    @Test
    fun resolvesAnthropicDefaultBaseUrlToMessagesEndpoint() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            ChatEndpointResolver.resolve(
                provider = ChatProvider.ANTHROPIC,
                rawBaseUrl = "https://api.anthropic.com",
                mode = ChatEndpointMode.ANTHROPIC_MESSAGES,
            )
        )
    }
}
