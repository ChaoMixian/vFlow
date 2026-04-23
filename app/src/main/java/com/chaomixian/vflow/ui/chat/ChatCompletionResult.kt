package com.chaomixian.vflow.ui.chat

data class ChatToolCall(
    val id: String?,
    val name: String,
    val argumentsJson: String,
)

data class ChatCompletionResult(
    val content: String,
    val reasoningContent: String?,
    val totalTokens: Int?,
    val toolCalls: List<ChatToolCall> = emptyList(),
)
