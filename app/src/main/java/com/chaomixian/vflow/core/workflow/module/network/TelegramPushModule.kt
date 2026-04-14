package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramPushModule : BaseModule() {
    companion object {
        private const val PARSE_MODE_NONE = "none"
        private const val PARSE_MODE_MARKDOWN = "markdown"
        private const val PARSE_MODE_MARKDOWN_V2 = "markdown_v2"
        private const val PARSE_MODE_HTML = "html"

        private fun toTelegramParseMode(value: String): String? {
            return when (value) {
                PARSE_MODE_MARKDOWN -> "Markdown"
                PARSE_MODE_MARKDOWN_V2 -> "MarkdownV2"
                PARSE_MODE_HTML -> "HTML"
                else -> null
            }
        }
    }

    override val id = "vflow.network.telegram_push"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_telegram_push_name,
        descriptionStringRes = R.string.module_vflow_network_telegram_push_desc,
        name = "Telegram 推送",
        description = "通过 Telegram Bot API 发送消息",
        iconRes = R.drawable.rounded_sms_24,
        category = "网络",
        categoryId = "network"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "bot_token",
            name = "Bot Token",
            nameStringRes = R.string.param_vflow_network_telegram_push_bot_token_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "chat_id",
            name = "Chat ID",
            nameStringRes = R.string.param_vflow_network_telegram_push_chat_id_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "text",
            name = "消息内容",
            nameStringRes = R.string.param_vflow_network_telegram_push_text_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "parse_mode",
            name = "解析模式",
            nameStringRes = R.string.param_vflow_network_telegram_push_parse_mode_name,
            staticType = ParameterType.ENUM,
            defaultValue = PARSE_MODE_NONE,
            options = listOf(PARSE_MODE_NONE, PARSE_MODE_MARKDOWN, PARSE_MODE_MARKDOWN_V2, PARSE_MODE_HTML),
            optionsStringRes = listOf(
                R.string.option_vflow_network_telegram_push_parse_mode_none,
                R.string.option_vflow_network_telegram_push_parse_mode_markdown,
                R.string.option_vflow_network_telegram_push_parse_mode_markdown_v2,
                R.string.option_vflow_network_telegram_push_parse_mode_html
            ),
            legacyValueMap = mapOf(
                "无" to PARSE_MODE_NONE,
                "Markdown" to PARSE_MODE_MARKDOWN,
                "Markdown V2" to PARSE_MODE_MARKDOWN_V2,
                "HTML" to PARSE_MODE_HTML
            ),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "disable_web_page_preview",
            name = "禁用网页预览",
            nameStringRes = R.string.param_vflow_network_telegram_push_disable_preview_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            isFolded = true,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "message_thread_id",
            name = "话题 ID",
            nameStringRes = R.string.param_vflow_network_telegram_push_thread_id_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_telegram_push_thread_id
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_network_telegram_push_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("ok", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_network_telegram_push_ok_name),
        OutputDefinition("message_id", "消息 ID", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_network_telegram_push_message_id_name),
        OutputDefinition("chat_id", "Chat ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_telegram_push_chat_id_name),
        OutputDefinition("description", "响应消息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_telegram_push_description_name),
        OutputDefinition("response_json", "响应 JSON", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_telegram_push_response_json_name),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_telegram_push_error_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val chatIdPill = PillUtil.createPillFromParam(step.parameters["chat_id"], inputs.find { it.id == "chat_id" })
        return PillUtil.buildSpannable(context, "Telegram 推送", chatIdPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val botToken = step.parameters["bot_token"]?.toString()?.trim().orEmpty()
        if (botToken.isEmpty()) {
            return ValidationResult(false, "Bot Token 不能为空")
        }
        val chatId = step.parameters["chat_id"]?.toString()?.trim().orEmpty()
        if (chatId.isEmpty()) {
            return ValidationResult(false, "Chat ID 不能为空")
        }
        val text = step.parameters["text"]?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return ValidationResult(false, "消息内容不能为空")
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val inputs = getInputs()
                val timeout = context.getVariableAsLong("timeout") ?: 15L
                val botToken = VariableResolver.resolve(context.getVariableAsString("bot_token", ""), context).trim()
                val chatId = VariableResolver.resolve(context.getVariableAsString("chat_id", ""), context).trim()
                val text = VariableResolver.resolve(context.getVariableAsString("text", ""), context)
                val rawParseMode = context.getVariableAsString("parse_mode", PARSE_MODE_NONE)
                val parseMode = inputs.find { it.id == "parse_mode" }?.normalizeEnumValue(rawParseMode) ?: rawParseMode
                val disablePreview = context.getVariableAsBoolean("disable_web_page_preview") ?: false
                val messageThreadId = VariableResolver.resolve(context.getVariableAsString("message_thread_id", ""), context).trim()

                if (botToken.isEmpty() || chatId.isEmpty() || text.isBlank()) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_telegram_push_param_error),
                        appContext.getString(R.string.error_vflow_network_telegram_push_required_fields)
                    )
                }

                val requestUrl = "https://api.telegram.org/bot$botToken/sendMessage"
                val payload = buildTelegramPushPayload(
                    chatId = chatId,
                    text = text,
                    parseModeApiValue = toTelegramParseMode(parseMode),
                    disableWebPreview = disablePreview,
                    messageThreadId = messageThreadId
                )

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_telegram_push_sending, chatId)))
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                val parsed = parseTelegramPushResponse(responseBody)

                if (!response.isSuccessful) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_telegram_push_network_error),
                        responseBody.ifBlank { response.message },
                        mapOf(
                            "ok" to VBoolean(false),
                            "message_id" to VNumber(0.0),
                            "chat_id" to VString(chatId),
                            "description" to VString(response.message),
                            "response_json" to VString(responseBody),
                            "error" to VString(responseBody.ifBlank { response.message })
                        )
                    )
                }

                if (parsed == null) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_telegram_push_parse_error),
                        appContext.getString(R.string.error_vflow_network_telegram_push_invalid_response),
                        mapOf(
                            "ok" to VBoolean(false),
                            "message_id" to VNumber(0.0),
                            "chat_id" to VString(chatId),
                            "description" to VString(""),
                            "response_json" to VString(responseBody),
                            "error" to VString(appContext.getString(R.string.error_vflow_network_telegram_push_invalid_response))
                        )
                    )
                }

                if (!parsed.ok) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_telegram_push_failed),
                        parsed.description.ifBlank { appContext.getString(R.string.error_vflow_network_telegram_push_unknown_error) },
                        mapOf(
                            "ok" to VBoolean(false),
                            "message_id" to VNumber((parsed.messageId ?: 0L).toDouble()),
                            "chat_id" to VString(parsed.chatId ?: chatId),
                            "description" to VString(parsed.description),
                            "response_json" to VString(responseBody),
                            "error" to VString(parsed.description)
                        )
                    )
                }

                ExecutionResult.Success(
                    mapOf(
                        "ok" to VBoolean(true),
                        "message_id" to VNumber((parsed.messageId ?: 0L).toDouble()),
                        "chat_id" to VString(parsed.chatId ?: chatId),
                        "description" to VString(parsed.description),
                        "response_json" to VString(responseBody),
                        "error" to VString("")
                    )
                )
            } catch (e: IOException) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_telegram_push_network_error),
                    e.message ?: appContext.getString(R.string.error_vflow_network_telegram_push_unknown_error)
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_telegram_push_execution_failed),
                    e.localizedMessage ?: appContext.getString(R.string.error_vflow_network_telegram_push_unknown_error)
                )
            }
        }
    }
}
