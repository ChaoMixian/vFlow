package com.chaomixian.vflow.ui.chat

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatCompletionClient(
    private val httpClient: OkHttpClient = sharedHttpClient,
) {
    suspend fun generateReply(
        preset: ChatPresetConfig,
        history: List<ChatMessage>,
        availableTools: List<ChatAgentToolDefinition> = emptyList(),
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val request = ChatProviderRequest(
            preset = preset,
            history = history.filter { it.role != ChatMessageRole.ERROR },
            availableTools = availableTools,
        )
        val adapter = when (preset.providerEnum) {
            ChatProvider.OPENAI -> OpenAICompatibleChatAdapter(httpClient, preset.providerEnum)
            ChatProvider.DEEPSEEK -> OpenAICompatibleChatAdapter(httpClient, preset.providerEnum)
            ChatProvider.OPENROUTER -> OpenAICompatibleChatAdapter(httpClient, preset.providerEnum)
            ChatProvider.OLLAMA -> OpenAICompatibleChatAdapter(httpClient, preset.providerEnum)
            ChatProvider.ANTHROPIC -> AnthropicChatAdapter(httpClient)
        }
        adapter.complete(request)
    }

    private companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val sharedHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }

    private data class ChatProviderRequest(
        val preset: ChatPresetConfig,
        val history: List<ChatMessage>,
        val availableTools: List<ChatAgentToolDefinition>,
    )

    private interface ChatProviderAdapter {
        suspend fun complete(request: ChatProviderRequest): ChatCompletionResult
    }

    private inner class OpenAICompatibleChatAdapter(
        private val httpClient: OkHttpClient,
        private val provider: ChatProvider,
    ) : ChatProviderAdapter {

        override suspend fun complete(request: ChatProviderRequest): ChatCompletionResult {
            val mode = if (request.preset.providerEnum == ChatProvider.OPENAI && request.preset.useResponsesApi) {
                ChatEndpointMode.RESPONSES
            } else {
                ChatEndpointMode.CHAT_COMPLETIONS
            }
            val url = ChatEndpointResolver.resolve(
                provider = provider,
                rawBaseUrl = request.preset.baseUrl,
                mode = mode,
            )
            val payload = when (mode) {
                ChatEndpointMode.CHAT_COMPLETIONS -> buildChatCompletionPayload(request)
                ChatEndpointMode.RESPONSES -> buildResponsesPayload(request)
                ChatEndpointMode.ANTHROPIC_MESSAGES -> error("Unsupported OpenAI adapter mode")
            }
            val root = executeJsonRequest(
                url = url,
                payload = payload,
                headers = buildHeaders(request.preset),
            )
            return when (mode) {
                ChatEndpointMode.CHAT_COMPLETIONS -> parseChatCompletion(root)
                ChatEndpointMode.RESPONSES -> parseResponsesCompletion(root)
                ChatEndpointMode.ANTHROPIC_MESSAGES -> error("Unsupported OpenAI adapter mode")
            }
        }

        private fun buildHeaders(preset: ChatPresetConfig): Map<String, String> {
            val headers = linkedMapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            )
            if (preset.apiKey.isNotBlank() && provider != ChatProvider.OLLAMA) {
                headers["Authorization"] = "Bearer ${preset.apiKey}"
            }
            if (provider == ChatProvider.OPENROUTER) {
                headers["X-Title"] = "vFlow"
            }
            return headers
        }

        private fun buildChatCompletionPayload(request: ChatProviderRequest): JsonObject {
            return buildJsonObject {
                put("model", request.preset.model)
                put("temperature", request.preset.temperature)
                put("stream", false)
                if (request.availableTools.isNotEmpty()) {
                    put("parallel_tool_calls", false)
                    put(
                        "tools",
                        buildJsonArray {
                            buildOpenAiChatToolDefinitions(request.availableTools).forEach(::add)
                        }
                    )
                }
                put(
                    "messages",
                    buildJsonArray {
                        buildChatCompletionHistoryMessages(request).forEach(::add)
                    }
                )
            }
        }

        private fun buildResponsesPayload(request: ChatProviderRequest): JsonObject {
            return buildJsonObject {
                put("model", request.preset.model)
                put("temperature", request.preset.temperature)
                put("store", false)
                if (request.availableTools.isNotEmpty()) {
                    put("parallel_tool_calls", false)
                    put(
                        "tools",
                        buildJsonArray {
                            buildOpenAiResponsesToolDefinitions(request.availableTools).forEach(::add)
                        }
                    )
                }
                put(
                    "input",
                    buildJsonArray {
                        buildResponsesInputItems(request).forEach(::add)
                    }
                )
            }
        }

        private fun buildChatCompletionHistoryMessages(request: ChatProviderRequest): List<JsonObject> {
            val items = mutableListOf<JsonObject>()
            val systemPrompt = buildSystemPrompt(request)
            if (systemPrompt.isNotBlank()) {
                items += buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            }
            request.history.forEach { message ->
                when (message.role) {
                    ChatMessageRole.USER -> {
                        val content = message.content.trim()
                        if (content.isBlank()) return@forEach
                        items += buildJsonObject {
                            put("role", "user")
                            put("content", content)
                        }
                    }

                    ChatMessageRole.ASSISTANT -> {
                        val assistantObject = buildJsonObject {
                            put("role", "assistant")
                            if (message.content.isNotBlank()) {
                                put("content", message.content)
                            } else {
                                put("content", JsonNull)
                            }
                            if (message.toolCalls.isNotEmpty()) {
                                put(
                                    "tool_calls",
                                    buildJsonArray {
                                        message.toolCalls.forEach { toolCall ->
                                            add(
                                                buildJsonObject {
                                                    put("id", toolCall.id ?: "call_${message.id}")
                                                    put("type", "function")
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", toolCall.name)
                                                            put("arguments", toolCall.argumentsJson)
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        if (message.content.isNotBlank() || message.toolCalls.isNotEmpty()) {
                            items += assistantObject
                        }
                    }

                    ChatMessageRole.TOOL -> {
                        val toolResult = message.toolResult ?: return@forEach
                        val callId = toolResult.callId ?: return@forEach
                        items += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("content", message.content.ifBlank { toolResult.outputText })
                        }
                    }

                    ChatMessageRole.ERROR -> Unit
                }
            }
            return items
        }

        private fun buildResponsesInputItems(request: ChatProviderRequest): List<JsonObject> {
            val items = mutableListOf<JsonObject>()
            val systemPrompt = buildSystemPrompt(request)
            if (systemPrompt.isNotBlank()) {
                items += buildMessageInput(role = "system", text = systemPrompt)
            }
            request.history.forEach { message ->
                when (message.role) {
                    ChatMessageRole.USER -> {
                        val content = message.content.trim()
                        if (content.isBlank()) return@forEach
                        items += buildMessageInput(role = "user", text = content)
                    }

                    ChatMessageRole.ASSISTANT -> {
                        val content = message.content.trim()
                        if (content.isNotBlank()) {
                            items += buildMessageInput(role = "assistant", text = content)
                        }
                        message.toolCalls.forEach { toolCall ->
                            items += buildJsonObject {
                                put("type", "function_call")
                                put("call_id", toolCall.id ?: "call_${message.id}")
                                put("name", toolCall.name)
                                put("arguments", toolCall.argumentsJson)
                            }
                        }
                    }

                    ChatMessageRole.TOOL -> {
                        val toolResult = message.toolResult ?: return@forEach
                        val callId = toolResult.callId ?: return@forEach
                        items += buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", message.content.ifBlank { toolResult.outputText })
                        }
                    }

                    ChatMessageRole.ERROR -> Unit
                }
            }
            return items
        }

        private fun parseChatCompletion(root: JsonObject): ChatCompletionResult {
            val payload = unwrapPayload(root, expectedKey = "choices")
            extractServiceError(payload)?.let { throw IllegalStateException(it) }
            val choice = payload["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("模型没有返回有效的 choices。")
            val message = choice["message"]?.jsonObject
                ?: throw IllegalStateException("模型没有返回有效的 message。")
            val normalized = normalizeAssistantReply(
                content = extractTextContent(message["content"]),
                reasoningContent = firstNonBlank(
                    extractTextContent(message["reasoning_content"]),
                    extractReasoningValue(message["reasoning"]),
                    extractReasoningFromContentArray(message["content"]),
                ),
            )
            return ChatCompletionResult(
                content = normalized.content,
                reasoningContent = normalized.reasoningContent,
                totalTokens = extractTotalTokens(payload["usage"]),
                toolCalls = parseOpenAiToolCalls(message["tool_calls"]),
            )
        }

        private fun parseResponsesCompletion(root: JsonObject): ChatCompletionResult {
            val payload = unwrapPayload(root, expectedKey = "output")
            extractServiceError(payload)?.let { throw IllegalStateException(it) }
            val textChunks = mutableListOf<String>()
            val reasoningChunks = mutableListOf<String>()
            val toolCalls = mutableListOf<ChatToolCall>()

            payload["output_text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let(textChunks::add)

            payload["output"]?.jsonArray?.forEach { item ->
                val obj = item.jsonObject
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "message" -> {
                        obj["content"]?.jsonArray?.forEach { part ->
                            val partObject = part.jsonObject
                            when (partObject["type"]?.jsonPrimitive?.contentOrNull) {
                                "output_text", "text" -> partObject["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(textChunks::add)
                                "reasoning_text" -> partObject["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(reasoningChunks::add)
                            }
                        }
                    }

                    "reasoning" -> {
                        obj["summary"]?.jsonArray?.forEach { summary ->
                            summary.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotBlank() }
                                ?.let(reasoningChunks::add)
                        }
                        obj["content"]?.jsonArray?.forEach { part ->
                            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotBlank() }
                                ?.let(reasoningChunks::add)
                        }
                    }

                    "function_call" -> {
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (name.isNotBlank()) {
                            toolCalls += ChatToolCall(
                                id = obj["call_id"]?.jsonPrimitive?.contentOrNull
                                    ?: obj["id"]?.jsonPrimitive?.contentOrNull,
                                name = name,
                                argumentsJson = obj["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                            )
                        }
                    }
                }
            }

            val normalized = normalizeAssistantReply(
                content = textChunks.joinToString(separator = "").trim(),
                reasoningContent = reasoningChunks.joinToString(separator = "\n\n").trim().ifBlank { null },
            )
            return ChatCompletionResult(
                content = normalized.content,
                reasoningContent = normalized.reasoningContent,
                totalTokens = extractResponsesTotalTokens(payload["usage"]),
                toolCalls = toolCalls,
            )
        }

        private fun parseOpenAiToolCalls(element: JsonElement?): List<ChatToolCall> {
            val toolCalls = element as? JsonArray ?: return emptyList()
            return toolCalls.mapNotNull { item ->
                val obj = item.jsonObject
                val function = obj["function"]?.jsonObject ?: return@mapNotNull null
                val name = function["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.isBlank()) return@mapNotNull null
                ChatToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull,
                    name = name,
                    argumentsJson = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                )
            }
        }
    }

    private inner class AnthropicChatAdapter(
        private val httpClient: OkHttpClient,
    ) : ChatProviderAdapter {

        override suspend fun complete(request: ChatProviderRequest): ChatCompletionResult {
            val url = ChatEndpointResolver.resolve(
                provider = ChatProvider.ANTHROPIC,
                rawBaseUrl = request.preset.baseUrl,
                mode = ChatEndpointMode.ANTHROPIC_MESSAGES,
            )
            val payload = buildJsonObject {
                put("model", request.preset.model)
                put("max_tokens", 4096)
                put("temperature", request.preset.temperature)
                put("system", buildSystemPrompt(request))
                if (request.availableTools.isNotEmpty()) {
                    put(
                        "tools",
                        buildJsonArray {
                            buildAnthropicToolDefinitions(request.availableTools).forEach(::add)
                        }
                    )
                }
                put(
                    "messages",
                    buildJsonArray {
                        buildAnthropicHistoryMessages(request).forEach(::add)
                    }
                )
            }
            val root = executeJsonRequest(
                url = url,
                payload = payload,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "x-api-key" to request.preset.apiKey,
                    "anthropic-version" to "2023-06-01",
                ),
            )
            extractServiceError(root)?.let { throw IllegalStateException(it) }

            val textChunks = mutableListOf<String>()
            val reasoningChunks = mutableListOf<String>()
            val toolCalls = mutableListOf<ChatToolCall>()
            root["content"]?.jsonArray?.forEach { item ->
                val obj = item.jsonObject
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" -> obj["text"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let(textChunks::add)
                    "thinking" -> obj["thinking"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let(reasoningChunks::add)
                    "tool_use" -> {
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (name.isNotBlank()) {
                            toolCalls += ChatToolCall(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull,
                                name = name,
                                argumentsJson = obj["input"]?.toString() ?: "{}",
                            )
                        }
                    }
                }
            }

            val normalized = normalizeAssistantReply(
                content = textChunks.joinToString(separator = "").trim(),
                reasoningContent = reasoningChunks.joinToString(separator = "\n\n").trim().ifBlank { null },
            )
            return ChatCompletionResult(
                content = normalized.content,
                reasoningContent = normalized.reasoningContent,
                totalTokens = extractAnthropicTotalTokens(root["usage"]),
                toolCalls = toolCalls,
            )
        }
    }

    private fun executeJsonRequest(
        url: String,
        payload: JsonObject,
        headers: Map<String, String>,
    ): JsonObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
        headers.forEach { (name, value) ->
            if (value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val rawBody = response.body?.string().orEmpty().trim()
            val root = parseJsonObject(rawBody)
            val errorMessage = root?.let(::extractServiceError)
            if (!response.isSuccessful) {
                val detail = errorMessage ?: rawBody.ifBlank { "HTTP ${response.code}" }
                throw IllegalStateException("Status code: ${response.code}\nError body:\n$detail")
            }
            if (root == null) {
                throw IllegalStateException("服务返回了无法解析的 JSON 响应。")
            }
            errorMessage?.let { throw IllegalStateException(it) }
            return root
        }
    }

    private fun parseJsonObject(rawBody: String): JsonObject? {
        if (rawBody.isBlank()) return null
        val element = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() ?: return null
        return element as? JsonObject
    }

    private fun unwrapPayload(root: JsonObject, expectedKey: String): JsonObject {
        if (root.containsKey(expectedKey)) return root
        val nested = root["data"] as? JsonObject
        if (nested != null && nested.containsKey(expectedKey)) {
            return nested
        }
        return root
    }

    private fun extractServiceError(root: JsonObject): String? {
        val error = root["error"]
        if (error != null && error !is JsonNull) {
            return when (error) {
                is JsonObject -> {
                    val message = error["message"]?.jsonPrimitive?.contentOrNull
                        ?: error["msg"]?.jsonPrimitive?.contentOrNull
                        ?: error["detail"]?.jsonPrimitive?.contentOrNull
                    val type = error["type"]?.jsonPrimitive?.contentOrNull
                    if (message.isNullOrBlank()) {
                        type
                    } else if (type.isNullOrBlank()) {
                        message
                    } else {
                        "$message [$type]"
                    }
                }

                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
        }
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        val topLevelMessage = root["message"]?.jsonPrimitive?.contentOrNull
            ?: root["msg"]?.jsonPrimitive?.contentOrNull
            ?: root["detail"]?.jsonPrimitive?.contentOrNull
        return if (type == "error" && !topLevelMessage.isNullOrBlank()) {
            topLevelMessage
        } else {
            topLevelMessage
        }
    }

    private fun extractTextContent(element: JsonElement?): String {
        return when (element) {
            null, JsonNull -> ""
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> element.joinToString(separator = "") { part ->
                val obj = part as? JsonObject ?: return@joinToString ""
                obj["text"]?.jsonPrimitive?.contentOrNull
                    ?: obj["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
            }
            is JsonObject -> {
                element["text"]?.jsonPrimitive?.contentOrNull
                    ?: element["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
            }
            else -> ""
        }
    }

    private fun extractReasoningFromContentArray(element: JsonElement?): String? {
        val array = element as? JsonArray ?: return null
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "reasoning_text") {
                obj["text"]?.jsonPrimitive?.contentOrNull
            } else {
                null
            }
        }.joinToString(separator = "\n\n").trim().ifBlank { null }
    }

    private fun extractReasoningValue(element: JsonElement?): String? {
        return when (element) {
            null, JsonNull -> null
            is JsonPrimitive -> element.contentOrNull?.trim()?.ifBlank { null }
            is JsonArray -> element.joinToString(separator = "\n\n") { extractTextContent(it) }
                .trim()
                .ifBlank { null }
            is JsonObject -> {
                val summary = (element["summary"] as? JsonArray)
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString(separator = "\n\n")
                    ?.trim()
                    .orEmpty()
                val content = extractTextContent(element["content"])
                firstNonBlank(summary, content)
            }
            else -> null
        }
    }

    private fun extractTotalTokens(element: JsonElement?): Int? {
        val usage = element as? JsonObject ?: return null
        return usage["total_tokens"]?.jsonPrimitive?.intOrNull
            ?: listOf(
                usage["prompt_tokens"]?.jsonPrimitive?.intOrNull,
                usage["completion_tokens"]?.jsonPrimitive?.intOrNull,
            ).filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }

    private fun extractResponsesTotalTokens(element: JsonElement?): Int? {
        val usage = element as? JsonObject ?: return null
        return usage["total_tokens"]?.jsonPrimitive?.intOrNull
            ?: listOf(
                usage["input_tokens"]?.jsonPrimitive?.intOrNull,
                usage["output_tokens"]?.jsonPrimitive?.intOrNull,
                usage["prompt_tokens"]?.jsonPrimitive?.intOrNull,
                usage["completion_tokens"]?.jsonPrimitive?.intOrNull,
            ).filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }

    private fun extractAnthropicTotalTokens(element: JsonElement?): Int? {
        val usage = element as? JsonObject ?: return null
        return listOf(
            usage["input_tokens"]?.jsonPrimitive?.intOrNull,
            usage["output_tokens"]?.jsonPrimitive?.intOrNull,
        ).filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun buildSystemPrompt(request: ChatProviderRequest): String {
        val basePrompt = request.preset.systemPrompt.trim()
        if (request.availableTools.isEmpty()) return basePrompt
        val toolAppendix = """
            You are the vFlow chat agent inside an Android automation app.
            Use the provided vFlow tools whenever device observation or device action is required.
            Prefer the shortest safe plan: if the user asks for a clear one-step device action, call the matching single-purpose tool directly.
            If the user asks for a deterministic multi-step or repeated device action, generate one complete temporary vFlow workflow and execute the whole sequence in one approval instead of spreading it across multiple assistant turns.
            The temporary workflow tool expects a workflow object with real ActionStep entries: each step must use moduleId and parameters with canonical values, not localized labels.
            Use stable, descriptive snake_case step ids such as find_search_box or tap_confirm; later magic-variable references depend on these ids.
            Only emit parameters that are present in the selected tool or module schema. Do not invent parameter names, localized parameter keys, or output ids.
            If the user asks to create, generate, or save a reusable automation, call vflow_agent_save_workflow instead of saying you cannot save workflows.
            Saved workflows may use trigger modules in workflow.triggers; temporary workflows must never include trigger modules.
            For saved workflows, include trigger modules only when the user asks for a schedule, event, or automatic condition. If no trigger is requested, omit workflow.triggers and let the app add a manual trigger.
            Trigger modules define when a saved workflow runs and are not useful for one-off temporary execution.
            For repeated actions, prefer vflow.logic.loop.start plus vflow.logic.loop.end over emitting many repeated tool calls. Loop output indices are 1-based.
            Every block start module must be closed by its matching block end module.
            Input-text tools type into the currently focused field. If focus is uncertain, first locate and click the target field using accessibility or coordinates.
            Do not open quick settings, take screenshots, run OCR, or search UI elements when a direct tool can complete the request.
            Shell tools are high risk. Use them only when no safer direct vFlow module can complete the task.
            Use read-only observation tools only when the target screen, current state, or required coordinates are actually unknown.
            Ask one concise clarification only when a missing target app, screen, time, account, or condition would make the action ambiguous or risky; otherwise act with the available tools.
            When you decide to act, include a brief natural-language explanation for the user, then call the tool.
            Never claim a tool succeeded until you receive a tool result.
            Tool results may contain reusable artifact handles like artifact://... . Pass those handles back into later tool arguments when appropriate.
            If a tool is rejected or a required Android permission is unavailable, adapt the plan instead of assuming the action happened.
        """.trimIndent()
        return listOf(basePrompt, toolAppendix)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun buildMessageInput(role: String, text: String): JsonObject {
        return buildJsonObject {
            put("type", "message")
            put("role", role)
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "input_text")
                            put("text", text)
                        }
                    )
                }
            )
        }
    }

    private fun buildOpenAiChatToolDefinitions(
        tools: List<ChatAgentToolDefinition>,
    ): List<JsonObject> {
        return tools.map { tool ->
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.inputSchema)
                    }
                )
            }
        }
    }

    private fun buildOpenAiResponsesToolDefinitions(
        tools: List<ChatAgentToolDefinition>,
    ): List<JsonObject> {
        return tools.map { tool ->
            buildJsonObject {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.inputSchema)
            }
        }
    }

    private fun buildAnthropicToolDefinitions(
        tools: List<ChatAgentToolDefinition>,
    ): List<JsonObject> {
        return tools.map { tool ->
            buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("input_schema", tool.inputSchema)
                put("strict", true)
            }
        }
    }

    private fun buildAnthropicHistoryMessages(request: ChatProviderRequest): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        request.history.forEach { message ->
            when (message.role) {
                ChatMessageRole.USER -> {
                    val content = message.content.trim()
                    if (content.isBlank()) return@forEach
                    items += buildJsonObject {
                        put("role", "user")
                        put("content", content)
                    }
                }

                ChatMessageRole.ASSISTANT -> {
                    val contentBlocks = buildJsonArray {
                        message.content.trim().takeIf { it.isNotBlank() }?.let { content ->
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", content)
                                }
                            )
                        }
                        message.toolCalls.forEach { toolCall ->
                            add(
                                buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", toolCall.id ?: "call_${message.id}")
                                    put("name", toolCall.name)
                                    put(
                                        "input",
                                        runCatching { json.parseToJsonElement(toolCall.argumentsJson) }
                                            .getOrNull() ?: buildJsonObject { }
                                    )
                                }
                            )
                        }
                    }
                    if (contentBlocks.isNotEmpty()) {
                        items += buildJsonObject {
                            put("role", "assistant")
                            put("content", contentBlocks)
                        }
                    }
                }

                ChatMessageRole.TOOL -> {
                    val toolResult = message.toolResult ?: return@forEach
                    val callId = toolResult.callId ?: return@forEach
                    items += buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "tool_result")
                                        put("tool_use_id", callId)
                                        put("content", message.content.ifBlank { toolResult.outputText })
                                        put("is_error", toolResult.status != ChatToolResultStatus.SUCCESS)
                                    }
                                )
                            }
                        )
                    }
                }

                ChatMessageRole.ERROR -> Unit
            }
        }
        return items
    }

    private fun normalizeAssistantReply(
        content: String,
        reasoningContent: String?,
    ): ChatCompletionResult {
        val thinkRegex = Regex("(?is)<(?:think|thinking)>(.*?)</(?:think|thinking)>")
        val matches = thinkRegex.findAll(content).toList()
        val inlineReasoning = matches.joinToString(separator = "\n\n") { it.groupValues[1].trim() }
            .trim()
            .ifBlank { null }
        val visibleContent = if (matches.isEmpty()) {
            content.trim()
        } else {
            thinkRegex.replace(content, "").trim()
        }
        val mergedReasoning = firstNonBlank(reasoningContent, inlineReasoning)
        val normalizedContent = when {
            visibleContent.isNotBlank() -> visibleContent
            mergedReasoning != null -> ""
            else -> content.trim()
        }
        return ChatCompletionResult(
            content = normalizedContent,
            reasoningContent = mergedReasoning,
            totalTokens = null,
        )
    }
}
