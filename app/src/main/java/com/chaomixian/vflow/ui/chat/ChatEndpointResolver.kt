package com.chaomixian.vflow.ui.chat

import java.net.URL

internal enum class ChatEndpointMode {
    CHAT_COMPLETIONS,
    RESPONSES,
    ANTHROPIC_MESSAGES,
}

internal object ChatEndpointResolver {
    fun resolve(
        provider: ChatProvider,
        rawBaseUrl: String,
        mode: ChatEndpointMode,
    ): String {
        val normalized = rawBaseUrl.trim()
            .ifBlank { provider.defaultBaseUrl }
            .removeSuffix("/")
        if (normalized.endsWith("#")) {
            return normalized.removeSuffix("#")
        }

        val parsed = runCatching { URL(normalized) }.getOrNull()
            ?: return normalized
        val origin = "${parsed.protocol}://${parsed.authority}"
        val path = parsed.path.removeSuffix("/")
        val suffix = when (mode) {
            ChatEndpointMode.CHAT_COMPLETIONS -> "chat/completions"
            ChatEndpointMode.RESPONSES -> "responses"
            ChatEndpointMode.ANTHROPIC_MESSAGES -> "messages"
        }
        val exactSuffixes = when (mode) {
            ChatEndpointMode.CHAT_COMPLETIONS -> listOf("/chat/completions")
            ChatEndpointMode.RESPONSES -> listOf("/responses")
            ChatEndpointMode.ANTHROPIC_MESSAGES -> listOf("/messages", "/v1/messages")
        }
        if (exactSuffixes.any { path.endsWith(it, ignoreCase = true) }) {
            return "$origin$path"
        }

        return when (mode) {
            ChatEndpointMode.ANTHROPIC_MESSAGES -> resolveAnthropic(origin, path)
            ChatEndpointMode.CHAT_COMPLETIONS,
            ChatEndpointMode.RESPONSES -> resolveOpenAiFamily(provider, origin, path, suffix)
        }
    }

    private fun resolveAnthropic(origin: String, path: String): String {
        if (path.isEmpty()) {
            return "$origin/v1/messages"
        }
        return when {
            path.endsWith("/anthropic", ignoreCase = true) -> "$origin$path/v1/messages"
            path.endsWith("/v1", ignoreCase = true) -> "$origin$path/messages"
            else -> "$origin$path/messages"
        }
    }

    private fun resolveOpenAiFamily(
        provider: ChatProvider,
        origin: String,
        path: String,
        suffix: String,
    ): String {
        val defaultRoot = when (provider) {
            ChatProvider.OPENAI -> "/v1"
            ChatProvider.DEEPSEEK -> ""
            ChatProvider.OPENROUTER -> "/api/v1"
            ChatProvider.OLLAMA -> "/v1"
            ChatProvider.ANTHROPIC -> ""
        }
        if (path.isEmpty()) {
            return if (defaultRoot.isEmpty()) {
                "$origin/$suffix"
            } else {
                "$origin$defaultRoot/$suffix"
            }
        }
        return if (defaultRoot.isNotEmpty() && path.endsWith(defaultRoot, ignoreCase = true)) {
            "$origin$path/$suffix"
        } else {
            "$origin$path/$suffix"
        }
    }
}
