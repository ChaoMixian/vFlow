package com.chaomixian.vflow.ui.chat

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.AnthropicLLMProvider
import ai.koog.prompt.llm.DeepSeekLLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaLLMProvider
import ai.koog.prompt.llm.OpenAILLMProvider
import ai.koog.prompt.llm.OpenRouterLLMProvider
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatCompletionResult(
    val content: String,
    val reasoningContent: String?,
    val totalTokens: Int?,
)

class KoogChatClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val openAiCompatibleHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun generateReply(
        preset: ChatPresetConfig,
        history: List<ChatMessage>,
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        if (preset.providerEnum == ChatProvider.CUSTOM_OPENAI) {
            return@withContext generateOpenAiCompatibleReply(preset, history)
        }
        val prompt = prompt(id = "chat-${System.currentTimeMillis()}") {
            system(preset.systemPrompt.ifBlank { ChatPresetConfig.DEFAULT_SYSTEM_PROMPT })
            history.forEach { message ->
                when (message.role) {
                    ChatMessageRole.USER -> user(message.content)
                    ChatMessageRole.ASSISTANT -> assistant(message.content)
                    ChatMessageRole.ERROR -> {}
                }
            }
        }
        val model = preset.toKoogModel()
        buildExecutor(preset).use { executor ->
            val response = executor.execute(prompt, model).firstOrNull()
                ?: error("模型没有返回任何内容")
            val assistant = response as? Message.Assistant
                ?: error("模型返回了不支持的响应类型：${response::class.simpleName}")
            val parsedReply = extractReasoningPayload(
                content = assistant.content.trim(),
                reasoningContent = null,
            )
            ChatCompletionResult(
                content = parsedReply.content,
                reasoningContent = parsedReply.reasoningContent,
                totalTokens = assistant.metaInfo.totalTokensCount,
            )
        }
    }

    private fun generateOpenAiCompatibleReply(
        preset: ChatPresetConfig,
        history: List<ChatMessage>,
    ): ChatCompletionResult {
        val payload = buildJsonObject {
            put("model", preset.model.trim())
            put("temperature", preset.temperature)
            putJsonArray("messages") {
                add(
                    createOpenAiCompatibleMessage(
                        role = "system",
                        content = preset.systemPrompt.ifBlank { ChatPresetConfig.DEFAULT_SYSTEM_PROMPT },
                    )
                )
                history.forEach { message ->
                    when (message.role) {
                        ChatMessageRole.USER -> add(
                            createOpenAiCompatibleMessage(
                                role = "user",
                                content = message.content,
                            )
                        )

                        ChatMessageRole.ASSISTANT -> add(
                            createOpenAiCompatibleMessage(
                                role = "assistant",
                                content = message.content,
                            )
                        )

                        ChatMessageRole.ERROR -> Unit
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(buildOpenAiCompatibleEndpoint(preset.baseUrl, preset.providerEnum.defaultBaseUrl))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .apply {
                if (preset.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${preset.apiKey}")
                }
            }
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        openAiCompatibleHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                error(parseOpenAiCompatibleError(response.code, responseBody))
            }
            if (responseBody.isBlank()) {
                error("模型返回了空响应。")
            }

            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            val assistantMessage = responseJson["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
            val parsedReply = extractOpenAiCompatibleMessage(assistantMessage)
            if (parsedReply.content.isBlank()) {
                error("模型返回了空内容。")
            }

            return ChatCompletionResult(
                content = parsedReply.content,
                reasoningContent = parsedReply.reasoningContent,
                totalTokens = responseJson["usage"]
                    ?.jsonObject
                    ?.get("total_tokens")
                    ?.jsonPrimitive
                    ?.intOrNull,
            )
        }
    }

    private fun buildExecutor(preset: ChatPresetConfig): PromptExecutor {
        val provider = preset.providerEnum
        return when (provider) {
            ChatProvider.OPENAI -> SingleLLMPromptExecutor(
                OpenAILLMClient(
                    preset.apiKey,
                    OpenAIClientSettings(baseUrl = normalizeOpenAIBaseUrl(preset.baseUrl, provider.defaultBaseUrl))
                )
            )

            ChatProvider.CUSTOM_OPENAI -> error("OpenAI compatible presets should use the compatible HTTP client.")

            ChatProvider.DEEPSEEK -> SingleLLMPromptExecutor(
                DeepSeekLLMClient(
                    preset.apiKey,
                    DeepSeekClientSettings(baseUrl = normalizeBaseUrl(preset.baseUrl, provider.defaultBaseUrl))
                )
            )

            ChatProvider.ANTHROPIC -> {
                val model = preset.toKoogModel()
                SingleLLMPromptExecutor(
                    AnthropicLLMClient(
                        preset.apiKey,
                        AnthropicClientSettings(
                            modelVersionsMap = mapOf(model to model.id),
                            baseUrl = normalizeBaseUrl(preset.baseUrl, provider.defaultBaseUrl),
                        )
                    )
                )
            }

            ChatProvider.OPENROUTER -> SingleLLMPromptExecutor(
                OpenRouterLLMClient(
                    preset.apiKey,
                    OpenRouterClientSettings(baseUrl = normalizeBaseUrl(preset.baseUrl, provider.defaultBaseUrl))
                )
            )

            ChatProvider.OLLAMA -> SingleLLMPromptExecutor(
                OllamaClient(baseUrl = normalizeBaseUrl(preset.baseUrl, provider.defaultBaseUrl))
            )
        }
    }

    private fun ChatPresetConfig.toKoogModel(): LLModel {
        return when (providerEnum) {
            ChatProvider.OPENAI -> LLModel(
                provider = OpenAILLMProvider,
                id = model.trim(),
            )

            ChatProvider.DEEPSEEK -> LLModel(
                provider = DeepSeekLLMProvider,
                id = model.trim(),
            )

            ChatProvider.ANTHROPIC -> LLModel(
                provider = AnthropicLLMProvider,
                id = model.trim(),
            )

            ChatProvider.OPENROUTER -> LLModel(
                provider = OpenRouterLLMProvider,
                id = model.trim(),
            )

            ChatProvider.OLLAMA -> LLModel(
                provider = OllamaLLMProvider,
                id = model.trim(),
            )

            ChatProvider.CUSTOM_OPENAI -> error("OpenAI compatible presets should use the compatible HTTP client.")
        }
    }

    private fun normalizeOpenAIBaseUrl(raw: String, fallback: String): String {
        val normalized = normalizeBaseUrl(raw, fallback)
        return normalized.removeSuffix("/v1")
    }

    private fun normalizeBaseUrl(raw: String, fallback: String): String {
        return raw.trim().ifBlank { fallback }.removeSuffix("/")
    }

    private fun createOpenAiCompatibleMessage(
        role: String,
        content: String,
    ): JsonObject {
        return buildJsonObject {
            put("role", role)
            put("content", content)
        }
    }

    private fun buildOpenAiCompatibleEndpoint(raw: String, fallback: String): String {
        val baseUrl = normalizeBaseUrl(raw, fallback)
        return if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }
    }

    private fun extractOpenAiCompatibleContent(content: JsonElement): String {
        return when (content) {
            is JsonArray -> content.joinToString(separator = "") { item ->
                when (item) {
                    is JsonObject -> {
                        item["text"]?.jsonPrimitive?.contentOrNull
                            ?: item["content"]?.jsonPrimitive?.contentOrNull
                            ?: ""
                    }

                    else -> item.jsonPrimitive.contentOrNull.orEmpty()
                }
            }

            is JsonObject -> content["text"]?.jsonPrimitive?.contentOrNull
                ?: content["content"]?.jsonPrimitive?.contentOrNull
                ?: ""

            else -> content.jsonPrimitive.contentOrNull.orEmpty()
        }
    }

    private fun extractOpenAiCompatibleMessage(message: JsonObject?): ParsedAssistantReply {
        if (message == null) return ParsedAssistantReply(content = "", reasoningContent = null)
        val contentParts = message["content"]?.let(::extractOpenAiCompatibleTextAndReasoning)
            ?: ParsedTextParts()
        val reasoningField = message["reasoning_content"]?.let(::extractOpenAiCompatibleContent)
            ?: message["reasoning"]?.let(::extractOpenAiCompatibleContent)
        return extractReasoningPayload(
            content = contentParts.content,
            reasoningContent = reasoningField ?: contentParts.reasoningContent,
        )
    }

    private fun extractOpenAiCompatibleTextAndReasoning(content: JsonElement): ParsedTextParts {
        return when (content) {
            is JsonArray -> {
                val contentBuilder = StringBuilder()
                val reasoningBuilder = StringBuilder()
                content.forEach { item ->
                    when (item) {
                        is JsonObject -> {
                            val type = item["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val text = item["text"]?.jsonPrimitive?.contentOrNull
                                ?: item["content"]?.jsonPrimitive?.contentOrNull
                                ?: ""
                            if (type.contains("reason", ignoreCase = true)) {
                                reasoningBuilder.append(text)
                            } else {
                                contentBuilder.append(text)
                            }
                        }

                        else -> contentBuilder.append(item.jsonPrimitive.contentOrNull.orEmpty())
                    }
                }
                ParsedTextParts(
                    content = contentBuilder.toString(),
                    reasoningContent = reasoningBuilder.toString().trim().ifBlank { null },
                )
            }

            else -> ParsedTextParts(content = extractOpenAiCompatibleContent(content))
        }
    }

    private fun extractReasoningPayload(
        content: String,
        reasoningContent: String?,
    ): ParsedAssistantReply {
        val thinkRegex = Regex("(?is)<(?:think|thinking)>(.*?)</(?:think|thinking)>")
        val tagMatches = thinkRegex.findAll(content).toList()
        val inlineReasoning = tagMatches
            .joinToString(separator = "\n\n") { it.groupValues[1].trim() }
            .trim()
            .ifBlank { null }
        val visibleContent = if (tagMatches.isEmpty()) {
            content.trim()
        } else {
            thinkRegex.replace(content, "").trim()
        }
        return ParsedAssistantReply(
            content = visibleContent.ifBlank { content.trim() },
            reasoningContent = reasoningContent?.trim()?.ifBlank { inlineReasoning } ?: inlineReasoning,
        )
    }

    private data class ParsedTextParts(
        val content: String = "",
        val reasoningContent: String? = null,
    )

    private data class ParsedAssistantReply(
        val content: String,
        val reasoningContent: String?,
    )

    private fun parseOpenAiCompatibleError(code: Int, responseBody: String): String {
        val parsedMessage = runCatching {
            json.parseToJsonElement(responseBody)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        return parsedMessage?.takeIf { it.isNotBlank() }
            ?: responseBody.trim().takeIf { it.isNotBlank() }
            ?: "请求失败，HTTP $code"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
