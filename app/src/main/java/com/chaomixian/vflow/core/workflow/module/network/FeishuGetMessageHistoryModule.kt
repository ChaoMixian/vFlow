package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.integration.feishu.FeishuEditorActions
import com.chaomixian.vflow.integration.feishu.FeishuModuleConfig
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FeishuGetMessageHistoryModule : BaseModule() {
    override val id = "vflow.feishu.get_message_history"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_feishu_get_message_history_name,
        descriptionStringRes = R.string.module_vflow_feishu_get_message_history_desc,
        name = "获取飞书会话历史",
        description = "获取飞书单聊、群聊或话题中的历史消息",
        iconRes = R.drawable.rounded_sms_24,
        category = "飞书",
        categoryId = "feishu"
    )

    private val containerIdTypeOptions = listOf("chat", "thread")
    private val sortTypeOptions = listOf("ByCreateTimeAsc", "ByCreateTimeDesc")

    override fun getEditorActions(step: ActionStep?, allSteps: List<ActionStep>?): List<EditorAction> {
        return listOf(FeishuEditorActions.openModuleConfigAction())
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "container_id_type",
            name = "容器类型",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_container_id_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = "chat",
            options = containerIdTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_feishu_get_message_history_container_id_type_chat,
                R.string.option_vflow_feishu_get_message_history_container_id_type_thread
            ),
            legacyValueMap = mapOf(
                "会话(chat)" to "chat",
                "话题(thread)" to "thread"
            )
        ),
        InputDefinition(
            id = "container_id",
            name = "容器 ID",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_container_id_name,
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            hint = "chat_id 或 thread_id",
            hintStringRes = R.string.hint_vflow_feishu_get_message_history_container_id
        ),
        InputDefinition(
            id = "start_time",
            name = "开始时间",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_start_time_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            visibility = InputVisibility.whenEquals("container_id_type", "chat"),
            hint = "秒级时间戳，仅 chat 可用",
            hintStringRes = R.string.hint_vflow_feishu_get_message_history_start_time
        ),
        InputDefinition(
            id = "end_time",
            name = "结束时间",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_end_time_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            visibility = InputVisibility.whenEquals("container_id_type", "chat"),
            hint = "秒级时间戳，仅 chat 可用",
            hintStringRes = R.string.hint_vflow_feishu_get_message_history_end_time
        ),
        InputDefinition(
            id = "sort_type",
            name = "排序方式",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_sort_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = "ByCreateTimeDesc",
            options = sortTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_feishu_get_message_history_sort_type_asc,
                R.string.option_vflow_feishu_get_message_history_sort_type_desc
            ),
            legacyValueMap = mapOf(
                "按创建时间升序" to "ByCreateTimeAsc",
                "按创建时间降序" to "ByCreateTimeDesc"
            ),
            isFolded = true
        ),
        InputDefinition(
            id = "page_size",
            name = "分页大小",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_page_size_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 20.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        ),
        InputDefinition(
            id = "page_token",
            name = "分页标记",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_page_token_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            hint = "留空表示从第一页开始",
            hintStringRes = R.string.hint_vflow_feishu_get_message_history_page_token
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_feishu_get_message_history_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "messages",
            name = "消息列表",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_messages_name
        ),
        OutputDefinition(
            id = "first_message",
            name = "第一条消息",
            typeName = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_first_message_name
        ),
        OutputDefinition(
            id = "count",
            name = "消息数量",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_count_name
        ),
        OutputDefinition(
            id = "has_more",
            name = "是否还有更多",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_has_more_name
        ),
        OutputDefinition(
            id = "page_token",
            name = "下一页标记",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_page_token_name
        ),
        OutputDefinition(
            id = "messages_json",
            name = "原始消息 JSON",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_messages_json_name
        ),
        OutputDefinition(
            id = "latest_message_text",
            name = "最新消息文本",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_latest_message_text_name
        ),
        OutputDefinition(
            id = "message_texts",
            name = "消息文本列表",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_message_texts_name
        ),
        OutputDefinition(
            id = "code",
            name = "响应码",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_code_name
        ),
        OutputDefinition(
            id = "msg",
            name = "响应消息",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_msg_name
        ),
        OutputDefinition(
            id = "error",
            name = "错误信息",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_feishu_get_message_history_error_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val containerTypePill = PillUtil.createPillFromParam(
            step.parameters["container_id_type"],
            inputs.find { it.id == "container_id_type" },
            isModuleOption = true
        )
        val containerIdPill = PillUtil.createPillFromParam(
            step.parameters["container_id"],
            inputs.find { it.id == "container_id" }
        )
        return PillUtil.buildSpannable(context, "飞书历史", containerTypePill, containerIdPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val containerId = step.parameters["container_id"]?.toString()?.trim().orEmpty()
        if (containerId.isEmpty()) {
            return ValidationResult(false, "容器 ID 不能为空")
        }

        val containerIdType = normalizeEnumStepValue(step, "container_id_type", "chat")
        if (containerIdType !in containerIdTypeOptions) {
            return ValidationResult(false, "容器类型无效")
        }

        val sortType = normalizeEnumStepValue(step, "sort_type", "ByCreateTimeDesc")
        if (sortType !in sortTypeOptions) {
            return ValidationResult(false, "排序方式无效")
        }

        val pageSize = when (val value = step.parameters["page_size"]) {
            is Number -> value.toInt()
            is String -> value.toDoubleOrNull()?.toInt()
            else -> null
        }
        if (pageSize != null && pageSize !in 1..50) {
            return ValidationResult(false, "分页大小必须在 1 到 50 之间")
        }

        return ValidationResult(true)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val timeout = resolveLongInput(context, "timeout", 15L).coerceAtLeast(1L)
                val containerIdType = resolveEnumInput(context, "container_id_type", "chat")
                val containerId = resolveStringInput(context, "container_id", "")
                val startTime = resolveStringInput(context, "start_time", "")
                val endTime = resolveStringInput(context, "end_time", "")
                val sortType = resolveEnumInput(context, "sort_type", "ByCreateTimeDesc")
                val pageToken = resolveStringInput(context, "page_token", "")
                val pageSize = resolveStringInput(context, "page_size", "20").toIntOrNull()
                    ?: return@withContext failureResult("参数错误", "分页大小必须是 1 到 50 的整数")

                validateResolvedRequest(
                    containerIdType = containerIdType,
                    containerId = containerId,
                    startTime = startTime,
                    endTime = endTime,
                    sortType = sortType,
                    pageSize = pageSize
                )?.let { errorMessage ->
                    return@withContext failureResult("参数错误", errorMessage)
                }

                val token = when (val tokenResolution = FeishuModuleConfig.resolveTenantAccessToken(appContext, timeout)) {
                    is FeishuModuleConfig.TokenResolution.Success -> tokenResolution.token
                    is FeishuModuleConfig.TokenResolution.Failure -> {
                        return@withContext failureResult(
                            tokenResolution.title,
                            tokenResolution.message
                        )
                    }
                }

                val urlBuilder = "https://open.feishu.cn/open-apis/im/v1/messages"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("container_id_type", containerIdType)
                    .addQueryParameter("container_id", containerId)
                    .addQueryParameter("sort_type", sortType)
                    .addQueryParameter("page_size", pageSize.toString())

                if (startTime.isNotEmpty()) {
                    urlBuilder.addQueryParameter("start_time", startTime)
                }
                if (endTime.isNotEmpty()) {
                    urlBuilder.addQueryParameter("end_time", endTime)
                }
                if (pageToken.isNotEmpty()) {
                    urlBuilder.addQueryParameter("page_token", pageToken)
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .get()
                    .build()

                onProgress(ProgressUpdate("正在获取飞书会话历史..."))

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    val responseJson = try {
                        JsonParser.parseString(responseBody).asJsonObject
                    } catch (_: Exception) {
                        return@withContext failureResult(
                            title = "响应格式错误",
                            message = if (responseBody.isBlank()) {
                                "飞书接口返回了空响应"
                            } else {
                                "飞书接口返回了无法解析的响应，HTTP ${response.code}"
                            }
                        )
                    }

                    val code = responseJson.get("code")?.asInt ?: -1
                    val msg = responseJson.get("msg")?.asString ?: response.message
                    val data = responseJson.getAsJsonObject("data")
                    val itemsJson = data?.getAsJsonArray("items")
                    val messagesJson = itemsJson?.toString() ?: "[]"

                    if (code != 0) {
                        return@withContext ExecutionResult.Failure(
                            errorTitle = "获取失败",
                            errorMessage = "错误码: $code, 消息: $msg",
                            partialOutputs = buildFailureOutputs(
                                code = code,
                                msg = msg,
                                error = "错误码: $code, 消息: $msg",
                                messagesJson = messagesJson
                            )
                        )
                    }

                    val messages = FeishuMessageHistoryParser.parseItems(itemsJson)
                    val messageTexts = messages.map { FeishuMessageHistoryParser.extractMessageText(it) }
                    val latestMessageText = messages
                        .maxByOrNull { message ->
                            (message["create_time"]?.toString()?.toLongOrNull()) ?: Long.MIN_VALUE
                        }
                        ?.let { FeishuMessageHistoryParser.extractMessageText(it) }
                        .orEmpty()
                    val hasMore = data?.get("has_more")?.asBoolean ?: false
                    val nextPageToken = data?.get("page_token")?.asString.orEmpty()

                    onProgress(ProgressUpdate("已获取 ${messages.size} 条飞书消息"))

                    ExecutionResult.Success(
                        mapOf(
                            "messages" to messages,
                            "first_message" to messages.firstOrNull(),
                            "count" to messages.size,
                            "has_more" to hasMore,
                            "page_token" to nextPageToken,
                            "messages_json" to messagesJson,
                            "latest_message_text" to latestMessageText,
                            "message_texts" to messageTexts,
                            "code" to code,
                            "msg" to msg,
                            "error" to ""
                        )
                    )
                }
            } catch (e: IOException) {
                failureResult("网络错误", e.message ?: "获取飞书会话历史时发生网络错误")
            } catch (e: Exception) {
                failureResult("执行失败", e.localizedMessage ?: "获取飞书会话历史时发生未知错误")
            }
        }
    }

    private fun resolveStringInput(context: ExecutionContext, key: String, defaultValue: String): String {
        val rawValue = context.getParameterRaw(key) ?: context.getVariableAsString(key, defaultValue)
        return VariableResolver.resolve(rawValue, context).trim()
    }

    private fun resolveEnumInput(context: ExecutionContext, key: String, defaultValue: String): String {
        val input = getInputs().first { it.id == key }
        val resolvedValue = resolveStringInput(context, key, defaultValue)
        return input.normalizeEnumValue(resolvedValue, defaultValue) ?: defaultValue
    }

    private fun normalizeEnumStepValue(step: ActionStep, key: String, defaultValue: String): String {
        val input = getInputs().first { it.id == key }
        val rawValue = step.parameters[key] as? String ?: defaultValue
        return input.normalizeEnumValue(rawValue, defaultValue) ?: defaultValue
    }

    private fun resolveLongInput(context: ExecutionContext, key: String, defaultValue: Long): Long {
        return resolveStringInput(context, key, defaultValue.toString()).toLongOrNull() ?: defaultValue
    }

    private fun failureResult(title: String, message: String): ExecutionResult.Failure {
        return ExecutionResult.Failure(
            errorTitle = title,
            errorMessage = message,
            partialOutputs = buildFailureOutputs(error = message)
        )
    }

    private fun buildFailureOutputs(
        code: Int = -1,
        msg: String = "",
        error: String = "",
        messagesJson: String = "[]"
    ): Map<String, Any?> {
        return mapOf(
            "messages" to emptyList<Any>(),
            "first_message" to null,
            "count" to 0,
            "has_more" to false,
            "page_token" to "",
            "messages_json" to messagesJson,
            "latest_message_text" to "",
            "message_texts" to emptyList<String>(),
            "code" to code,
            "msg" to msg,
            "error" to error
        )
    }

    internal fun validateResolvedRequest(
        containerIdType: String,
        containerId: String,
        startTime: String,
        endTime: String,
        sortType: String,
        pageSize: Int
    ): String? {
        if (containerIdType !in containerIdTypeOptions) {
            return "容器类型仅支持 chat 或 thread"
        }
        if (containerId.isBlank()) {
            return "容器 ID 不能为空"
        }
        if (containerIdType == "thread" && (startTime.isNotEmpty() || endTime.isNotEmpty())) {
            return "thread 模式暂不支持按时间范围查询，请清空开始时间和结束时间"
        }
        if (sortType !in sortTypeOptions) {
            return "排序方式仅支持 ByCreateTimeAsc 或 ByCreateTimeDesc"
        }
        if (pageSize !in 1..50) {
            return "分页大小必须在 1 到 50 之间"
        }
        return null
    }
}

