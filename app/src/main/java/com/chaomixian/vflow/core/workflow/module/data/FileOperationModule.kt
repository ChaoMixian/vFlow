// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/module/data/FileOperationModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.PickerType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import androidx.core.net.toUri

/**
 * 文件操作模块
 * 支持文件的读取、写入、追加、删除操作
 */
class FileOperationModule : BaseModule() {

    override val id = "vflow.data.file_operation"
    override val metadata = com.chaomixian.vflow.core.module.ActionMetadata(
        nameStringRes = R.string.module_vflow_data_file_operation_name,
        descriptionStringRes = R.string.module_vflow_data_file_operation_desc,
        name = "文件操作",
        description = "对文件进行读取、写入等操作",
        iconRes = R.drawable.rounded_inbox_text_share_24,
        category = "数据"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        // 1. 基础输入 - 文件路径（使用文件选择器）
        InputDefinition(
            id = "file_path",
            nameStringRes = R.string.param_vflow_data_file_operation_file_path_name,
            name = "文件路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "点击选择文件",
            pickerType = PickerType.FILE,
            acceptsMagicVariable = false
        ),

        // 2. 操作类型（使用 CHIP_GROUP）
        InputDefinition(
            id = "operation",
            nameStringRes = R.string.param_vflow_data_file_operation_operation_name,
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = "读取",
            options = listOf("读取", "写入", "删除", "追加"),
            inputStyle = InputStyle.CHIP_GROUP
        ),

        // 3. 写入内容 - 当操作是"写入"或"追加"时显示
        InputDefinition(
            id = "content",
            nameStringRes = R.string.param_vflow_data_file_operation_content_name,
            name = "写入内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "输入要写入的内容",
            supportsRichText = true,
            visibility = InputVisibility.`in`("operation", listOf("写入", "追加"))
        ),

        // 4. 编码格式 - 当操作是"读取"时显示
        InputDefinition(
            id = "encoding",
            nameStringRes = R.string.param_vflow_data_file_operation_encoding_name,
            name = "编码格式",
            staticType = ParameterType.ENUM,
            defaultValue = "UTF-8",
            options = listOf("UTF-8", "GBK", "GB2312", "ISO-8859-1"),
            inputStyle = InputStyle.CHIP_GROUP,
            visibility = InputVisibility.whenEquals("operation", "读取")
        ),

        // 5. 高级设置（折叠区域）
        InputDefinition(
            id = "overwrite",
            nameStringRes = R.string.param_vflow_data_file_operation_overwrite_name,
            name = "覆盖写入",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            visibility = InputVisibility.whenEquals("operation", "写入")
        ),

        InputDefinition(
            id = "buffer_size",
            nameStringRes = R.string.param_vflow_data_file_operation_buffer_size_name,
            name = "缓冲区大小",
            staticType = ParameterType.NUMBER,
            defaultValue = 8192,
            hint = "字节数",
            sliderConfig = InputDefinition.Companion.slider(1024f, 65536f, 1024f),
            isFolded = true,
            visibility = InputVisibility.`in`("operation", listOf("读取", "写入", "追加"))
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val operation = step?.parameters?.get("operation") as? String

        return when (operation) {
            "读取" -> listOf(
                OutputDefinition("content", "文件内容", VTypeRegistry.STRING.id),
                OutputDefinition("size", "文件大小", VTypeRegistry.NUMBER.id)
            )
            else -> listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("message", "操作信息", VTypeRegistry.STRING.id)
            )
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val filePath = step.parameters["file_path"] as? String ?: "未选择文件"
        val operation = step.parameters["operation"] as? String ?: "读取"

        // 提取文件名
        val fileName = try {
            val uri = android.net.Uri.parse(filePath)
            uri.lastPathSegment ?: filePath
        } catch (e: Exception) {
            // 如果不是有效的 URI，尝试从路径中提取文件名
            java.io.File(filePath).name.takeIf { it.isNotEmpty() } ?: filePath
        }

        return "$operation: $fileName"
    }

    /**
     * 当操作类型改变时，清空不需要的值
     */
    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue

        if (updatedParameterId == "operation") {
            when (updatedValue) {
                "读取" -> {
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                }
                "删除" -> {
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                    newParameters.remove("encoding")
                }
                "写入", "追加" -> {
                    newParameters.remove("encoding")
                }
            }
        }
        return newParameters
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val currentStep = context.allSteps.getOrNull(context.currentStepIndex)
            ?: return ExecutionResult.Failure("执行错误", "无法获取当前步骤")

        val filePath = currentStep.parameters["file_path"] as? String
            ?: return ExecutionResult.Failure("执行错误", "未指定文件路径")

        val operation = currentStep.parameters["operation"] as? String ?: "读取"
        val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
        val content = currentStep.parameters["content"] as? String ?: ""
        val overwrite = currentStep.parameters["overwrite"] as? Boolean ?: true
        val bufferSize = (currentStep.parameters["buffer_size"] as? Number)?.toInt() ?: 8192

        return try {
            when (operation) {
                "读取" -> executeRead(context.applicationContext, filePath, encoding, bufferSize, onProgress)
                "写入" -> executeWrite(context.applicationContext, filePath, content, encoding, overwrite, onProgress)
                "追加" -> executeAppend(context.applicationContext, filePath, content, encoding, onProgress)
                "删除" -> executeDelete(context.applicationContext, filePath, onProgress)
                else -> ExecutionResult.Failure("执行错误", "未知的操作类型: $operation")
            }
        } catch (e: SecurityException) {
            ExecutionResult.Failure("权限错误", "没有访问该文件的权限: ${e.message}")
        } catch (e: Exception) {
            ExecutionResult.Failure("执行错误", "文件操作失败: ${e.message}")
        }
    }

