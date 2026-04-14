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

class DiscordPushModule : BaseModule() {
    override val id = "vflow.network.discord_push"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_discord_push_name,
        descriptionStringRes = R.string.module_vflow_network_discord_push_desc,
        name = "Discord 推送",
        description = "通过 Discord Webhook 发送消息",
        iconRes = R.drawable.rounded_sms_24,
        category = "网络",
        categoryId = "network"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "webhook_url",
            name = "Webhook 地址",
            nameStringRes = R.string.param_vflow_network_discord_push_webhook_url_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "content",
            name = "消息内容",
            nameStringRes = R.string.param_vflow_network_discord_push_content_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "mention_user_ids",
            name = "提及用户 ID",
            nameStringRes = R.string.param_vflow_network_discord_push_mention_user_ids_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_discord_push_mention_user_ids
        ),
        InputDefinition(
            id = "username",
            name = "显示名称",
            nameStringRes = R.string.param_vflow_network_discord_push_username_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true
        ),
        InputDefinition(
            id = "avatar_url",
            name = "头像地址",
            nameStringRes = R.string.param_vflow_network_discord_push_avatar_url_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true
        ),
        InputDefinition(
            id = "tts",
            name = "TTS 朗读",
            nameStringRes = R.string.param_vflow_network_discord_push_tts_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_network_discord_push_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_network_discord_push_success_name),
        OutputDefinition("status_code", "状态码", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_network_discord_push_status_code_name),
        OutputDefinition("response_body", "响应内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_discord_push_response_body_name),
        OutputDefinition("final_content", "最终内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_discord_push_final_content_name),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_discord_push_error_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val contentPill = PillUtil.createPillFromParam(step.parameters["content"], inputs.find { it.id == "content" })
        return PillUtil.buildSpannable(context, "Discord 推送", contentPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val webhookUrl = step.parameters["webhook_url"]?.toString()?.trim().orEmpty()
        if (webhookUrl.isEmpty()) {
            return ValidationResult(false, "Webhook 地址不能为空")
        }
        val content = step.parameters["content"]?.toString()?.trim().orEmpty()
        if (content.isEmpty()) {
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
                val timeout = context.getVariableAsLong("timeout") ?: 15L
                val webhookUrl = VariableResolver.resolve(context.getVariableAsString("webhook_url", ""), context).trim()
                val content = VariableResolver.resolve(context.getVariableAsString("content", ""), context).trim()
                val mentionUserIds = VariableResolver.resolve(context.getVariableAsString("mention_user_ids", ""), context)
                val username = VariableResolver.resolve(context.getVariableAsString("username", ""), context).trim()
                val avatarUrl = VariableResolver.resolve(context.getVariableAsString("avatar_url", ""), context).trim()
                val tts = context.getVariableAsBoolean("tts") ?: false

                if (webhookUrl.isEmpty() || content.isBlank()) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_discord_push_param_error),
                        appContext.getString(R.string.error_vflow_network_discord_push_required_fields)
                    )
                }

                val finalContent = buildDiscordContent(content, mentionUserIds)
                val payload = buildDiscordPushPayload(
                    content = finalContent,
                    username = username,
                    avatarUrl = avatarUrl,
                    tts = tts
                )

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_discord_push_sending, webhookUrl)))
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_discord_push_failed),
                        responseBody.ifBlank { response.message },
                        mapOf(
                            "success" to VBoolean(false),
                            "status_code" to VNumber(response.code.toDouble()),
                            "response_body" to VString(responseBody),
                            "final_content" to VString(finalContent),
                            "error" to VString(responseBody.ifBlank { response.message })
                        )
                    )
                }

                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "status_code" to VNumber(response.code.toDouble()),
                        "response_body" to VString(responseBody),
                        "final_content" to VString(finalContent),
                        "error" to VString("")
                    )
                )
            } catch (e: IOException) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_discord_push_network_error),
                    e.message ?: appContext.getString(R.string.error_vflow_network_discord_push_unknown_error)
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_discord_push_execution_failed),
                    e.localizedMessage ?: appContext.getString(R.string.error_vflow_network_discord_push_unknown_error)
                )
            }
        }
    }
}
