// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/FeishuMediaUploadModule.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.zip.Adler32

/**
 * 飞书云文档素材上传模块。
 * 支持将文件、图片等素材上传到飞书指定的云文档中。
 */
class FeishuMediaUploadModule : BaseModule() {
    override val id = "vflow.network.feishu_upload"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_feishu_upload_name,
        descriptionStringRes = R.string.module_vflow_network_feishu_upload_desc,
        name = "飞书上传素材",
        description = "上传文件到飞书云文档",
        iconRes = R.drawable.rounded_cloud_24,
        category = "网络"
    )

    private val parentTypeOptions = listOf(
        "doc_image" to "旧版文档图片",
        "docx_image" to "新版文档图片",
        "sheet_image" to "电子表格图片",
        "doc_file" to "旧版文档文件",
        "docx_file" to "新版文档文件",
        "sheet_file" to "电子表格文件",
        "bitable_image" to "多维表格图片",
        "bitable_file" to "多维表格文件"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "access_token",
            name = "访问令牌",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            hint = "飞书应用的 access_token 或 user_access_token"
        ),
        InputDefinition(
            id = "file",
            name = "文件",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id),
            hint = "选择或引用要上传的图片文件"
        ),
        InputDefinition(
            id = "file_name",
            name = "文件名",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            defaultValue = "",
            hint = "留空则使用原始文件名"
        ),
        InputDefinition(
            id = "parent_type",
            name = "上传点类型",
            staticType = ParameterType.ENUM,
            defaultValue = "docx_image",
            options = parentTypeOptions.map { it.first }
        ),
        InputDefinition(
            id = "parent_node",
            name = "文档 Token",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            hint = "要上传到的云文档 token"
        ),
        InputDefinition(
            id = "extra",
            name = "额外参数",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            supportsRichText = true,
            defaultValue = "",
            isFolded = true,
            hint = "JSON 格式，如 {\"drive_route_token\":\"xxx\"}"
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 30.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "file_token",
            name = "文件 Token",
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "code",
            name = "响应码",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "msg",
            name = "响应消息",
            typeName = VTypeRegistry.STRING.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val fileNamePill = PillUtil.createPillFromParam(step.parameters["file_name"], getInputs().find { it.id == "file_name" })
        val parentTypePill = PillUtil.createPillFromParam(step.parameters["parent_type"], getInputs().find { it.id == "parent_type" })

        return PillUtil.buildSpannable(context,"飞书上传",  parentTypePill, "并命名为", fileNamePill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val rawAccessToken = context.getVariableAsString("access_token", "")
                val accessToken = rawAccessToken.trim()

                if (accessToken.isEmpty()) {
                    return@withContext ExecutionResult.Failure(
                        "参数错误",
                        "访问令牌不能为空"
                    )
                }

                val fileVar = context.getVariable("file")
                val image = fileVar as? VImage
                if (image == null) {
                    return@withContext ExecutionResult.Failure(
                        "参数错误",
                        "文件参数必须是图片类型"
                    )
                }

                val rawFileName = context.getVariableAsString("file_name", "")
                val fileName = if (rawFileName.isEmpty()) {
                    image.uriString.substringAfterLast("/")
                } else {
                    rawFileName
                }

                if (fileName.length > 250) {
                    return@withContext ExecutionResult.Failure(
                        "参数错误",
                        "文件名长度不能超过 250 字符"
                    )
                }

                val parentType = context.getVariableAsString("parent_type", "docx_image")

                val parentNode = context.getVariableAsString("parent_node", "")
                if (parentNode.isEmpty()) {
                    return@withContext ExecutionResult.Failure(
                        "参数错误",
                        "文档 Token 不能为空"
                    )
                }

                val extra = context.getVariableAsString("extra", "")

                val timeout = context.getVariableAsLong("timeout") ?: 30

                onProgress(ProgressUpdate("正在读取文件..."))
                val uri = android.net.Uri.parse(image.uriString)
                val resolver = context.applicationContext.contentResolver
                val inputStream = resolver.openInputStream(uri)
                    ?: return@withContext ExecutionResult.Failure("文件错误", "无法打开文件")

                val fileBytes = inputStream.use { it.readBytes() }
                val fileSize = fileBytes.size

                if (fileSize > 20971520) {
                    return@withContext ExecutionResult.Failure(
                        "文件错误",
                        "文件大小超过 20MB 限制 (当前: ${fileSize / 1024 / 1024}MB)"
                    )
                }

                val checksum = Adler32().apply { update(fileBytes) }.value.toString()

                onProgress(ProgressUpdate("正在上传 (${fileSize / 1024}KB)..."))

                val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

                multipartBuilder.addFormDataPart("file_name", fileName)
                multipartBuilder.addFormDataPart("parent_type", parentType)
                multipartBuilder.addFormDataPart("parent_node", parentNode)
                multipartBuilder.addFormDataPart("size", fileSize.toString())
                multipartBuilder.addFormDataPart("checksum", checksum)

                if (extra.isNotEmpty()) {
                    multipartBuilder.addFormDataPart("extra", extra)
                }

                val mimeType = resolver.getType(uri) ?: "application/octet-stream"

                multipartBuilder.addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )

                val client = OkHttpClient.Builder()
                    .callTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://open.feishu.cn/open-apis/drive/v1/medias/upload_all")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(multipartBuilder.build())
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                onProgress(ProgressUpdate("上传完成，正在解析响应..."))

                val jsonResponse = try {
                    com.google.gson.Gson().fromJson(responseBody, java.util.Map::class.java) as? Map<String, Any?>
                } catch (e: Exception) {
                    null
                }

                if (jsonResponse == null) {
                    return@withContext ExecutionResult.Failure(
                        "响应解析失败",
                        "无法解析服务器响应: $responseBody"
                    )
                }

                val code = (jsonResponse["code"] as? Double)?.toInt() ?: -1
                val msg = (jsonResponse["msg"] as? String) ?: "未知错误"
                val data = jsonResponse["data"] as? Map<String, Any?>

                if (code != 0) {
                    return@withContext ExecutionResult.Failure(
                        "上传失败",
                        "错误码: $code, 消息: $msg"
                    )
                }

                val fileToken = data?.get("file_token") as? String ?: ""

                onProgress(ProgressUpdate("上传成功！"))

                ExecutionResult.Success(mapOf(
                    "file_token" to VString(fileToken),
                    "code" to com.chaomixian.vflow.core.types.basic.VNumber(code.toDouble()),
                    "msg" to VString(msg)
                ))

            } catch (e: IOException) {
                ExecutionResult.Failure(
                    "网络错误",
                    e.message ?: "上传文件时发生网络错误"
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    "执行失败",
                    e.localizedMessage ?: "上传文件时发生未知错误"
                )
            }
        }
    }
}
