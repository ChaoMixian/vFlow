// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.serialization.VObjectGsonAdapter
import okhttp3.MultipartBody
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
        nameStringRes = R.string.module_vflow_network_http_request_name,
        descriptionStringRes = R.string.module_vflow_network_http_request_desc,
        name = "HTTP 请求",  // Fallback
        description = "发送 HTTP 请求并获取响应",  // Fallback
        iconRes = R.drawable.rounded_public_24,
        category = "网络"
    )

    // 为该模块提供自定义的编辑器UI
    override val uiProvider: ModuleUIProvider? = HttpRequestModuleUIProvider()

    // 定义支持的HTTP方法和请求体类型
    val methodOptions = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    val bodyTypeOptions = listOf("无", "JSON", "表单", "原始文本", "文件")

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

    /**
     * 动态输入：根据 body_type 设置 body 的 acceptedMagicVariableTypes
     * - 文件类型：只允许图片类型
     * - 其他类型：允许任何类型
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val baseInputs = getInputs()
        val bodyType = step?.parameters?.get("body_type") as? String ?: "无"

        return baseInputs.map { inputDef ->
            if (inputDef.id == "body") {
                // 根据请求体类型动态设置接受的变量类型
                val acceptedTypes = when (bodyType) {
                    "文件" -> setOf(VTypeRegistry.IMAGE.id)  // 只允许图片
                    else -> emptySet()  // 允许任何类型
                }
                inputDef.copy(acceptedMagicVariableTypes = acceptedTypes)
            } else {
                inputDef
            }
        }
    }

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
                val rawUrl = context.getVariableAsString("url", "")
                var urlString = VariableResolver.resolve(rawUrl, context)

                if (urlString.isEmpty()) return@withContext ExecutionResult.Failure("参数错误", "URL不能为空")

                if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    urlString = "https://$urlString"
                }

                val method = context.getVariableAsString("method", "GET")

                // 解析 Headers (支持变量)
                @Suppress("UNCHECKED_CAST")
                val headersObj = context.getVariable("headers")
                val rawHeaders = (headersObj as? VDictionary)?.raw
                    ?: (headersObj as? Map<String, Any?>)
                    ?: emptyMap()
                val headers = resolveMap(rawHeaders, context)

                // 解析 Query Params (支持变量)
                @Suppress("UNCHECKED_CAST")
                val queryParamsObj = context.getVariable("query_params")
                val rawQueryParams = (queryParamsObj as? VDictionary)?.raw
                    ?: (queryParamsObj as? Map<String, Any?>)
                    ?: emptyMap()
                val queryParams = resolveMap(rawQueryParams, context)

                // 解析 Body
                val bodyType = context.getVariableAsString("body_type", "无")
                val bodyDataRaw = context.getVariable("body")

                val bodyData: Any? = when (bodyType) {
                    "原始文本" -> {
                        // 原始文本：直接解析富文本字符串
                        VariableResolver.resolve(bodyDataRaw?.asString() ?: "", context)
                    }
                    "表单" -> {
                        // 表单：必须使用字符串转换（表单编码要求）
                        val mapData = (bodyDataRaw as? VDictionary)?.raw
                            ?: (bodyDataRaw as? Map<*, *>)
                        if (mapData is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            resolveMap(mapData as Map<String, Any?>, context)  // 保持现有行为：转字符串
                        } else {
                            mapData
                        }
                    }
                    "JSON" -> {
                        // JSON：支持三种输入：
                        // 1. Map 输入（来自变量引用）- 递归解析保留类型
                        // 2. VObject 输入（直接插入的魔法变量）- 提取raw值并递归解析
                        // 3. 字符串输入（来自 RichTextView）- 解析变量后尝试解析JSON
                        val mapData = (bodyDataRaw as? VDictionary)?.raw
                            ?: (bodyDataRaw as? Map<*, *>)

                        if (mapData is Map<*, *>) {
                            // Map 输入：递归解析 Map 中的值
                            @Suppress("UNCHECKED_CAST")
                            resolveMapForJson(mapData as Map<String, Any?>, context)
                        } else if (bodyDataRaw is VObject) {
                            // VObject 输入（直接插入的魔法变量，如 VBoolean、VString 等）
                            resolveValuePreservingType(bodyDataRaw, context)
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
                    "文件" -> {
                        // 文件：不解析变量，直接传递原始文本（稍后在 createRequestBody 中处理）
                        // 这样可以保留 {{step1.image}}{{step2.image}} 格式的变量引用
                        bodyDataRaw?.asString() ?: ""
                    }
                    else -> bodyDataRaw
                }

                val timeout = context.getVariableAsLong("timeout") ?: 10

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val httpUrlBuilder = urlString.toHttpUrlOrNull()?.newBuilder() ?: return@withContext ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_http_request_url_format_error),
                    String.format(appContext.getString(R.string.error_vflow_network_http_request_url_parse_failed), urlString)
                )
                queryParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
                val finalUrl = httpUrlBuilder.build()

                val requestBody = when {
                    method == "GET" || method == "DELETE" -> null
                    else -> createRequestBody(context, bodyType, bodyData)
                }

                val requestBuilder = Request.Builder().url(finalUrl)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
                requestBuilder.method(method, requestBody)

                onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_network_http_request_sending), method, finalUrl)))
                val response = client.newCall(requestBuilder.build()).execute()

                val statusCode = response.code
                val responseBody = response.body?.string() ?: ""
                val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }

                onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_network_http_request_received), statusCode)))

                ExecutionResult.Success(mapOf(
                    "response_body" to VString(responseBody),
                    "status_code" to VNumber(statusCode.toDouble()),
                    "response_headers" to VDictionary(responseHeaders.mapValues { VString(it.value) }),
                    "error" to VString("")
                ))

            } catch (e: IOException) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_http_request_network_error),
                    e.message ?: appContext.getString(R.string.error_vflow_network_http_request_unknown_network)
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_http_request_execution_failed),
                    e.localizedMessage ?: appContext.getString(R.string.error_vflow_network_http_request_unknown_error)
                )
            }
        }
    }

    /**
     * 辅助函数：遍历 Map 中的值，如果是字符串则尝试解析变量。
     * 注意：此函数将所有值转换为字符串，适用于表单编码，但不适用于JSON（会丢失类型）。
     */
    private fun resolveMap(map: Map<String, Any?>, context: ExecutionContext): Map<String, String> {
        return map.entries.associate { (key, value) ->
            val resolvedValue = when (value) {
                is String -> VariableResolver.resolve(value, context)
                is VString -> {
                    val rawStr = value.raw ?: ""
                    VariableResolver.resolve(rawStr, context)
                }
                is VObject -> value.asString()
                else -> value?.toString() ?: ""
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
                    is VNumber -> value.raw.toDouble()  // 转换为 Double 以保持兼容性
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

            // 3. 集合类型：递归处理
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
     * - 文件: multipart/form-data（用于上传多个图片）
     */
    private fun createRequestBody(context: ExecutionContext, bodyType: String, bodyData: Any?): RequestBody? {
        return when (bodyType) {
            "JSON" -> {
                // fixme: VList转字符串后不是Json格式
                val jsonString = when (bodyData) {
                    is String -> bodyData
                    is Map<*, *> -> Gson().toJson(bodyData)
                    is List<*> -> Gson().toJson(bodyData)
                    is VDictionary -> Gson().toJson(bodyData.raw)
                    is VList -> Gson().toJson(bodyData.raw)
                    else -> bodyData?.toString() ?: ""
                }
                // 明确设置Content-Type为application/json
                jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
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
            "文件" -> {
                // 支持上传多个图片文件（使用 multipart/form-data）
                // bodyData 应该是原始文本，可能包含多个变量引用
                val resolvedText = bodyData?.toString() ?: ""

                android.util.Log.d("HttpRequestModule", "File upload - bodyData: $bodyData, resolvedText: '$resolvedText'")

                // 解析富文本中的所有图片变量引用
                val images = extractImagesFromRichText(resolvedText, context)

                android.util.Log.d("HttpRequestModule", "Images count: ${images.size}")

                if (images.isEmpty()) {
                    android.util.Log.e("HttpRequestModule", "No images found, trying magicVariables")
                    // 没有找到图片，尝试直接作为单个 VImage 处理
                    val image = context.getVariable("body") as? VImage
                    android.util.Log.d("HttpRequestModule", "Magic variable image: $image")
                    if (image != null) {
                        createSingleFileUpload(context, image)
                    } else {
                        android.util.Log.e("HttpRequestModule", "No image found at all, returning null")
                        null
                    }
                } else {
                    // 多个图片，创建 multipart 请求
                    createMultipleFilesUpload(context, images)
                }
            }
            else -> null
        }
    }

    /**
     * 从富文本中提取所有图片变量
     * 支持纯变量引用（如 "{{step1.image}}"）和混合文本（如 "prefix{{step1.image}}suffix"）
     * 会自动过滤掉纯文本干扰
     */
    private fun extractImagesFromRichText(text: String, context: ExecutionContext): List<VImage> {
        val images = mutableListOf<VImage>()

        // 正则匹配变量引用 {{variable}}
        val variablePattern = Regex("\\{\\{([^}]+)\\}\\}")
        val matches = variablePattern.findAll(text)

        android.util.Log.d("HttpRequestModule", "extractImagesFromRichText: text='$text', matches count=${matches.count()}")

        for (match in matches) {
            val variablePath = match.groupValues[1]
            android.util.Log.d("HttpRequestModule", "Found variable: $variablePath")

            // 解析变量路径：格式为 "stepId.outputId" 或 "outputId"
            val parts = variablePath.split('.')
            val stepId = if (parts.size >= 2) parts[0] else null
            val outputId = if (parts.size >= 2) parts[1] else parts[0]

            android.util.Log.d("HttpRequestModule", "Parsed - stepId: $stepId, outputId: $outputId")

            // 尝试从步骤输出中获取 VImage 对象
            val image = if (stepId != null) {
                // 格式：stepId.outputId
                val stepOutput = context.stepOutputs[stepId]
                val outputValue = stepOutput?.get(outputId)
                android.util.Log.d("HttpRequestModule", "Step output value type: ${outputValue?.javaClass?.name}")
                outputValue as? VImage
            } else {
                // 格式：只有 outputId（从当前步骤的魔法变量获取）
                val magicVar = context.getVariable(outputId)
                android.util.Log.d("HttpRequestModule", "Magic variable type: ${magicVar?.javaClass?.name}")
                magicVar as? VImage
            }

            if (image != null) {
                android.util.Log.d("HttpRequestModule", "Added VImage: ${image.uriString}")
                images.add(image)
            } else {
                android.util.Log.w("HttpRequestModule", "Variable $variablePath is not an image or not found")
            }
        }

        android.util.Log.d("HttpRequestModule", "Total images extracted: ${images.size}")
        return images
    }

    /**
     * 创建单个文件上传的请求体
     */
    private fun createSingleFileUpload(context: ExecutionContext, image: VImage): RequestBody? {
        return try {
            val uri = android.net.Uri.parse(image.uriString)
            val resolver = context.applicationContext.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return null

            // 读取图片数据到内存
            val bytes = inputStream.use { it.readBytes() }

            // 获取 MIME 类型
            val mimeType = resolver.getType(uri) ?: "image/jpeg"

            // 获取文件名
            val fileName = image.uriString.substringAfterLast("/")

            // 创建 multipart body
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",  // 表单字段名
                    fileName,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 创建多文件上传的请求体
     */
    private fun createMultipleFilesUpload(context: ExecutionContext, images: List<VImage>): RequestBody? {
        return try {
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

            images.forEachIndexed { index, image ->
                val uri = android.net.Uri.parse(image.uriString)
                val resolver = context.applicationContext.contentResolver
                val inputStream = resolver.openInputStream(uri) ?: return@forEachIndexed

                try {
                    // 读取图片数据到内存
                    val bytes = inputStream.use { it.readBytes() }

                    // 获取 MIME 类型
                    val mimeType = resolver.getType(uri) ?: "image/jpeg"

                    // 获取文件名
                    val fileName = image.uriString.substringAfterLast("/")

                    // 添加到 multipart 请求
                    // 使用 file0, file1, file2... 作为字段名
                    multipartBuilder.addFormDataPart(
                        "file$index",
                        fileName,
                        bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            multipartBuilder.build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}