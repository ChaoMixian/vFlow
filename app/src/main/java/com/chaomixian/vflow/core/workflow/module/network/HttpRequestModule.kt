// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
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
        iconRes = R.drawable.rounded_public_24, // 复用现有图标
        category = "网络"
    )

    // 为该模块提供自定义的编辑器UI
    override val uiProvider: ModuleUIProvider? = HttpRequestModuleUIProvider()

    // 定义支持的HTTP方法和请求体类型
    val methodOptions = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    val bodyTypeOptions = listOf("无", "JSON", "表单", "原始文本")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("url", "URL", ParameterType.STRING, acceptsMagicVariable = true, acceptsNamedVariable = true),
        InputDefinition("method", "方法", ParameterType.ENUM, "GET", options = methodOptions, acceptsMagicVariable = false),
        InputDefinition("headers", "请求头", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true, acceptsNamedVariable = true),
        InputDefinition("query_params", "查询参数", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true, acceptsNamedVariable = true),
        InputDefinition("body_type", "请求体类型", ParameterType.ENUM, "无", options = bodyTypeOptions, acceptsMagicVariable = false),
        InputDefinition("body", "请求体", ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true),
        InputDefinition("timeout", "超时(秒)", ParameterType.NUMBER, 10.0, acceptsMagicVariable = true, acceptsNamedVariable = true)
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
                // 解析参数
                val urlString = (context.magicVariables["url"] as? TextVariable)?.value
                    ?: (context.variables["url"] as? String)
                    ?: return@withContext ExecutionResult.Failure("参数错误", "URL不能为空")

                // 自动为URL添加协议头
                val finalUrlString = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    "https://$urlString"
                } else {
                    urlString
                }

                val method = context.variables["method"] as? String ?: "GET"
                val headers = (context.magicVariables["headers"] as? DictionaryVariable)?.value
                    ?: (context.variables["headers"] as? Map<String, Any?>)
                    ?: emptyMap()
                val queryParams = (context.magicVariables["query_params"] as? DictionaryVariable)?.value
                    ?: (context.variables["query_params"] as? Map<String, Any?>)
                    ?: emptyMap()
                val bodyType = context.variables["body_type"] as? String ?: "无"
                val bodyData = context.magicVariables["body"] ?: context.variables["body"]
                val timeout = ((context.magicVariables["timeout"] as? NumberVariable)?.value
                    ?: (context.variables["timeout"] as? Number)?.toDouble()
                    ?: 10.0).toLong()

                // 构建 OkHttpClient
                val client = OkHttpClient.Builder()
                    .callTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                // 构建 URL (带查询参数)
                val httpUrlBuilder = finalUrlString.toHttpUrlOrNull()?.newBuilder() ?: return@withContext ExecutionResult.Failure("URL格式错误", "无法解析URL: $finalUrlString")
                queryParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value.toString()) }
                val finalUrl = httpUrlBuilder.build()

                // 构建 RequestBody
                val requestBody = when {
                    method == "GET" || method == "DELETE" -> null
                    else -> createRequestBody(bodyType, bodyData)
                }

                // 构建 Request
                val requestBuilder = Request.Builder().url(finalUrl)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value.toString()) }
                requestBuilder.method(method, requestBody)
                val request = requestBuilder.build()

                // 执行请求
                onProgress(ProgressUpdate("正在发送 $method 请求到 $finalUrl"))
                val response = client.newCall(request).execute()

                // 解析响应
                val statusCode = response.code
                val responseBody = response.body?.string() ?: ""
                val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }

                onProgress(ProgressUpdate("收到响应，状态码: $statusCode"))

                ExecutionResult.Success(mapOf(
                    "response_body" to TextVariable(responseBody),
                    "status_code" to NumberVariable(statusCode.toDouble()),
                    "response_headers" to DictionaryVariable(responseHeaders),
                    "error" to TextVariable("") // 成功时错误信息为空
                ))

            } catch (e: IOException) {
                ExecutionResult.Failure("网络错误", e.message ?: "未知网络错误").also {
                    return@withContext it.copy(outputs = mapOf("error" to TextVariable(it.errorMessage)))
                }
            } catch (e: Exception) {
                ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发生未知错误").also {
                    return@withContext it.copy(outputs = mapOf("error" to TextVariable(it.errorMessage)))
                }
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

    // 增加一个 copy 方法，用于在返回 Failure 的同时也能携带输出
    private fun ExecutionResult.Failure.copy(outputs: Map<String, Any?>): ExecutionResult.Failure {
        return ExecutionResult.Failure(this.errorTitle, this.errorMessage).apply {
            // 虽然是 Failure, 但仍可以附加一些输出，比如 error 信息
            // this.outputs = outputs
            // (注意: 原版 ExecutionResult.Failure 没有 outputs 字段, 此处为示意，实际返回中不包含)
        }
    }
}