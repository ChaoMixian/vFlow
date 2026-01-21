// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
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
        InputDefinition("body", "请求体", ParameterType.ANY, acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("timeout", "超时(秒)", ParameterType.NUMBER, 10.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)),
        InputDefinition("show_advanced", "显示高级", ParameterType.BOOLEAN, false, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("response_body", "响应内容", VTypeRegistry.STRING.id),
        OutputDefinition("status_code", "状态码", VTypeRegistry.NUMBER.id),
        OutputDefinition("response_headers", "响应头", VTypeRegistry.DICTIONARY.id),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val method = step.parameters["method"] as? String ?: "GET"
        val rawUrl = step.parameters["url"]?.toString() ?: ""

        // 检查 URL 是否为复杂内容（包含变量或其他文本的组合）
        if (VariableResolver.isComplex(rawUrl)) {
            // 复杂内容：只显示方法和模块名称，详细内容由 UIProvider 在预览中显示
            return PillUtil.buildSpannable(context, method, " ", metadata.name)
        }

        // 简单内容：显示完整的摘要（带药丸）
        val urlPill = PillUtil.createPillFromParam(step.parameters["url"], getInputs().find { it.id == "url" })
        return PillUtil.buildSpannable(context, method, " ", urlPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                // 使用 VariableResolver 解析 URL
                val rawUrl = context.variables["url"]?.toString() ?: ""
                var urlString = VariableResolver.resolve(rawUrl, context)

                if (urlString.isEmpty()) return@withContext ExecutionResult.Failure("参数错误", "URL不能为空")

                if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    urlString = "https://$urlString"
                }

                val method = context.variables["method"] as? String ?: "GET"

                // 解析 Headers (支持变量)
                @Suppress("UNCHECKED_CAST")
                val rawHeaders = (context.magicVariables["headers"] as? DictionaryVariable)?.value
                    ?: (context.variables["headers"] as? Map<String, Any?>)
                    ?: emptyMap()
                val headers = resolveMap(rawHeaders, context)

                // 解析 Query Params (支持变量)
                @Suppress("UNCHECKED_CAST")
                val rawQueryParams = (context.magicVariables["query_params"] as? DictionaryVariable)?.value
                    ?: (context.variables["query_params"] as? Map<String, Any?>)
                    ?: emptyMap()
                val queryParams = resolveMap(rawQueryParams, context)

                // 解析 Body
                val bodyType = context.variables["body_type"] as? String ?: "无"
                val bodyDataRaw = context.variables["body"]

                val bodyData: Any? = when (bodyType) {
                    "原始文本" -> {
                        // 原始文本：直接解析富文本字符串
                        VariableResolver.resolve(bodyDataRaw?.toString() ?: "", context)
                    }
                    "表单" -> {
                        // 表单：必须使用字符串转换（表单编码要求）
                        val mapData = (context.magicVariables["body"] as? DictionaryVariable)?.value
                            ?: (bodyDataRaw as? Map<*, *>)
                        if (mapData is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            resolveMap(mapData as Map<String, Any?>, context)  // 保持现有行为：转字符串
                        } else {
                            mapData
                        }
                    }
                    "JSON" -> {
                        // JSON：支持字符串输入（来自 RichTextView）或 Map 输入（来自变量引用）
                        val mapData = (context.magicVariables["body"] as? DictionaryVariable)?.value
                            ?: (bodyDataRaw as? Map<*, *>)

                        if (mapData is Map<*, *>) {
                            // Map 输入：递归解析 Map 中的值
                            @Suppress("UNCHECKED_CAST")
                            resolveMapForJson(mapData as Map<String, Any?>, context)
                        } else {
                            // 字符串输入：解析变量引用，然后尝试解析 JSON
                            val jsonString = VariableResolver.resolve(bodyDataRaw?.toString() ?: "", context)
                            // 尝试解析 JSON 字符串为对象，以便保留类型
                            try {
                                Gson().fromJson(jsonString, Any::class.java)
                            } catch (e: Exception) {
                                // JSON 解析失败，返回原始字符串
                                jsonString
                            }
                        }
                    }
                    else -> context.magicVariables["body"] ?: bodyDataRaw
                }

                val timeout = ((context.magicVariables["timeout"] as? NumberVariable)?.value
                    ?: (context.variables["timeout"] as? Number)?.toDouble()
                    ?: 10.0).toLong()

                val client = OkHttpClient.Builder()
                    .callTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val httpUrlBuilder = urlString.toHttpUrlOrNull()?.newBuilder() ?: return@withContext ExecutionResult.Failure("URL格式错误", "无法解析URL: $urlString")
                queryParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
                val finalUrl = httpUrlBuilder.build()

                val requestBody = when {
                    method == "GET" || method == "DELETE" -> null
                    else -> createRequestBody(bodyType, bodyData)
                }

                val requestBuilder = Request.Builder().url(finalUrl)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
                requestBuilder.method(method, requestBody)

                onProgress(ProgressUpdate("正在发送 $method 请求到 $finalUrl"))
                val response = client.newCall(requestBuilder.build()).execute()

                val statusCode = response.code
                val responseBody = response.body?.string() ?: ""
                val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }

                onProgress(ProgressUpdate("收到响应，状态码: $statusCode"))

                ExecutionResult.Success(mapOf(
                    "response_body" to VString(responseBody),
                    "status_code" to VNumber(statusCode.toDouble()),
                    "response_headers" to VDictionary(responseHeaders.mapValues { VString(it.value) }),
                    "error" to VString("")
                ))

            } catch (e: IOException) {
                ExecutionResult.Failure("网络错误", e.message ?: "未知网络错误")
            } catch (e: Exception) {
                ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发生未知错误")
            }
        }
    }

    /**
     * 辅助函数：遍历 Map 中的值，如果是字符串则尝试解析变量。
     * 注意：此函数将所有值转换为字符串，适用于表单编码，但不适用于JSON（会丢失类型）。
     */
    private fun resolveMap(map: Map<String, Any?>, context: ExecutionContext): Map<String, String> {
        return map.entries.associate { (key, value) ->
            val resolvedValue = if (value is String) {
                VariableResolver.resolve(value, context)
            } else {
                value?.toString() ?: ""
            }
            key to resolvedValue
        }
    }

    /**
     * 用于JSON的Map解析：保留类型信息
     * 返回 Map<String, Any?> 其中值可以是 String, Number, Boolean, Map, List, null
     */
    private fun resolveMapForJson(map: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        return map.mapValues { (_, value) ->
            resolveValuePreservingType(value, context)
        }
    }

    /**
     * 递归解析值，保留原始类型用于JSON序列化
     * - 字符串：解析变量引用
     * - VObject：提取raw值，递归处理嵌套结构
     * - 基础类型（数字/布尔/null）：直接返回
     * - 集合类型（Map/List）：递归处理每个元素
     */
    private fun resolveValuePreservingType(value: Any?, context: ExecutionContext): Any? {
        return when (value) {
            // 1. 字符串：使用resolveValue保留类型，或resolve解析为字符串
            is String -> {
                // 如果字符串只是单个变量引用（如 "{{step1.output}}"），使用resolveValue保留类型
                if (!VariableResolver.isComplex(value)) {
                    VariableResolver.resolveValue(value, context)
                } else {
                    // 复杂文本（混合文本和变量），解析为字符串
                    VariableResolver.resolve(value, context)
                }
            }

            // 2. VObject: 提取原始值并递归处理
            is VObject -> {
                when (value) {
                    is VString -> {
                        // VString也可能是复杂字符串，需要解析
                        val rawStr = value.raw ?: ""
                        if (!VariableResolver.isComplex(rawStr)) {
                            VariableResolver.resolveValue(rawStr, context)
                        } else {
                            VariableResolver.resolve(rawStr, context)
                        }
                    }
                    is VNumber -> value.raw  // 保留 Double 类型
                    is VBoolean -> value.raw  // 保留 Boolean 类型
                    is VList -> value.raw.map { resolveValuePreservingType(it, context) }
                    is VDictionary -> {
                        value.raw.mapValues { (_, vObj) ->
                            resolveValuePreservingType(vObj, context)
                        }
                    }
                    else -> value.asString()  // 兜底
                }
            }

            // 3. 旧版Variable类型兼容
            is NumberVariable -> value.value
            is BooleanVariable -> value.value
            is TextVariable -> VariableResolver.resolve(value.value, context)
            is ListVariable -> value.value.map { resolveValuePreservingType(it, context) }
            is DictionaryVariable -> {
                value.value.mapValues { (_, v) ->
                    resolveValuePreservingType(v, context)
                }
            }

            // 4. 集合类型：递归处理
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (value as? Map<String, Any?>)?.mapValues { (_, v) ->
                    resolveValuePreservingType(v, context)
                }
            }
            is List<*> -> value.map { resolveValuePreservingType(it, context) }

            // 5. 基础类型：直接返回
            is Number, is Boolean, null -> value

            // 6. 兜底
            else -> value.toString()
        }
    }

    /**
     * 根据bodyType创建适当的请求体
     * Content-Type会自动设置：
     * - JSON: application/json; charset=utf-8
     * - 表单: application/x-www-form-urlencoded（由FormBody自动设置）
     * - 原始文本: text/plain; charset=utf-8
     */
    private fun createRequestBody(bodyType: String, bodyData: Any?): RequestBody? {
        return when (bodyType) {
            "JSON" -> {
                val json = Gson().toJson(bodyData)
                // 明确设置Content-Type为application/json
                json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
            "表单" -> {
                val formBuilder = FormBody.Builder()
                (bodyData as? Map<*, *>)?.forEach { (key, value) ->
                    formBuilder.add(key.toString(), value.toString())
                }
                // FormBody会自动设置Content-Type为application/x-www-form-urlencoded
                formBuilder.build()
            }
            "原始文本" -> {
                // 设置Content-Type为text/plain
                (bodyData?.toString() ?: "").toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
            }
            else -> null
        }
    }
}