internal object FeishuMessageHistoryParser {
    fun parseItems(items: JsonArray?): List<Map<String, Any?>> {
        if (items == null) {
            return emptyList()
        }
        return items.mapNotNull { element ->
            if (element.isJsonObject) {
                parseItem(element.asJsonObject)
            } else {
                null
            }
        }
    }

    fun parseItem(item: JsonObject): Map<String, Any?> {
        val message = linkedMapOf<String, Any?>()
        item.entrySet().forEach { (key, value) ->
            if (key != "body") {
                message[key] = jsonElementToAny(value)
            }
        }

        val body = parseBody(item.getAsJsonObject("body"))
        if (body.isNotEmpty()) {
            message["body"] = body
            message["content"] = body["parsed_content"]
            val text = body["text"] as? String
            if (!text.isNullOrEmpty()) {
                message["text"] = text
            }
        }

        return message
    }

    fun extractMessageText(message: Map<String, Any?>): String {
        val directText = message["text"] as? String
        if (!directText.isNullOrBlank()) {
            return directText
        }

        val body = message["body"] as? Map<*, *>
        val bodyText = body?.get("text") as? String
        if (!bodyText.isNullOrBlank()) {
            return bodyText
        }

        val parsedContent = body?.get("parsed_content")
        return extractText(parsedContent).orEmpty()
    }

