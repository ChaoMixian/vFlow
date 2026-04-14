package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
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

class BarkPushModule : BaseModule() {
    companion object {
        private const val LEVEL_ACTIVE = "active"
        private const val LEVEL_TIME_SENSITIVE = "timeSensitive"
        private const val LEVEL_PASSIVE = "passive"
        private const val LEVEL_CRITICAL = "critical"
    }

    override val id = "vflow.network.bark_push"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_bark_push_name,
        descriptionStringRes = R.string.module_vflow_network_bark_push_desc,
        name = "Bark 推送",
        description = "向 Bark App 发送通知消息",
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "网络",
        categoryId = "network"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "server_url",
            name = "服务器地址",
            nameStringRes = R.string.param_vflow_network_bark_push_server_url_name,
            staticType = ParameterType.STRING,
            defaultValue = "https://api.day.app",
            supportsRichText = true
        ),
        InputDefinition(
            id = "device_key",
            name = "设备 Key",
            nameStringRes = R.string.param_vflow_network_bark_push_device_key_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "title",
            name = "标题",
            nameStringRes = R.string.param_vflow_network_bark_push_title_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true
        ),
        InputDefinition(
            id = "body",
            name = "内容",
            nameStringRes = R.string.param_vflow_network_bark_push_body_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "subtitle",
            name = "副标题",
            nameStringRes = R.string.param_vflow_network_bark_push_subtitle_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true
        ),
        InputDefinition(
            id = "level",
            name = "通知级别",
            nameStringRes = R.string.param_vflow_network_bark_push_level_name,
            staticType = ParameterType.ENUM,
            defaultValue = LEVEL_ACTIVE,
            options = listOf(
                LEVEL_ACTIVE,
                LEVEL_TIME_SENSITIVE,
                LEVEL_PASSIVE,
                LEVEL_CRITICAL
            ),
            optionsStringRes = listOf(
                R.string.option_vflow_network_bark_push_level_active,
                R.string.option_vflow_network_bark_push_level_time_sensitive,
                R.string.option_vflow_network_bark_push_level_passive,
                R.string.option_vflow_network_bark_push_level_critical
            ),
            legacyValueMap = mapOf(
                "默认" to LEVEL_ACTIVE,
                "时效通知" to LEVEL_TIME_SENSITIVE,
                "被动通知" to LEVEL_PASSIVE,
                "重要告警" to LEVEL_CRITICAL,
                "Default" to LEVEL_ACTIVE
            ),
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "volume",
            name = "音量",
            nameStringRes = R.string.param_vflow_network_bark_push_volume_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_bark_push_volume,
            inputStyle = InputStyle.SLIDER,
            sliderConfig = InputDefinition.slider(0f, 10f, 1f)
        ),
        InputDefinition(
            id = "badge",
            name = "角标",
            nameStringRes = R.string.param_vflow_network_bark_push_badge_name,
            staticType = ParameterType.NUMBER,
            defaultValue = null,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_bark_push_badge
        ),
        InputDefinition(
            id = "icon",
            name = "图标地址",
            nameStringRes = R.string.param_vflow_network_bark_push_icon_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true
        ),
        InputDefinition(
            id = "image",
            name = "图片地址",
            nameStringRes = R.string.param_vflow_network_bark_push_image_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true
        ),
        InputDefinition(
            id = "auto_copy",
            name = "自动复制",
            nameStringRes = R.string.param_vflow_network_bark_push_auto_copy_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_bark_push_auto_copy
        ),
        InputDefinition(
            id = "copy",
            name = "复制内容",
            nameStringRes = R.string.param_vflow_network_bark_push_copy_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_bark_push_copy
        ),
        InputDefinition(
            id = "jump_url",
            name = "URL",
            nameStringRes = R.string.param_vflow_network_bark_push_url_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_bark_push_url
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_network_bark_push_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_network_bark_push_success_name),
        OutputDefinition("response_code", "响应码", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_network_bark_push_response_code_name),
        OutputDefinition("response_message", "响应消息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_bark_push_response_message_name),
        OutputDefinition("response_json", "响应 JSON", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_bark_push_response_json_name),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_bark_push_error_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val titlePill = PillUtil.createPillFromParam(step.parameters["title"], inputs.find { it.id == "title" })
        return PillUtil.buildSpannable(context, "Bark 推送", titlePill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val deviceKey = step.parameters["device_key"]?.toString()?.trim().orEmpty()
        if (deviceKey.isEmpty()) {
            return ValidationResult(false, "设备 Key 不能为空")
        }
        val body = step.parameters["body"]?.toString()?.trim().orEmpty()
        if (body.isEmpty()) {
            return ValidationResult(false, "推送内容不能为空")
        }
        val serverUrl = step.parameters["server_url"]?.toString()?.trim().orEmpty()
        if (serverUrl.isEmpty()) {
            return ValidationResult(false, "服务器地址不能为空")
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val timeout = context.getVariableAsLong("timeout") ?: 15L
                val serverUrl = VariableResolver.resolve(
                    context.getVariableAsString("server_url", "https://api.day.app"),
                    context
                ).trim().trimEnd('/')
                val deviceKey = VariableResolver.resolve(
                    context.getVariableAsString("device_key", ""),
                    context
                ).trim()
                val title = VariableResolver.resolve(context.getVariableAsString("title", ""), context).trim()
                val body = VariableResolver.resolve(context.getVariableAsString("body", ""), context).trim()
                val subtitle = VariableResolver.resolve(context.getVariableAsString("subtitle", ""), context).trim()
                val rawLevel = context.getVariableAsString("level", LEVEL_ACTIVE)
                val level = getInputs().find { it.id == "level" }?.normalizeEnumValue(rawLevel) ?: rawLevel
                val volume = VariableResolver.resolve(context.getVariableAsString("volume", ""), context).trim()
                val badge = VariableResolver.resolve(context.getVariableAsString("badge", ""), context).trim()
                val icon = VariableResolver.resolve(context.getVariableAsString("icon", ""), context).trim()
                val image = VariableResolver.resolve(context.getVariableAsString("image", ""), context).trim()
                val autoCopy = context.getVariableAsBoolean("auto_copy") ?: false
                val copy = VariableResolver.resolve(context.getVariableAsString("copy", ""), context).trim()
                val jumpUrl = VariableResolver.resolve(context.getVariableAsString("jump_url", ""), context).trim()

                if (serverUrl.isEmpty() || deviceKey.isEmpty() || body.isEmpty()) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_bark_push_param_error),
                        appContext.getString(R.string.error_vflow_network_bark_push_required_fields)
                    )
                }

                val requestUrl = "$serverUrl/$deviceKey"
                val requestBody = buildBarkPushPayload(
                    title = title,
                    body = body,
                    subtitle = subtitle,
                    level = level,
                    volume = volume,
                    badge = badge,
                    icon = icon,
                    image = image,
                    autoCopy = autoCopy,
                    copy = copy,
                    jumpUrl = jumpUrl
                )
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_bark_push_sending, requestUrl)))
                val request = Request.Builder().url(requestUrl).post(requestBody).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                val parsed = parseBarkPushResponse(responseBody)

                if (!response.isSuccessful) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_bark_push_network_error),
                        responseBody.ifBlank { response.message },
                        mapOf(
                            "success" to VBoolean(false),
                            "response_code" to VNumber(response.code.toDouble()),
                            "response_message" to VString(response.message),
                            "response_json" to VString(responseBody),
                            "error" to VString(responseBody.ifBlank { response.message })
                        )
                    )
                }

                if (parsed == null) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_bark_push_parse_error),
                        appContext.getString(R.string.error_vflow_network_bark_push_invalid_response),
                        mapOf(
                            "success" to VBoolean(false),
                            "response_code" to VNumber(response.code.toDouble()),
                            "response_message" to VString(""),
                            "response_json" to VString(responseBody),
                            "error" to VString(appContext.getString(R.string.error_vflow_network_bark_push_invalid_response))
                        )
                    )
                }

                if (parsed.code != 200) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_bark_push_failed),
                        parsed.message.ifBlank { appContext.getString(R.string.error_vflow_network_bark_push_unknown_error) },
                        mapOf(
                            "success" to VBoolean(false),
                            "response_code" to VNumber(parsed.code.toDouble()),
                            "response_message" to VString(parsed.message),
                            "response_json" to VString(responseBody),
                            "error" to VString(parsed.message)
                        )
                    )
                }

                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "response_code" to VNumber(parsed.code.toDouble()),
                        "response_message" to VString(parsed.message),
                        "response_json" to VString(responseBody),
                        "error" to VString("")
                    )
                )
            } catch (e: IOException) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_bark_push_network_error),
                    e.message ?: appContext.getString(R.string.error_vflow_network_bark_push_unknown_error)
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_bark_push_execution_failed),
                    e.localizedMessage ?: appContext.getString(R.string.error_vflow_network_bark_push_unknown_error)
                )
            }
        }
    }
}
