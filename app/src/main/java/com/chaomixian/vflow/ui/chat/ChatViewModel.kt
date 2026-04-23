package com.chaomixian.vflow.ui.chat

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.permissions.Permission
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatPermissionRequest(
    val requestId: String,
    val permissions: List<Permission>,
    val conversationId: String,
    val messageId: String,
)

data class ChatUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversationId: String? = null,
    val presets: List<ChatPresetConfig> = emptyList(),
    val defaultPresetId: String? = null,
    val isSending: Boolean = false,
    val isAgentRunning: Boolean = false,
    val availableTools: List<ChatAgentToolDefinition> = emptyList(),
    val pendingPermissionRequest: ChatPermissionRequest? = null,
    val autoApprovalScope: ChatToolAutoApprovalScope = ChatToolAutoApprovalScope.OFF,
    val queuedPromptCount: Int = 0,
)

private data class PendingToolExecution(
    val conversationId: String,
    val messageId: String,
    val preset: ChatPresetConfig,
    val batch: ChatPreparedToolBatch,
)

private data class QueuedUserPrompt(
    val conversationId: String,
    val content: String,
    val timestampMillis: Long,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatPresetRepository(application)
    private val toolRegistry = ChatAgentToolRegistry(application)
    private val toolExecutor = ChatAgentModuleExecutor(application, toolRegistry)
    private val chatClient = ChatCompletionClient()
    private val artifactStores = mutableMapOf<String, ChatAgentArtifactStore>()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            availableTools = toolRegistry.getTools(),
            autoApprovalScope = repository.getAutoApprovalScope(),
        )
    )
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var conversationCounter = 1
    private var pendingToolExecution: PendingToolExecution? = null
    private var currentAgentJob: Job? = null
    private var currentAgentConversationId: String? = null
    private val queuedUserPrompts = mutableListOf<QueuedUserPrompt>()
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

    fun deleteConversation(conversationId: String) {
        val stateBeforeDelete = _uiState.value
        val deletedConversation = stateBeforeDelete.conversations.firstOrNull { it.id == conversationId }
        val deletesBusyConversation = currentAgentConversationId == conversationId ||
            pendingToolExecution?.conversationId == conversationId ||
            stateBeforeDelete.pendingPermissionRequest?.conversationId == conversationId ||
            (deletedConversation?.messages?.any { message ->
                message.isPending ||
                    message.toolApprovalState == ChatToolApprovalState.PENDING ||
                    message.toolApprovalState == ChatToolApprovalState.RUNNING
            } == true)

        queuedUserPrompts.removeAll { it.conversationId == conversationId }
        if (deletesBusyConversation) {
            currentAgentJob?.cancel()
            currentAgentJob = null
            currentAgentConversationId = null
            pendingToolExecution = null
        }

        updateUiStateAndPersist { state ->
            val remaining = state.conversations.filter { it.id != conversationId }
            state.copy(
                conversations = remaining,
                activeConversationId = if (state.activeConversationId == conversationId) {
                    remaining.firstOrNull()?.id
                } else {
                    state.activeConversationId
                },
                isSending = if (deletesBusyConversation) false else state.isSending,
                isAgentRunning = if (deletesBusyConversation) false else state.isAgentRunning,
                pendingPermissionRequest = state.pendingPermissionRequest
                    ?.takeUnless { it.conversationId == conversationId || deletesBusyConversation },
                queuedPromptCount = queuedUserPrompts.size,
            )
        }
        artifactStores.remove(conversationId)
        processNextQueuedPromptIfIdle()
    }

    fun selectPreset(presetId: String) {
        updateActiveConversation { conversation ->
            conversation.copy(presetId = presetId)
        }
    }

    fun setAutoApprovalScope(scope: ChatToolAutoApprovalScope) {
        repository.setAutoApprovalScope(scope)
        _uiState.update { state -> state.copy(autoApprovalScope = scope) }
    }

    fun stopAgent() {
        currentAgentJob?.cancel()
        currentAgentJob = null
        currentAgentConversationId = null
        pendingToolExecution = null
        queuedUserPrompts.clear()
        val now = System.currentTimeMillis()
        updateUiStateAndPersist { state ->
            val activeId = state.activeConversationId
            state.copy(
                isSending = false,
                isAgentRunning = false,
                pendingPermissionRequest = null,
                queuedPromptCount = 0,
                conversations = state.conversations.map { conversation ->
                    if (conversation.id != activeId) return@map conversation
                    val stoppedMessages = buildList {
                        conversation.messages.forEach { message ->
                            when {
                                message.isPending -> {
                                    add(
                                        ChatMessage(
                                            role = ChatMessageRole.ERROR,
                                            content = "已停止。",
                                            timestampMillis = now,
                                        )
                                    )
                                }

                                message.role == ChatMessageRole.ASSISTANT &&
                                    message.toolApprovalState in setOf(
                                        ChatToolApprovalState.PENDING,
                                        ChatToolApprovalState.RUNNING,
                                    ) -> {
                                    add(message.copy(toolApprovalState = ChatToolApprovalState.REJECTED))
                                    toolExecutor.buildRejectedResults(message.toolCalls)
                                        .forEachIndexed { index, result ->
                                            add(
                                                ChatMessage(
                                                    role = ChatMessageRole.TOOL,
                                                    content = result.outputText,
                                                    timestampMillis = now + index + 1,
                                                    toolResult = result,
                                                )
                                            )
                                        }
                                }

                                else -> add(message)
                            }
                        }
                    }
                    conversation.copy(
                        messages = stoppedMessages,
                        updatedAtMillis = now,
                    )
                }.sortedByDescending { it.updatedAtMillis },
            )
        }
        _events.tryEmit("已停止。")
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

        val pendingApproval = conversation.pendingToolApprovalMessage()
        if (pendingApproval != null) {
            pendingToolExecution = null
            rejectPendingToolAndStartUserMessage(
                conversation = conversation,
                pendingToolMessage = pendingApproval,
                content = content,
                preset = preset,
            )
            return true
        }

        if (state.isAgentRunning || state.isSending || pendingToolExecution != null) {
            enqueueUserPrompt(conversation.id, content)
            return true
        }

        startUserMessage(conversation, content, preset)
        return true
    }

    private fun startUserMessage(
        conversation: ChatConversation,
        content: String,
        preset: ChatPresetConfig,
    ) {
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            role = ChatMessageRole.USER,
            content = content,
            timestampMillis = now,
        )
        val historyForRequest = conversation.messages + userMessage
        val updatedConversation = conversation.copy(
            title = deriveConversationTitle(historyForRequest),
            updatedAtMillis = now,
            messages = historyForRequest,
            presetId = conversation.presetId ?: preset.id,
        )
        requestAssistantReply(
            conversation = updatedConversation,
            historyForRequest = historyForRequest,
            preset = preset,
        )
    }

    private fun rejectPendingToolAndStartUserMessage(
        conversation: ChatConversation,
        pendingToolMessage: ChatMessage,
        content: String,
        preset: ChatPresetConfig,
    ) {
        val now = System.currentTimeMillis()
        val rejectedResults = toolExecutor.buildRejectedResults(pendingToolMessage.toolCalls)
        val rejectedResultMessages = rejectedResults.mapIndexed { index, result ->
            ChatMessage(
                role = ChatMessageRole.TOOL,
                content = result.outputText,
                timestampMillis = now + index,
                toolResult = result,
            )
        }
        val messagesWithRejectedTool = buildList {
            conversation.messages.forEach { message ->
                if (message.id == pendingToolMessage.id) {
                    add(message.copy(toolApprovalState = ChatToolApprovalState.REJECTED))
                    addAll(rejectedResultMessages)
                } else {
                    add(message)
                }
            }
        }
        val userMessage = ChatMessage(
            role = ChatMessageRole.USER,
            content = content,
            timestampMillis = now + rejectedResultMessages.size + 1,
        )
        val historyForRequest = messagesWithRejectedTool + userMessage
        val updatedConversation = conversation.copy(
            title = deriveConversationTitle(historyForRequest),
            updatedAtMillis = userMessage.timestampMillis,
            messages = historyForRequest,
            presetId = conversation.presetId ?: preset.id,
        )
        requestAssistantReply(
            conversation = updatedConversation,
            historyForRequest = historyForRequest,
            preset = preset,
        )
    }

    private fun enqueueUserPrompt(conversationId: String, content: String) {
        queuedUserPrompts += QueuedUserPrompt(
            conversationId = conversationId,
            content = content,
            timestampMillis = System.currentTimeMillis(),
        )
        _uiState.update { state ->
            state.copy(queuedPromptCount = queuedUserPrompts.size)
        }
        _events.tryEmit("已加入队列。")
    }

    private fun processNextQueuedPromptIfIdle() {
        val state = _uiState.value
        if (state.isSending || pendingToolExecution != null || queuedUserPrompts.isEmpty()) return
        val nextIndex = queuedUserPrompts.indexOfFirst { queued ->
            state.conversations.any { it.id == queued.conversationId }
        }
        if (nextIndex < 0) {
            queuedUserPrompts.clear()
            _uiState.update { it.copy(queuedPromptCount = 0) }
            return
        }
        val queued = queuedUserPrompts[nextIndex]
        val conversation = state.conversations.firstOrNull { it.id == queued.conversationId } ?: return
        val pendingApproval = conversation.pendingToolApprovalMessage()
        if (state.isAgentRunning && pendingApproval == null) return
        val preset = resolvePreset(conversation, state)
        if (preset == null) {
            queuedUserPrompts.removeAt(nextIndex)
            _uiState.update { it.copy(queuedPromptCount = queuedUserPrompts.size) }
            _events.tryEmit("队列中的消息无法发送：当前会话没有可用模型预设。")
            processNextQueuedPromptIfIdle()
            return
        }

        queuedUserPrompts.removeAt(nextIndex)
        _uiState.update { it.copy(queuedPromptCount = queuedUserPrompts.size) }
        if (pendingApproval != null) {
            rejectPendingToolAndStartUserMessage(
                conversation = conversation,
                pendingToolMessage = pendingApproval,
                content = queued.content,
                preset = preset,
            )
        } else {
            startUserMessage(
                conversation = conversation,
                content = queued.content,
                preset = preset,
            )
        }
    }

    fun approveToolCalls(messageId: String) {
        val state = _uiState.value
        val conversation = state.activeConversation ?: return
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return
        if (message.role != ChatMessageRole.ASSISTANT || message.toolApprovalState != ChatToolApprovalState.PENDING) {
            return
        }
        if (state.isSending || pendingToolExecution != null) {
            return
        }
        val preset = resolvePreset(conversation, state)
        if (preset == null) {
            _events.tryEmit("当前会话没有可用的模型预设。")
            return
        }

        val artifactStore = artifactStores.getOrPut(conversation.id, ::ChatAgentArtifactStore)
        val batch = toolExecutor.prepareBatch(message.toolCalls, artifactStore)
        pendingToolExecution = PendingToolExecution(
            conversationId = conversation.id,
            messageId = message.id,
            preset = preset,
            batch = batch,
        )

        updateUiStateAndPersist { current ->
            current.copy(
                conversations = current.conversations.updateMessage(message.id) { existing ->
                    existing.copy(toolApprovalState = ChatToolApprovalState.RUNNING)
                },
                isSending = true,
                isAgentRunning = true,
                pendingPermissionRequest = batch.missingPermissions
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        ChatPermissionRequest(
                            requestId = "perm_${System.currentTimeMillis()}_${message.id}",
                            permissions = it,
                            conversationId = conversation.id,
                            messageId = message.id,
                        )
                    },
            )
        }

        if (batch.missingPermissions.isEmpty()) {
            launchPendingToolBatch()
        }
    }

    fun rejectToolCalls(messageId: String) {
        val state = _uiState.value
        val conversation = state.activeConversation ?: return
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return
        if (message.role != ChatMessageRole.ASSISTANT || message.toolApprovalState != ChatToolApprovalState.PENDING) {
            return
        }
        val preset = resolvePreset(conversation, state)
        if (preset == null) {
            _events.tryEmit("当前会话没有可用的模型预设。")
            return
        }
        val toolResults = toolExecutor.buildRejectedResults(message.toolCalls)
        pendingToolExecution = null
        appendToolResultsAndContinue(
            conversationId = conversation.id,
            messageId = message.id,
            preset = preset,
            approvalState = ChatToolApprovalState.REJECTED,
            toolResults = toolResults,
        )
    }

    fun rerunToolCalls(messageId: String) {
        val state = _uiState.value
        val conversation = state.activeConversation ?: return
        val sourceMessage = conversation.messages.firstOrNull { it.id == messageId } ?: return
        if (sourceMessage.role != ChatMessageRole.ASSISTANT || sourceMessage.toolCalls.isEmpty()) {
            return
        }
        if (sourceMessage.toolApprovalState in setOf(ChatToolApprovalState.PENDING, ChatToolApprovalState.RUNNING)) {
            return
        }
        if (state.isSending || pendingToolExecution != null) {
            _events.tryEmit("Agent 正在执行中。")
            return
        }
        val artifactStore = artifactStores.getOrPut(conversation.id, ::ChatAgentArtifactStore)
        val rerunToolCalls = sourceMessage.toolCalls.mapIndexed { index, toolCall ->
            toolCall.copy(id = "rerun_${System.currentTimeMillis()}_$index")
        }
        val batch = toolExecutor.prepareBatch(rerunToolCalls, artifactStore)
        if (batch.missingPermissions.isNotEmpty()) {
            _events.tryEmit("缺少必要权限，无法再次执行。")
            return
        }

        val now = System.currentTimeMillis()
        val rerunMessage = ChatMessage(
            role = ChatMessageRole.ASSISTANT,
            content = "",
            timestampMillis = now,
            toolCalls = rerunToolCalls,
            toolApprovalState = ChatToolApprovalState.RUNNING,
        )
        replaceConversation(
            conversation = conversation.copy(
                messages = conversation.messages + rerunMessage,
                updatedAtMillis = now,
            ),
            isSending = true,
            isAgentRunning = true,
        )

        currentAgentConversationId = conversation.id
        currentAgentJob = viewModelScope.launch {
            try {
                val toolResults = toolExecutor.executeBatch(batch, artifactStore)
                val resultMessages = toolResults.mapIndexed { index, result ->
                    ChatMessage(
                        role = ChatMessageRole.TOOL,
                        content = result.outputText,
                        timestampMillis = System.currentTimeMillis() + index,
                        toolResult = result,
                    )
                }
                updateUiStateAndPersist { currentState ->
                    currentState.copy(
                        conversations = currentState.conversations.map { conv ->
                            if (conv.id != conversation.id) return@map conv
                            conv.copy(
                                messages = conv.messages.map { msg ->
                                    if (msg.id == rerunMessage.id) msg.copy(toolApprovalState = ChatToolApprovalState.APPROVED)
                                    else msg
                                } + resultMessages,
                                updatedAtMillis = resultMessages.lastOrNull()?.timestampMillis ?: now,
                            )
                        }.sortedByDescending { it.updatedAtMillis },
                        isSending = false,
                        isAgentRunning = false,
                    )
                }
                processNextQueuedPromptIfIdle()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _events.tryEmit(
                    throwable.message?.trim().orEmpty().ifBlank { "工具执行失败。" }
                )
                updateUiStateAndPersist { currentState ->
                    currentState.copy(
                        conversations = currentState.conversations.updateMessage(rerunMessage.id) {
                            it.copy(toolApprovalState = ChatToolApprovalState.REJECTED)
                        },
                        isSending = false,
                        isAgentRunning = false,
                    )
                }
                processNextQueuedPromptIfIdle()
            } finally {
                if (currentAgentJob === coroutineContext[Job]) {
                    currentAgentJob = null
                    currentAgentConversationId = null
                }
            }
        }
    }

    fun markPermissionRequestLaunched() {
        _uiState.update { state ->
            state.copy(pendingPermissionRequest = null)
        }
    }

    fun onToolPermissionResult(granted: Boolean) {
        val pending = pendingToolExecution ?: run {
            updateUiStateAndPersist { state ->
                state.copy(
                    isSending = false,
                    isAgentRunning = false,
                    pendingPermissionRequest = null,
                )
            }
            processNextQueuedPromptIfIdle()
            return
        }
        if (granted) {
            launchPendingToolBatch()
        } else {
            pendingToolExecution = null
            val toolResults = toolExecutor.buildPermissionRequiredResults(pending.batch)
            appendToolResultsAndContinue(
                conversationId = pending.conversationId,
                messageId = pending.messageId,
                preset = pending.preset,
                approvalState = ChatToolApprovalState.APPROVED,
                toolResults = toolResults,
            )
        }
    }

    private suspend fun executePendingToolBatch() {
        val pending = pendingToolExecution ?: return
        val artifactStore = artifactStores.getOrPut(pending.conversationId, ::ChatAgentArtifactStore)
        val toolResults = toolExecutor.executeBatch(pending.batch, artifactStore)
        pendingToolExecution = null
        appendToolResultsAndContinue(
            conversationId = pending.conversationId,
            messageId = pending.messageId,
            preset = pending.preset,
            approvalState = ChatToolApprovalState.APPROVED,
            toolResults = toolResults,
        )
    }

    private fun launchPendingToolBatch() {
        currentAgentConversationId = pendingToolExecution?.conversationId
        currentAgentJob = viewModelScope.launch {
            try {
                executePendingToolBatch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val pending = pendingToolExecution
                pendingToolExecution = null
                _events.tryEmit(
                    throwable.message?.trim().orEmpty().ifBlank { "工具执行失败。" }
                )
                updateUiStateAndPersist { state ->
                    state.copy(
                        isSending = false,
                        isAgentRunning = false,
                        pendingPermissionRequest = null,
                        conversations = pending?.let { failed ->
                            state.conversations.updateMessage(failed.messageId) { message ->
                                message.copy(toolApprovalState = ChatToolApprovalState.REJECTED)
                            }
                        } ?: state.conversations,
                    )
                }
                processNextQueuedPromptIfIdle()
            } finally {
                if (currentAgentJob === coroutineContext[Job]) {
                    currentAgentJob = null
                    currentAgentConversationId = null
                }
            }
        }
    }

    private fun appendToolResultsAndContinue(
        conversationId: String,
        messageId: String,
        preset: ChatPresetConfig,
        approvalState: ChatToolApprovalState,
        toolResults: List<ChatToolResult>,
    ) {
        val conversation = _uiState.value.conversations.firstOrNull { it.id == conversationId } ?: return
        val updatedAssistantMessages = conversation.messages.map { message ->
            if (message.id == messageId) {
                message.copy(toolApprovalState = approvalState)
            } else {
                message
            }
        }
        val resultMessages = toolResults.mapIndexed { index, result ->
            ChatMessage(
                role = ChatMessageRole.TOOL,
                content = result.outputText,
                timestampMillis = System.currentTimeMillis() + index,
                toolResult = result,
            )
        }
        val historyForRequest = updatedAssistantMessages + resultMessages
        val updatedConversation = conversation.copy(
            messages = historyForRequest,
            updatedAtMillis = historyForRequest.lastOrNull()?.timestampMillis ?: System.currentTimeMillis(),
        )
        requestAssistantReply(
            conversation = updatedConversation,
            historyForRequest = historyForRequest,
            preset = preset,
        )
    }

    private fun requestAssistantReply(
        conversation: ChatConversation,
        historyForRequest: List<ChatMessage>,
        preset: ChatPresetConfig,
    ) {
        val now = System.currentTimeMillis()
        val pendingMessage = ChatMessage(
            role = ChatMessageRole.ASSISTANT,
            content = "",
            timestampMillis = now,
            isPending = true,
        )
        val updatedConversation = conversation.copy(
            title = deriveConversationTitle(historyForRequest),
            updatedAtMillis = now,
            messages = historyForRequest + pendingMessage,
            presetId = conversation.presetId ?: preset.id,
        )
        replaceConversation(
            conversation = updatedConversation,
            isSending = true,
            pendingPermissionRequest = null,
        )

        currentAgentConversationId = updatedConversation.id
        currentAgentJob = viewModelScope.launch {
            try {
                val result = chatClient.generateReply(
                    preset = preset,
                    history = historyForRequest,
                    availableTools = _uiState.value.availableTools,
                )
                val timestamp = System.currentTimeMillis()
                var shouldAutoApproveMessageId: String? = null
                val replacement = if (result.toolCalls.isNotEmpty()) {
                    val normalizedToolCalls = result.toolCalls.mapIndexed { index, toolCall ->
                        toolCall.copy(
                            id = toolCall.id ?: "call_${updatedConversation.id}_${pendingMessage.id}_$index"
                        )
                    }
                    ChatMessage(
                        role = ChatMessageRole.ASSISTANT,
                        content = result.content,
                        reasoningContent = result.reasoningContent,
                        timestampMillis = timestamp,
                        tokenCount = result.totalTokens,
                        toolCalls = normalizedToolCalls,
                        toolApprovalState = ChatToolApprovalState.PENDING,
                    ).also { message ->
                        if (shouldAutoApproveToolCalls(updatedConversation.id, normalizedToolCalls)) {
                            shouldAutoApproveMessageId = message.id
                        }
                    }
                } else {
                    ChatMessage(
                        role = ChatMessageRole.ASSISTANT,
                        content = result.content.ifBlank { "模型返回了空内容。" },
                        reasoningContent = result.reasoningContent,
                        timestampMillis = timestamp,
                        tokenCount = result.totalTokens,
                    )
                }
                replacePendingMessage(
                    conversationId = updatedConversation.id,
                    pendingMessageId = pendingMessage.id,
                    replacement = replacement,
                )
                val autoApproveMessageId = shouldAutoApproveMessageId
                if (autoApproveMessageId != null) {
                    approveToolCalls(autoApproveMessageId)
                } else {
                    processNextQueuedPromptIfIdle()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                pendingToolExecution = null
                replacePendingMessage(
                    conversationId = updatedConversation.id,
                    pendingMessageId = pendingMessage.id,
                    replacement = ChatMessage(
                        role = ChatMessageRole.ERROR,
                        content = throwable.message?.trim().orEmpty().ifBlank { "请求失败，请检查当前模型配置。" },
                        timestampMillis = System.currentTimeMillis(),
                    )
                )
                processNextQueuedPromptIfIdle()
            } finally {
                if (currentAgentJob === coroutineContext[Job]) {
                    currentAgentJob = null
                    currentAgentConversationId = null
                }
            }
        }
    }

    private fun shouldAutoApproveToolCalls(
        conversationId: String,
        toolCalls: List<ChatToolCall>,
    ): Boolean {
        val scope = _uiState.value.autoApprovalScope
        if (scope == ChatToolAutoApprovalScope.OFF || toolCalls.isEmpty()) return false
        val artifactStore = artifactStores.getOrPut(conversationId, ::ChatAgentArtifactStore)
        val batch = toolExecutor.prepareBatch(toolCalls, artifactStore)
        return scope.allows(batch.riskLevel)
    }

    fun canSaveTemporaryWorkflow(messageId: String): Boolean {
        val state = _uiState.value
        val conversation = state.activeConversation ?: return false
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return false
        return message.toolCalls.any { it.name == CHAT_TEMPORARY_WORKFLOW_TOOL_NAME }
    }

    fun saveTemporaryWorkflow(messageId: String) {
        val state = _uiState.value
        val conversation = state.activeConversation ?: return
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return
        val tempToolCall = message.toolCalls.firstOrNull { it.name == CHAT_TEMPORARY_WORKFLOW_TOOL_NAME } ?: return
        val workflow = toolExecutor.buildWorkflowForSave(tempToolCall) ?: run {
            _events.tryEmit("无法解析临时工作流。")
            return
        }
        val result = runCatching {
            WorkflowManager(getApplication()).saveWorkflow(workflow)
        }
        _events.tryEmit(
            result.fold(
                onSuccess = { "工作流「${workflow.name}」已保存。" },
                onFailure = { "保存失败：${it.message?.ifBlank { null } ?: "未知错误"}" },
            )
        )
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
        replaceConversation(
            conversation = transform(current),
            isSending = state.isSending,
            isAgentRunning = state.isAgentRunning,
        )
    }

    private fun replaceConversation(
        conversation: ChatConversation,
        isSending: Boolean,
        pendingPermissionRequest: ChatPermissionRequest? = _uiState.value.pendingPermissionRequest,
        isAgentRunning: Boolean = isSending,
    ) {
        updateUiStateAndPersist { state ->
            state.copy(
                conversations = state.conversations.reorderedWith(conversation),
                activeConversationId = conversation.id,
                isSending = isSending,
                isAgentRunning = isAgentRunning,
                pendingPermissionRequest = pendingPermissionRequest,
            )
        }
    }

    private fun replacePendingMessage(
        conversationId: String,
        pendingMessageId: String,
        replacement: ChatMessage,
    ) {
        updateUiStateAndPersist { state ->
            val agentStillRunning = replacement.role == ChatMessageRole.ASSISTANT &&
                replacement.toolApprovalState == ChatToolApprovalState.PENDING
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
                isAgentRunning = agentStillRunning,
                pendingPermissionRequest = null,
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
                isAgentRunning = false,
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

    private fun List<ChatConversation>.updateMessage(
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ): List<ChatConversation> {
        return map { conversation ->
            val updatedMessages = conversation.messages.map { message ->
                if (message.id == messageId) transform(message) else message
            }
            if (updatedMessages == conversation.messages) {
                conversation
            } else {
                conversation.copy(
                    messages = updatedMessages,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    private fun ChatConversation.pendingToolApprovalMessage(): ChatMessage? {
        return messages.firstOrNull { message ->
            message.role == ChatMessageRole.ASSISTANT &&
                message.toolApprovalState == ChatToolApprovalState.PENDING
        }
    }
}