    private fun parseBody(bodyObject: JsonObject?): Map<String, Any?> {
        if (bodyObject == null) {
            return emptyMap()
        }

        val bodyMap = LinkedHashMap<String, Any?>()
        bodyObject.entrySet().forEach { (key, value) ->
            bodyMap[key] = jsonElementToAny(value)
        }

        val rawContent = bodyObject.get("content")?.takeUnless { it.isJsonNull }?.asString
        if (!rawContent.isNullOrBlank()) {
            val parsedContent = parseContent(rawContent)
            bodyMap["parsed_content"] = parsedContent
            val text = extractText(parsedContent)
            if (!text.isNullOrBlank()) {
                bodyMap["text"] = text
            }
        }

        return bodyMap
    }

    private fun parseContent(rawContent: String): Any? {
        return try {
            jsonElementToAny(JsonParser.parseString(rawContent))
        } catch (_: Exception) {
            rawContent
        }
    }

    private fun extractText(parsedContent: Any?): String? {
        return when (parsedContent) {
            is String -> parsedContent
            is Map<*, *> -> parsedContent["text"] as? String
            else -> null
        }
    }

    private fun jsonElementToAny(element: JsonElement?): Any? {
        if (element == null || element.isJsonNull) {
            return null
        }

        if (element.isJsonArray) {
            return element.asJsonArray.map { child -> jsonElementToAny(child) }
        }

        if (element.isJsonObject) {
            val map = linkedMapOf<String, Any?>()
            element.asJsonObject.entrySet().forEach { (key, value) ->
                map[key] = jsonElementToAny(value)
            }
            return map
        }

        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> {
                val numberString = primitive.asString
                numberString.toLongOrNull() ?: numberString.toDoubleOrNull() ?: numberString
            }
            else -> primitive.asString
        }
    }
}
