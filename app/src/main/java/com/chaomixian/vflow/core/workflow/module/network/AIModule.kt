// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/AIModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * "AI 对话" 模块。
 * 支持 OpenAI、DeepSeek 等兼容 OpenAI 接口的大模型服务。
 */
class AIModule : BaseModule() {
    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_CUSTOM = "custom"

        val PROVIDER_INPUT_DEFINITION = InputDefinition(
            "provider",
            "服务商",
            ParameterType.ENUM,
            PROVIDER_OPENAI,
            options = listOf(PROVIDER_OPENAI, PROVIDER_DEEPSEEK, PROVIDER_CUSTOM),
            nameStringRes = R.string.param_vflow_network_ai_provider,
            optionsStringRes = listOf(
                R.string.option_vflow_ai_completion_provider_openai,
                R.string.option_vflow_ai_completion_provider_deepseek,
                R.string.option_vflow_ai_completion_provider_custom
            ),
            legacyValueMap = mapOf(
                "OpenAI" to PROVIDER_OPENAI,
                "DeepSeek" to PROVIDER_DEEPSEEK,
                "自定义" to PROVIDER_CUSTOM,
                "Custom" to PROVIDER_CUSTOM
            )
        )
    }

    override val id = "vflow.ai.completion"
    override val metadata = ActionMetadata(
        name = "AI 对话",  // Fallback
        nameStringRes = R.string.module_vflow_ai_completion_name,
        description = "调用大模型 API (OpenAI/DeepSeek) 进行智能对话。",  // Fallback
        descriptionStringRes = R.string.module_vflow_ai_completion_desc,
        iconRes = R.drawable.rounded_hexagon_nodes_24,
        category = "网络",
        categoryId = "network"
    )

    override val uiProvider: ModuleUIProvider = AIModuleUIProvider()

    private val gson = Gson()

    override fun getInputs(): List<InputDefinition> = listOf(
        PROVIDER_INPUT_DEFINITION,
        InputDefinition("base_url", "Base URL", ParameterType.STRING, "https://api.openai.com/v1", nameStringRes = R.string.param_vflow_network_ai_base_url),
        InputDefinition("api_key", "API Key", ParameterType.STRING, "", nameStringRes = R.string.param_vflow_network_ai_api_key),
        InputDefinition("model", "模型", ParameterType.STRING, "gpt-3.5-turbo", nameStringRes = R.string.param_vflow_network_ai_model),
        InputDefinition("prompt", "提示词", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, nameStringRes = R.string.param_vflow_network_ai_prompt),
        InputDefinition("system_prompt", "系统提示词", ParameterType.STRING, "You are a helpful assistant.", nameStringRes = R.string.param_vflow_network_ai_system_prompt),
        InputDefinition("temperature", "随机性", ParameterType.NUMBER, 0.7, nameStringRes = R.string.param_vflow_network_ai_temperature),
        InputDefinition("show_advanced", "显示高级", ParameterType.BOOLEAN, false, isHidden = true, nameStringRes = R.string.param_vflow_network_ai_show_advanced)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "回答内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_ai_completion_result_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_ai_completion_success_name)
    )

    // 摘要显示逻辑
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val provider = PROVIDER_INPUT_DEFINITION.normalizeEnumValueOrNull(step.parameters["provider"] as? String) ?: PROVIDER_OPENAI
        val promptText = step.parameters["prompt"] as? String ?: ""
        val providerDisplay = getProviderDisplayName(context, provider)

        // 如果内容是"复杂"的（多变量或长文本），VariableResolver.isComplex 会返回 true
        if (VariableResolver.isComplex(promptText)) {
            val providerPill = PillUtil.createPillFromParam(step.parameters["provider"], PROVIDER_INPUT_DEFINITION, isModuleOption = true)
            val prefix = context.getString(R.string.summary_vflow_network_ai_prefix)
            val suffix = context.getString(R.string.summary_vflow_network_ai_middle)
            return PillUtil.buildSpannable(context, prefix, providerPill, suffix, PillUtil.richTextPreview(promptText))
        } else {
            // 如果内容简单（单变量或短文本），显示完整的摘要（包含 Prompt 药丸）
            val promptPill = PillUtil.createPillFromParam(step.parameters["prompt"], getInputs().find { it.id == "prompt" })
            val prefix = context.getString(R.string.summary_vflow_network_ai_with, providerDisplay)
            return PillUtil.buildSpannable(context, prefix, promptPill)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取并解析参数
        val baseUrl = context.getVariableAsString("base_url", "https://api.openai.com/v1")
        val apiKey = context.getVariableAsString("api_key", "")
        val model = context.getVariableAsString("model", "gpt-3.5-turbo")
        val rawPrompt = context.getVariableAsString("prompt", "")
        val prompt = VariableResolver.resolve(rawPrompt, context)
        val systemPrompt = context.getVariableAsString("system_prompt", "You are a helpful assistant.")
        // 现在 variables 是 Map<String, VObject>，使用 getVariableAsNumber 获取
        val temperature = context.getVariableAsNumber("temperature") ?: 0.7

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")
        if (prompt.isBlank()) return ExecutionResult.Failure("配置错误", "提示词不能为空")

        // 准备网络请求
        val client = applyProxyIfConfigured(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS),
            appContext
        ).build()

        // 构建 JSON Body
        // 格式: { "model": "...", "messages": [...], "temperature": ... }
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to prompt)
        )

        val payload = mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to temperature
        )

        val jsonBody = gson.toJson(payload)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        // 发送请求
        // 注意处理 URL 结尾的斜杠
        val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        onProgress(ProgressUpdate("正在思考 ($model)..."))

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseStr = response.body?.string()

                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: $responseStr"
                    return@withContext ExecutionResult.Failure("请求失败", errorMsg)
                }

                if (responseStr.isNullOrBlank()) {
                    return@withContext ExecutionResult.Failure("响应为空", "服务器返回了空内容")
                }

                // 5. 解析响应
                // 目标路径: choices[0].message.content
                val jsonObject = gson.fromJson(responseStr, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val firstChoice = choices[0].asJsonObject
                    val message = firstChoice.getAsJsonObject("message")
                    if (message != null && message.has("content")) {
                        val content = message.get("content").asString
                        ExecutionResult.Success(mapOf(
                            "result" to VString(content),
                            "success" to VBoolean(true)
                        ))
                    } else {
                        ExecutionResult.Failure("解析失败", "响应格式不符合预期: message.content 不存在")
                    }
                } else {
                    ExecutionResult.Failure("解析失败", "无法从响应中提取回答: $responseStr")
                }

            } catch (e: Exception) {
                ExecutionResult.Failure("网络异常", e.message ?: "未知错误")
            }
        }
    }

    private fun getProviderDisplayName(context: Context, provider: String): String {
        return when (provider) {
            PROVIDER_DEEPSEEK -> context.getString(R.string.option_vflow_ai_completion_provider_deepseek)
            PROVIDER_CUSTOM -> context.getString(R.string.option_vflow_ai_completion_provider_custom)
            else -> context.getString(R.string.option_vflow_ai_completion_provider_openai)
        }
    }
}
