package com.chaomixian.vflow.ui.chat

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversationId: String? = null,
    val presets: List<ChatPresetConfig> = emptyList(),
    val defaultPresetId: String? = null,
    val isSending: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatPresetRepository(application)
    private val chatClient = KoogChatClient()
    private val _uiState = MutableStateFlow(ChatUiState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var conversationCounter = 1
    private val newConversationTitlePattern = Regex("""新对话\s+(\d+)""")

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "chat_presets_json" ||
            key == "chat_provider_configs_json" ||
            key == "chat_default_preset_id"
        ) {
            reloadPresets()
        }
    }

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        repository.registerChangeListener(prefsListener)
        restoreSessionState()
        reloadPresets()
        ensureConversation()
    }

    override fun onCleared() {
        repository.unregisterChangeListener(prefsListener)
        super.onCleared()
    }

    fun newConversation() {
        val currentActive = _uiState.value.activeConversation
        if (currentActive != null && currentActive.messages.isEmpty()) {
            updateUiStateAndPersist { state ->
                state.copy(activeConversationId = currentActive.id)
            }
            return
        }
        val now = System.currentTimeMillis()
        val next = ChatConversation(
            title = "新对话 ${conversationCounter++}",
            presetId = resolveFallbackPresetId(),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        updateUiStateAndPersist { state ->
            state.copy(
                conversations = listOf(next) + state.conversations,
                activeConversationId = next.id,
            )
        }
    }

    fun selectConversation(conversationId: String) {
        updateUiStateAndPersist { state -> state.copy(activeConversationId = conversationId) }
    }

    fun selectPreset(presetId: String) {
        updateActiveConversation { conversation ->
            conversation.copy(presetId = presetId)
        }
    }

    fun sendMessage(prompt: String): Boolean {
        val content = prompt.trim()
        if (content.isBlank()) return false
        val state = _uiState.value
        val conversation = state.activeConversation ?: return false
        val preset = resolvePreset(conversation, state)
        if (preset == null) {
            _events.tryEmit("请先在设置 -> 模型配置里配置聊天模型。")
            return false
        }

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            role = ChatMessageRole.USER,
            content = content,
            timestampMillis = now,
        )
        val pendingMessage = ChatMessage(
            role = ChatMessageRole.ASSISTANT,
            content = "",
            timestampMillis = now,
            isPending = true,
        )
        val historyForRequest = conversation.messages + userMessage
        val updatedConversation = conversation.copy(
            title = deriveConversationTitle(historyForRequest),
            updatedAtMillis = now,
            messages = historyForRequest + pendingMessage,
            presetId = conversation.presetId ?: preset.id,
        )
        replaceConversation(updatedConversation, isSending = true)

        viewModelScope.launch {
            runCatching {
                chatClient.generateReply(preset, historyForRequest)
            }.onSuccess { result ->
                replacePendingMessage(
                    conversationId = updatedConversation.id,
                    pendingMessageId = pendingMessage.id,
                    replacement = ChatMessage(
                        role = ChatMessageRole.ASSISTANT,
                        content = result.content.ifBlank { "模型返回了空内容。" },
                        reasoningContent = result.reasoningContent,
                        timestampMillis = System.currentTimeMillis(),
                        tokenCount = result.totalTokens,
                    )
                )
            }.onFailure { throwable ->
                replacePendingMessage(
                    conversationId = updatedConversation.id,
                    pendingMessageId = pendingMessage.id,
                    replacement = ChatMessage(
                        role = ChatMessageRole.ERROR,
                        content = throwable.message?.trim().orEmpty().ifBlank { "请求失败，请检查当前模型配置。" },
                        timestampMillis = System.currentTimeMillis(),
                    )
                )
            }
        }

        return true
    }

    private fun reloadPresets() {
        val presets = repository.getPresets()
        val defaultPresetId = repository.getDefaultPresetId()
        _uiState.update { state ->
            state.copy(
                presets = presets,
                defaultPresetId = defaultPresetId,
                conversations = state.conversations.map { conversation ->
                    val presetExists = presets.any { it.id == conversation.presetId }
                    if (conversation.presetId == null || presetExists) {
                        conversation
                    } else {
                        conversation.copy(presetId = defaultPresetId ?: presets.firstOrNull()?.id)
                    }
                }
            )
        }
        persistSessionState()
    }

    private fun ensureConversation() {
        if (_uiState.value.conversations.isEmpty()) {
            newConversation()
        }
    }

    private fun resolveFallbackPresetId(): String? {
        val state = _uiState.value
        return state.defaultPresetId ?: state.presets.firstOrNull()?.id
    }

    private fun resolvePreset(
        conversation: ChatConversation,
        state: ChatUiState = _uiState.value,
    ): ChatPresetConfig? {
        val preferredId = conversation.presetId ?: state.defaultPresetId
        return state.presets.firstOrNull { it.id == preferredId } ?: state.presets.firstOrNull()
    }

    private fun updateActiveConversation(transform: (ChatConversation) -> ChatConversation) {
        val state = _uiState.value
        val activeId = state.activeConversationId ?: return
        val current = state.conversations.firstOrNull { it.id == activeId } ?: return
        replaceConversation(transform(current), isSending = state.isSending)
    }

    private fun replaceConversation(conversation: ChatConversation, isSending: Boolean) {
        updateUiStateAndPersist { state ->
            state.copy(
                conversations = state.conversations.reorderedWith(conversation),
                activeConversationId = conversation.id,
                isSending = isSending,
            )
        }
    }

    private fun replacePendingMessage(
        conversationId: String,
        pendingMessageId: String,
        replacement: ChatMessage,
    ) {
        updateUiStateAndPersist { state ->
            state.copy(
                conversations = state.conversations.map { conversation ->
                    if (conversation.id != conversationId) return@map conversation
                    val updatedMessages = conversation.messages.map { message ->
                        if (message.id == pendingMessageId) replacement else message
                    }
                    conversation.copy(
                        messages = updatedMessages,
                        title = deriveConversationTitle(updatedMessages),
                        updatedAtMillis = replacement.timestampMillis,
                    )
                }.sortedByDescending { it.updatedAtMillis },
                isSending = false,
            )
        }
    }

    private fun deriveConversationTitle(messages: List<ChatMessage>): String {
        val firstUserMessage = messages.firstOrNull { it.role == ChatMessageRole.USER }?.content
            ?.replace('\n', ' ')
            ?.trim()
            .orEmpty()
        if (firstUserMessage.isBlank()) {
            return String.format(Locale.getDefault(), "新对话 %d", conversationCounter)
        }
        return if (firstUserMessage.length > 18) {
            "${firstUserMessage.take(18)}..."
        } else {
            firstUserMessage
        }
    }

    private fun restoreSessionState() {
        val persisted = repository.getSessionState().sanitizeForRestore()
        if (persisted.conversations.isEmpty()) return
        _uiState.update { state ->
            val activeConversationId = persisted.activeConversationId
                ?.takeIf { id -> persisted.conversations.any { it.id == id } }
                ?: persisted.conversations.firstOrNull()?.id
            state.copy(
                conversations = persisted.conversations,
                activeConversationId = activeConversationId,
                isSending = false,
            )
        }
        conversationCounter = persisted.nextConversationCounter()
    }

    private fun updateUiStateAndPersist(transform: (ChatUiState) -> ChatUiState) {
        _uiState.update(transform)
        persistSessionState()
    }

    private fun persistSessionState() {
        val state = _uiState.value
        repository.saveSessionState(
            ChatSessionState(
                conversations = state.conversations.map { conversation ->
                    conversation.copy(
                        messages = conversation.messages.filterNot { it.isPending }
                    )
                },
                activeConversationId = state.activeConversationId,
            )
        )
    }

    private fun ChatSessionState.sanitizeForRestore(): ChatSessionState {
        val sanitizedConversations = conversations.map { conversation ->
            conversation.copy(
                messages = conversation.messages.filterNot { it.isPending }
            )
        }
        val sanitizedActiveId = activeConversationId?.takeIf { id ->
            sanitizedConversations.any { it.id == id }
        }
        return copy(
            conversations = sanitizedConversations,
            activeConversationId = sanitizedActiveId,
        )
    }

    private fun ChatSessionState.nextConversationCounter(): Int {
        val nextFromTitles = conversations.maxOfOrNull { conversation ->
            newConversationTitlePattern.find(conversation.title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        } ?: 0
        return (nextFromTitles + 1).coerceAtLeast(conversations.size + 1).coerceAtLeast(1)
    }

    private val ChatUiState.activeConversation: ChatConversation?
        get() = conversations.firstOrNull { it.id == activeConversationId }

    private fun List<ChatConversation>.reorderedWith(updated: ChatConversation): List<ChatConversation> {
        return listOf(updated) + filterNot { it.id == updated.id }
    }
}
