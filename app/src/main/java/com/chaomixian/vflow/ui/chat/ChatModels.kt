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
    TOOL,
    ERROR,
}

@Serializable
enum class ChatToolApprovalState {
    PENDING,
    RUNNING,
    APPROVED,
    REJECTED,
}

@Serializable
enum class ChatToolResultStatus {
    SUCCESS,
    ERROR,
    REJECTED,
    PERMISSION_REQUIRED,
}

enum class ChatAgentToolRiskLevel(val rank: Int) {
    READ_ONLY(0),
    LOW(1),
    STANDARD(2),
    HIGH(3);

    companion object {
        fun maxOf(levels: Iterable<ChatAgentToolRiskLevel>): ChatAgentToolRiskLevel {
            return levels.maxByOrNull { it.rank } ?: READ_ONLY
        }
    }
}

enum class ChatAgentToolUsageScope(val label: String) {
    DIRECT_TOOL("direct tool call"),
    TEMPORARY_WORKFLOW("temporary workflow step"),
    SAVED_WORKFLOW("saved workflow step"),
}

enum class ChatToolAutoApprovalScope(
    val storageValue: String,
    val maxRiskLevel: ChatAgentToolRiskLevel?,
) {
    OFF("off", null),
    READ_ONLY("read_only", ChatAgentToolRiskLevel.READ_ONLY),
    LOW_RISK("low_risk", ChatAgentToolRiskLevel.LOW),
    STANDARD("standard", ChatAgentToolRiskLevel.STANDARD),
    ALL("all", ChatAgentToolRiskLevel.HIGH);

    fun allows(level: ChatAgentToolRiskLevel): Boolean {
        return maxRiskLevel?.let { level.rank <= it.rank } == true
    }

    fun next(): ChatToolAutoApprovalScope {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromStorage(value: String?): ChatToolAutoApprovalScope {
            return entries.firstOrNull { it.storageValue == value } ?: OFF
        }
    }
}

@Serializable
data class ChatToolCall(
    val id: String? = null,
    val name: String,
    val argumentsJson: String,
)

@Serializable
data class ChatArtifactReference(
    val key: String,
    val handle: String,
    val typeLabel: String,
)

@Serializable
data class ChatToolResult(
    val callId: String? = null,
    val name: String,
    val status: ChatToolResultStatus,
    val summary: String,
    val outputText: String,
    val artifacts: List<ChatArtifactReference> = emptyList(),
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatMessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val timestampMillis: Long,
    val tokenCount: Int? = null,
    val isPending: Boolean = false,
    val toolCalls: List<ChatToolCall> = emptyList(),
    val toolApprovalState: ChatToolApprovalState? = null,
    val toolResult: ChatToolResult? = null,
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