    /**
     * 执行文件读取操作
     */
    private suspend fun executeRead(
        context: Context,
        filePath: String,
        encoding: String,
        bufferSize: Int,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在读取文件..."))

        val uri = parseUri(context, filePath) ?: return ExecutionResult.Failure("执行错误", "无效的文件路径")

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, encoding)).use { reader ->
                    val content = StringBuilder()
                    val buffer = CharArray(bufferSize)
                    var bytesRead: Int

                    while (reader.read(buffer).also { bytesRead = it } != -1) {
                        content.append(buffer, 0, bytesRead)
                    }

                    val size = content.length
                    onProgress(ProgressUpdate("读取完成", 100))

                    ExecutionResult.Success(mapOf(
                        "content" to content.toString(),
                        "size" to size
                    ))
                }
            } ?: return ExecutionResult.Failure("执行错误", "无法打开文件")
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure("编码错误", "不支持的编码格式: $encoding")
        }
    }

    /**
     * 执行文件写入操作
     */
    private suspend fun executeWrite(
        context: Context,
        filePath: String,
        content: String,
        encoding: String,
        overwrite: Boolean,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val action = if (overwrite) "写入" else "追加"
        onProgress(ProgressUpdate("正在${action}文件..."))

        val uri = parseUri(context, filePath) ?: return ExecutionResult.Failure("执行错误", "无效的文件路径")

        // 如果是覆盖写入且文件存在，先删除
        if (overwrite) {
            try {
                deleteDocument(context, uri)
            } catch (e: Exception) {
                // 忽略删除错误，可能是新建文件
            }
        }

        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, if (overwrite) "wt" else "at")
                ?: return ExecutionResult.Failure("执行错误", "无法打开文件进行写入")

            outputStream.use { stream ->
                OutputStreamWriter(stream, encoding).use { writer ->
                    writer.write(content)
                }
            }

            val message = if (overwrite) "文件写入完成" else "内容已追加到文件"
            onProgress(ProgressUpdate(message, 100))

            ExecutionResult.Success(mapOf(
                "success" to true,
                "message" to "$message: $filePath"
            ))
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure("编码错误", "不支持的编码格式: $encoding")
        }
    }

    /**
     * 执行文件追加操作
     */
    private suspend fun executeAppend(
        context: Context,
        filePath: String,
        content: String,
        encoding: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        return executeWrite(context, filePath, content, encoding, false, onProgress)
    }

    /**
     * 执行文件删除操作
     */
    private suspend fun executeDelete(
        context: Context,
        filePath: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在删除文件..."))

        val uri = parseUri(context, filePath) ?: return ExecutionResult.Failure("执行错误", "无效的文件路径")

        val deleted = try {
            deleteDocument(context, uri)
        } catch (e: Exception) {
            false
        }

        return if (deleted) {
            onProgress(ProgressUpdate("文件已删除", 100))
            ExecutionResult.Success(mapOf(
                "success" to true,
                "message" to "文件已删除: $filePath"
            ))
        } else {
            // 尝试使用普通删除
            try {
                val file = java.io.File(filePath)
                if (file.exists() && file.delete()) {
                    onProgress(ProgressUpdate("文件已删除", 100))
                    return ExecutionResult.Success(mapOf(
                        "success" to true,
                        "message" to "文件已删除: $filePath"
                    ))
                }
            } catch (e: Exception) {
                // 忽略
            }

            ExecutionResult.Failure("删除失败", "无法删除文件: $filePath")
        }
    }

    /**
     * 解析文件路径为 URI
     */
    private fun parseUri(context: Context, path: String): Uri? {
        return try {
            // 如果已经是 content:// URI
            if (path.startsWith("content://")) {
                path.toUri()
            }
            // 如果是 file:// URI
            else if (path.startsWith("file://")) {
                Uri.fromFile(java.io.File(path.substring(7)))
            }
            // 如果是普通路径，尝试作为文件路径处理
            else {
                Uri.fromFile(java.io.File(path))
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 使用 DocumentsContract 删除文档
     */
    private fun deleteDocument(context: Context, uri: Uri): Boolean {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            // 如果是 content:// URI，尝试使用 DocumentsContract 删除
            if (uri.toString().startsWith("content://")) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
