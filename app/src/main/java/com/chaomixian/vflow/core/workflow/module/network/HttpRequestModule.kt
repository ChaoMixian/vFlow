// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * HTTP 请求模块。
 * 允许用户发送 GET, POST 等网络请求，并处理响应。
 */
class HttpRequestModule : BaseModule() {
    override val id = "vflow.network.http_request"
    override val metadata = ActionMetadata(
        name = "HTTP 请求",
        description = "发送 HTTP 请求并获取响应。",
        iconRes = R.drawable.rounded_public_24,
        category = "网络"
    )

    // 为该模块提供自定义的编辑器UI
    override val uiProvider: ModuleUIProvider? = HttpRequestModuleUIProvider()

    // 定义支持的HTTP方法和请求体类型
    val methodOptions = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    val bodyTypeOptions = listOf("无", "JSON", "表单", "原始文本")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("url", "URL", ParameterType.STRING, acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("method", "方法", ParameterType.ENUM, "GET", options = methodOptions, acceptsMagicVariable = false),
        InputDefinition("headers", "请求头", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true),
        InputDefinition("query_params", "查询参数", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true),
        InputDefinition("body_type", "请求体类型", ParameterType.ENUM, "无", options = bodyTypeOptions, acceptsMagicVariable = false),
        InputDefinition("body", "请求体", ParameterType.ANY, acceptsMagicVariable = true, supportsRichText = true), // 原始文本支持富文本
        InputDefinition("timeout", "超时(秒)", ParameterType.NUMBER, 10.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("response_body", "响应内容", TextVariable.TYPE_NAME),
        OutputDefinition("status_code", "状态码", NumberVariable.TYPE_NAME),
        OutputDefinition("response_headers", "响应头", DictionaryVariable.TYPE_NAME),
        OutputDefinition("error", "错误信息", TextVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val method = step.parameters["method"] as? String ?: "GET"
        val urlPill = PillUtil.createPillFromParam(step.parameters["url"], getInputs().find { it.id == "url" })
        return PillUtil.buildSpannable(context, method, " ", urlPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                // [重构] 使用 VariableResolver 解析 URL
                val rawUrl = context.variables["url"]?.toString() ?: ""
                var urlString = VariableResolver.resolve(rawUrl, context)

                if (urlString.isEmpty()) return@withContext ExecutionResult.Failure("参数错误", "URL不能为空")

                if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    urlString = "https://$urlString"
                }

                val method = context.variables["method"] as? String ?: "GET"

                // 处理 Map 类型的参数 (Headers, Query Params)
                // 这里暂时假设 Map 的 Value 已经是解析好的，或者在 UI 层没有提供富文本输入。
                // 如果需要深度解析 Map 中的 Value，需要额外的逻辑遍历 Map。
                // 鉴于目前 DictionaryKVAdapter 较为简单，先保持原样，直接读取 magicVariables 或 raw variables
                val headers = (context.magicVariables["headers"] as? DictionaryVariable)?.value
                    ?: (context.variables["headers"] as? Map<String, Any?>)
                    ?: emptyMap()
                val queryParams = (context.magicVariables["query_params"] as? DictionaryVariable)?.value
                    ?: (context.variables["query_params"] as? Map<String, Any?>)
                    ?: emptyMap()

                val bodyType = context.variables["body_type"] as? String ?: "无"
                val bodyDataRaw = context.variables["body"]
                // 对于 Body，我们需要区分处理
                val bodyData = if (bodyType == "原始文本") {
                    // 如果是原始文本，支持富文本解析
                    VariableResolver.resolve(bodyDataRaw?.toString() ?: "", context)
                } else {
                    // 如果是 JSON 或表单 (Map)，或者直接连接了变量
                    context.magicVariables["body"] ?: bodyDataRaw
                }

                val timeout = ((context.magicVariables["timeout"] as? NumberVariable)?.value
                    ?: (context.variables["timeout"] as? Number)?.toDouble()
                    ?: 10.0).toLong()

                val client = OkHttpClient.Builder()
                    .callTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val httpUrlBuilder = urlString.toHttpUrlOrNull()?.newBuilder() ?: return@withContext ExecutionResult.Failure("URL格式错误", "无法解析URL: $urlString")
                queryParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value.toString()) }
                val finalUrl = httpUrlBuilder.build()

                val requestBody = when {
                    method == "GET" || method == "DELETE" -> null
                    else -> createRequestBody(bodyType, bodyData)
                }

                val requestBuilder = Request.Builder().url(finalUrl)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value.toString()) }
                requestBuilder.method(method, requestBody)

                onProgress(ProgressUpdate("正在发送 $method 请求到 $finalUrl"))
                val response = client.newCall(requestBuilder.build()).execute()

                val statusCode = response.code
                val responseBody = response.body?.string() ?: ""
                val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }

                onProgress(ProgressUpdate("收到响应，状态码: $statusCode"))

                ExecutionResult.Success(mapOf(
                    "response_body" to TextVariable(responseBody),
                    "status_code" to NumberVariable(statusCode.toDouble()),
                    "response_headers" to DictionaryVariable(responseHeaders),
                    "error" to TextVariable("")
                ))

            } catch (e: IOException) {
                ExecutionResult.Failure("网络错误", e.message ?: "未知网络错误").also {
                }
            } catch (e: Exception) {
                ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发生未知错误")
            }
        }
    }

    private fun createRequestBody(bodyType: String, bodyData: Any?): RequestBody? {
        return when (bodyType) {
            "JSON" -> {
                val json = Gson().toJson(bodyData)
                json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
            "表单" -> {
                val formBuilder = FormBody.Builder()
                (bodyData as? Map<*, *>)?.forEach { (key, value) ->
                    formBuilder.add(key.toString(), value.toString())
                }
                formBuilder.build()
            }
            "原始文本" -> {
                (bodyData?.toString() ?: "").toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
            }
            else -> null
        }
    }
}