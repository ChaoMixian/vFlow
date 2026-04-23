package com.chaomixian.vflow.ui.chat

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class ChatProvider(
    val storageValue: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean = true,
) {
    OPENAI(
        storageValue = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5.4",
    ),
    DEEPSEEK(
        storageValue = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-chat",
    ),
    ANTHROPIC(
        storageValue = "anthropic",
        displayName = "Claude",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-sonnet-4-6",
    ),
    OPENROUTER(
        storageValue = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai",
        defaultModel = "anthropic/claude-sonnet-4.6",
    ),
    OLLAMA(
        storageValue = "ollama",
        displayName = "Ollama",
        defaultBaseUrl = "http://127.0.0.1:11434",
        defaultModel = "qwen3:8b",
        requiresApiKey = false,
    );

    companion object {
        fun fromStorage(value: String?): ChatProvider {
            return when (value) {
                "custom_openai" -> OPENAI
                else -> entries.firstOrNull { it.storageValue == value } ?: OPENAI
            }
        }
    }
}

@Serializable
data class ChatProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val provider: String = ChatProvider.OPENAI.storageValue,
    val apiKey: String = "",
    val baseUrl: String = ChatProvider.OPENAI.defaultBaseUrl,
    val systemPrompt: String = ChatPresetConfig.DEFAULT_SYSTEM_PROMPT,
    val temperature: Double = 0.7,
    val useResponsesApi: Boolean = false,
    val isBuiltIn: Boolean = false,
) {
    val providerEnum: ChatProvider
        get() = ChatProvider.fromStorage(provider)
}

@Serializable
data class ChatPresetConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val providerConfigId: String = "",
    val provider: String = ChatProvider.OPENAI.storageValue,
    val model: String = ChatProvider.OPENAI.defaultModel,
    val apiKey: String = "",
    val baseUrl: String = ChatProvider.OPENAI.defaultBaseUrl,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Double = 0.7,
    val useResponsesApi: Boolean = false,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant. Keep responses concise and clear."
    }

    val providerEnum: ChatProvider
        get() = ChatProvider.fromStorage(provider)
}

@Serializable
enum class ChatMessageRole {
    USER,
    ASSISTANT,
    ERROR,
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatMessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val timestampMillis: Long,
    val tokenCount: Int? = null,
    val isPending: Boolean = false,
)

@Serializable
data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val presetId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class ChatSessionState(
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversationId: String? = null,
)
