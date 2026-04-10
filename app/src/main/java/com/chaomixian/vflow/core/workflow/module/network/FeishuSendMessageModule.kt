package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.integration.feishu.FeishuModuleConfig
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class FeishuSendMessageModule : BaseModule() {
    override val id = "vflow.feishu.send_message"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_feishu_send_message_name,
        descriptionStringRes = R.string.module_vflow_feishu_send_message_desc,
        name = "发送飞书消息",
        description = "支持以应用身份或用户身份发送飞书消息",
        iconRes = R.drawable.rounded_sms_24,
        category = "飞书",
        categoryId = "feishu"
    )

    private val authModeOptions = listOf("tenant_access_token", "user_access_token")
    private val receiveIdTypeOptions = listOf("open_id", "union_id", "user_id", "email", "chat_id")
    private val msgTypeOptions = listOf(
        "text",
        "post",
        "image",
        "file",
        "audio",
        "media",
        "sticker",
        "interactive",
        "share_chat",
        "share_user",
        "system"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "auth_mode",
            name = "发送身份",
            nameStringRes = R.string.param_vflow_feishu_send_message_auth_mode_name,
            staticType = ParameterType.ENUM,
            defaultValue = "tenant_access_token",
            options = authModeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_feishu_send_message_auth_mode_tenant,
                R.string.option_vflow_feishu_send_message_auth_mode_user
            ),
            legacyValueMap = mapOf(
                "应用身份" to "tenant_access_token",
                "用户身份" to "user_access_token"
            )
        ),
        InputDefinition(
            id = "receive_id_type",
            name = "接收者类型",
            nameStringRes = R.string.param_vflow_feishu_send_message_receive_id_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = "open_id",
            options = receiveIdTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_feishu_send_message_receive_id_type_open_id,
                R.string.option_vflow_feishu_send_message_receive_id_type_union_id,
                R.string.option_vflow_feishu_send_message_receive_id_type_user_id,
                R.string.option_vflow_feishu_send_message_receive_id_type_email,
                R.string.option_vflow_feishu_send_message_receive_id_type_chat_id
            ),
        ),
        InputDefinition(
            id = "receive_id",
            name = "接收者 ID",
            nameStringRes = R.string.param_vflow_feishu_send_message_receive_id_name,
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            hint = "用户 open_id / user_id / email 或群 chat_id",
            hintStringRes = R.string.hint_vflow_feishu_send_message_receive_id
        ),
        InputDefinition(
            id = "msg_type",
            name = "消息类型",
            nameStringRes = R.string.param_vflow_feishu_send_message_msg_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = "text",
            options = msgTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_feishu_send_message_msg_type_text,
                R.string.option_vflow_feishu_send_message_msg_type_post,
                R.string.option_vflow_feishu_send_message_msg_type_image,
                R.string.option_vflow_feishu_send_message_msg_type_file,
                R.string.option_vflow_feishu_send_message_msg_type_audio,
                R.string.option_vflow_feishu_send_message_msg_type_media,
                R.string.option_vflow_feishu_send_message_msg_type_sticker,
                R.string.option_vflow_feishu_send_message_msg_type_interactive,
                R.string.option_vflow_feishu_send_message_msg_type_share_chat,
                R.string.option_vflow_feishu_send_message_msg_type_share_user,
                R.string.option_vflow_feishu_send_message_msg_type_system
            ),
        ),
        InputDefinition(
            id = "text",
            name = "文本内容",
            nameStringRes = R.string.param_vflow_feishu_send_message_text_name,
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            visibility = InputVisibility.whenEquals("msg_type", "text"),
            hint = "发送文本消息时填写",
            hintStringRes = R.string.hint_vflow_feishu_send_message_text
        ),
        InputDefinition(
            id = "content_json",
            name = "消息内容 JSON",
            nameStringRes = R.string.param_vflow_feishu_send_message_content_json_name,
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            visibility = InputVisibility.whenNotEquals("msg_type", "text"),
            hint = "非文本消息传入 content 对应的 JSON 对象",
            hintStringRes = R.string.hint_vflow_feishu_send_message_content_json
        ),
        InputDefinition(
            id = "uuid",
            name = "UUID(可选)",
            nameStringRes = R.string.param_vflow_feishu_send_message_uuid_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            hint = "可选，用于请求去重",
            hintStringRes = R.string.hint_vflow_feishu_send_message_uuid
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_feishu_send_message_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("message_id", "消息 ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_message_id_name),
        OutputDefinition("root_id", "根消息 ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_root_id_name),
        OutputDefinition("parent_id", "父消息 ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_parent_id_name),
        OutputDefinition("thread_id", "话题 ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_thread_id_name),
        OutputDefinition("chat_id", "群聊 ID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_chat_id_name),
        OutputDefinition("msg_type", "消息类型", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_msg_type_name),
        OutputDefinition("create_time", "创建时间", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_create_time_name),
        OutputDefinition("code", "响应码", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_feishu_send_message_code_name),
        OutputDefinition("msg", "响应消息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_msg_name),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_feishu_send_message_error_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val authModePill = PillUtil.createPillFromParam(step.parameters["auth_mode"], getInputs().find { it.id == "auth_mode" }, isModuleOption = true)
        val receiveIdPill = PillUtil.createPillFromParam(step.parameters["receive_id"], getInputs().find { it.id == "receive_id" })
        val msgTypePill = PillUtil.createPillFromParam(step.parameters["msg_type"], getInputs().find { it.id == "msg_type" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, "飞书发送", authModePill, msgTypePill, "到", receiveIdPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val receiveId = step.parameters["receive_id"]?.toString()?.trim().orEmpty()
        if (receiveId.isEmpty()) {
            return ValidationResult(false, "接收者 ID 不能为空")
        }

        val msgType = step.parameters["msg_type"] as? String ?: "text"
        if (msgType == "text") {
            val text = step.parameters["text"]?.toString().orEmpty()
            if (text.isBlank()) {
                return ValidationResult(false, "文本内容不能为空")
            }
        } else {
            val contentJson = step.parameters["content_json"]?.toString().orEmpty()
            if (contentJson.isBlank()) {
                return ValidationResult(false, "消息内容 JSON 不能为空")
            }
        }
        return ValidationResult(true)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val inputsById = getInputs().associateBy { it.id }
                val timeout = context.getVariableAsLong("timeout") ?: 15L
                val rawAuthMode = context.getVariableAsString("auth_mode", "tenant_access_token")
                val authMode = inputsById["auth_mode"]?.normalizeEnumValue(rawAuthMode) ?: rawAuthMode
                val token = when (
                    val tokenResolution = if (authMode == "user_access_token") {
                        FeishuModuleConfig.resolveUserAccessToken(appContext, timeout)
                    } else {
                        FeishuModuleConfig.resolveTenantAccessToken(appContext, timeout)
                    }
                ) {
                    is FeishuModuleConfig.TokenResolution.Success -> tokenResolution.token
                    is FeishuModuleConfig.TokenResolution.Failure -> {
                        return@withContext ExecutionResult.Failure(
                            tokenResolution.title,
                            tokenResolution.message
                        )
                    }
                }

                val receiveIdType = context.getVariableAsString("receive_id_type", "open_id")
                val rawReceiveId = context.getVariableAsString("receive_id", "")
                val receiveId = VariableResolver.resolve(rawReceiveId, context).trim()
                if (receiveId.isEmpty()) {
                    return@withContext ExecutionResult.Failure("参数错误", "接收者 ID 不能为空")
                }

                val msgType = context.getVariableAsString("msg_type", "text")
                val content = buildContent(context, msgType)
                    ?: return@withContext ExecutionResult.Failure("参数错误", "消息内容格式不正确")

                val uuidRaw = context.getVariableAsString("uuid", "")
                val uuid = VariableResolver.resolve(uuidRaw, context).trim()

                val requestJson = linkedMapOf<String, Any>(
                    "receive_id" to receiveId,
                    "msg_type" to msgType,
                    "content" to content
                )
                if (uuid.isNotEmpty()) {
                    requestJson["uuid"] = uuid
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                val body = Gson().toJson(requestJson)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=$receiveIdType")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .post(body)
                    .build()

                onProgress(ProgressUpdate("正在发送飞书消息..."))
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                val responseJson = JsonParser.parseString(responseBody).asJsonObject

                val code = responseJson.get("code")?.asInt ?: -1
                val msg = responseJson.get("msg")?.asString ?: response.message
                val data = responseJson.getAsJsonObject("data")

                if (code != 0) {
                    return@withContext ExecutionResult.Failure(
                        "发送失败",
                        "错误码: $code, 消息: $msg"
                    )
                }

                onProgress(ProgressUpdate("飞书消息发送成功"))
                ExecutionResult.Success(
                    mapOf(
                        "message_id" to VString(data?.get("message_id")?.asString.orEmpty()),
                        "root_id" to VString(data?.get("root_id")?.asString.orEmpty()),
                        "parent_id" to VString(data?.get("parent_id")?.asString.orEmpty()),
                        "thread_id" to VString(data?.get("thread_id")?.asString.orEmpty()),
                        "chat_id" to VString(data?.get("chat_id")?.asString.orEmpty()),
                        "msg_type" to VString(data?.get("msg_type")?.asString ?: msgType),
                        "create_time" to VString(data?.get("create_time")?.asString.orEmpty()),
                        "code" to VNumber(code.toDouble()),
                        "msg" to VString(msg),
                        "error" to VString("")
                    )
                )
            } catch (e: IOException) {
                ExecutionResult.Failure("网络错误", e.message ?: "发送飞书消息时发生网络错误")
            } catch (e: Exception) {
                ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发送飞书消息时发生未知错误")
            }
        }
    }

    private fun buildContent(context: ExecutionContext, msgType: String): String? {
        return if (msgType == "text") {
            val rawText = context.getVariableAsString("text", "")
            val text = VariableResolver.resolve(rawText, context)
            if (text.isBlank()) {
                null
            } else {
                Gson().toJson(mapOf("text" to text))
            }
        } else {
            val rawContent = context.getVariableAsString("content_json", "")
            val resolvedContent = VariableResolver.resolve(rawContent, context)
            if (resolvedContent.isBlank()) {
                null
            } else {
                try {
                    Gson().toJson(JsonParser.parseString(resolvedContent))
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
