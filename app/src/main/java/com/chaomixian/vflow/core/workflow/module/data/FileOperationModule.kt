// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/module/data/FileOperationModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
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
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
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
            hint = "输入文件路径 或 点击右侧按钮选择",
            pickerType = PickerType.FILE,
            acceptsMagicVariable = false,
            visibility = InputVisibility.notIn("operation", listOf("创建"))
        ),

        // 2. 操作类型（使用 CHIP_GROUP）
        InputDefinition(
            id = "operation",
            nameStringRes = R.string.param_vflow_data_file_operation_operation_name,
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = "读取",
            options = listOf("读取", "写入", "删除", "追加", "创建"),
            inputStyle = InputStyle.CHIP_GROUP
        ),

        // 3. 创建操作 - 目录路径（使用目录选择器）
        InputDefinition(
            id = "directory_path",
            nameStringRes = R.string.param_vflow_data_file_operation_directory_path_name,
            name = "目录路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "输入目录路径 或 点击选择",
            pickerType = PickerType.DIRECTORY,
            acceptsMagicVariable = false,
            visibility = InputVisibility.whenEquals("operation", "创建")
        ),

        // 4. 创建操作 - 文件名（支持富文本、魔法变量、命名变量）
        InputDefinition(
            id = "file_name",
            nameStringRes = R.string.param_vflow_data_file_operation_file_name_name,
            name = "文件名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "输入文件名（含扩展名）",
            supportsRichText = true,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            visibility = InputVisibility.whenEquals("operation", "创建")
        ),

        // 5. 创建操作 - 编码格式
        InputDefinition(
            id = "encoding",
            nameStringRes = R.string.param_vflow_data_file_operation_encoding_name,
            name = "编码格式",
            staticType = ParameterType.ENUM,
            defaultValue = "UTF-8",
            options = listOf("UTF-8", "GBK", "GB2312", "ISO-8859-1"),
            inputStyle = InputStyle.CHIP_GROUP,
            visibility = InputVisibility.whenEquals("operation", "创建")
        ),

        // 6. 写入内容 - 当操作是"写入"或"追加"时显示
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

        // 7. 创建操作 - 文件内容（可选）
        InputDefinition(
            id = "create_content",
            nameStringRes = R.string.param_vflow_data_file_operation_create_content_name,
            name = "文件内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "输入文件内容（可选）",
            supportsRichText = true,
            visibility = InputVisibility.whenEquals("operation", "创建")
        ),

        // 8. 编码格式 - 当操作是"读取"时显示
        InputDefinition(
            id = "encoding_read",
            nameStringRes = R.string.param_vflow_data_file_operation_encoding_name,
            name = "编码格式",
            staticType = ParameterType.ENUM,
            defaultValue = "UTF-8",
            options = listOf("UTF-8", "GBK", "GB2312", "ISO-8859-1"),
            inputStyle = InputStyle.CHIP_GROUP,
            visibility = InputVisibility.whenEquals("operation", "读取")
        ),

        // 9. 高级设置（折叠区域）
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
                OutputDefinition("file_name", "文件名", VTypeRegistry.STRING.id),
                OutputDefinition("mime_type", "MIME类型", VTypeRegistry.STRING.id),
                OutputDefinition("size", "文件大小", VTypeRegistry.NUMBER.id)
            )
            "创建" -> listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("message", "操作信息", VTypeRegistry.STRING.id),
                OutputDefinition("file_path", "文件路径", VTypeRegistry.STRING.id),
                OutputDefinition("file_name", "文件名", VTypeRegistry.STRING.id)
            )
            else -> listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("message", "操作信息", VTypeRegistry.STRING.id)
            )
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val operation = step.parameters["operation"] as? String ?: "读取"

        return when (operation) {
            "创建" -> {
                val fileName = step.parameters["file_name"] as? String ?: "未指定文件名"
                val fileNamePill = PillUtil.Pill(fileName, "file_name")
                PillUtil.buildSpannable(context, "创建: ", fileNamePill)
            }
            else -> {
                val filePath = step.parameters["file_path"] as? String ?: "未选择文件"
                // 提取文件名
                val fileName = try {
                    val uri = filePath.toUri()
                    uri.lastPathSegment ?: filePath
                } catch (e: Exception) {
                    // 如果不是有效的 URI，尝试从路径中提取文件名
                    java.io.File(filePath).name.takeIf { it.isNotEmpty() } ?: filePath
                }
                "$operation: $fileName"
            }
        }
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
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                "删除" -> {
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                    newParameters.remove("encoding")
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                "写入", "追加" -> {
                    newParameters.remove("encoding")
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                "创建" -> {
                    newParameters["file_path"] = ""
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                    newParameters.remove("encoding")
                }
            }
        }
        return newParameters
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val currentStep = context.allSteps.getOrNull(context.currentStepIndex)
            ?: return ExecutionResult.Failure("执行错误", "无法获取当前步骤")

        val operation = currentStep.parameters["operation"] as? String ?: "读取"

        return when (operation) {
            "创建" -> {
                val directoryPath = currentStep.parameters["directory_path"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定目录路径")
                val rawFileName = currentStep.parameters["file_name"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定文件名")
                val fileName = VariableResolver.resolve(rawFileName, context)
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val content = currentStep.parameters["create_content"] as? String ?: ""

                executeCreate(
                    context = context.applicationContext,
                    directoryPath = directoryPath,
                    fileName = fileName,
                    encoding = encoding,
                    content = content,
                    onProgress = onProgress
                )
            }
            "读取" -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定文件路径")
                val encoding = currentStep.parameters["encoding_read"] as? String ?: "UTF-8"
                val bufferSize = (currentStep.parameters["buffer_size"] as? Number)?.toInt() ?: 8192

                executeRead(context.applicationContext, filePath, encoding, bufferSize, onProgress)
            }
            "写入" -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定文件路径")
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val content = currentStep.parameters["content"] as? String ?: ""
                val overwrite = currentStep.parameters["overwrite"] as? Boolean ?: true

                executeWrite(context.applicationContext, filePath, content, encoding, overwrite, onProgress)
            }
            "追加" -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定文件路径")
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val content = currentStep.parameters["content"] as? String ?: ""

                executeAppend(context.applicationContext, filePath, content, encoding, onProgress)
            }
            "删除" -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure("执行错误", "未指定文件路径")

                executeDelete(context.applicationContext, filePath, onProgress)
            }
            else -> ExecutionResult.Failure("执行错误", "未知的操作类型: $operation")
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
            // 提取文件名和MIME类型
            val fileName = getFileName(context, uri) ?: "unknown"
            val mimeType = getMimeType(context, uri) ?: "application/octet-stream"

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
                        "file_name" to fileName,
                        "mime_type" to mimeType,
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
     * 执行创建新文件操作
     */
    private suspend fun executeCreate(
        context: Context,
        directoryPath: String,
        fileName: String,
        encoding: String,
        content: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在创建文件: $fileName..."))

        return try {
            if (isFileUri(directoryPath)) {
                // 使用传统 File API 处理 file:// 路径
                executeCreateWithFileApi(context, directoryPath, fileName, encoding, content, onProgress)
            } else {
                // 使用 DocumentFile API 处理 content:// 路径
                val directoryUri = parseDirectoryUri(context, directoryPath)
                    ?: return ExecutionResult.Failure("执行错误", "无效的目录路径: $directoryPath")

                executeCreateWithDocumentFile(context, directoryUri, fileName, encoding, content, onProgress)
            }
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure("编码错误", "不支持的编码格式: $encoding")
        } catch (e: Exception) {
            ExecutionResult.Failure("创建失败", "创建文件失败: ${e.message}")
        }
    }

    /**
     * 使用 DocumentFile API 创建文件（处理 content:// URI）
     */
    private suspend fun executeCreateWithDocumentFile(
        context: Context,
        directoryUri: Uri,
        fileName: String,
        encoding: String,
        content: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val directoryDoc = DocumentFile.fromTreeUri(context, directoryUri)
            ?: return ExecutionResult.Failure("创建失败", "无法访问目录")

        val newFile = directoryDoc.createFile("*/*", fileName)
            ?: return ExecutionResult.Failure("创建失败", "无法创建文件，可能已存在或名称无效")

        // 写入内容
        if (content.isNotEmpty()) {
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, encoding).use { writer ->
                    writer.write(content)
                }
            } ?: return ExecutionResult.Failure("创建失败", "无法打开文件进行写入")
        }

        onProgress(ProgressUpdate("文件创建成功: $fileName", 100))

        return ExecutionResult.Success(mapOf(
            "success" to true,
            "message" to "文件创建成功: $fileName",
            "file_path" to newFile.uri.toString(),
            "file_name" to fileName
        ))
    }

    /**
     * 使用传统 File API 创建文件（处理 file:// 路径）
     */
    private suspend fun executeCreateWithFileApi(
        context: Context,
        directoryPath: String,
        fileName: String,
        encoding: String,
        content: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 解析 file:// 路径
        val dirPath = directoryPath.removePrefix("file://")
        val directory = java.io.File(dirPath)

        if (!directory.exists()) {
            return ExecutionResult.Failure("创建失败", "目录不存在: $dirPath")
        }

        if (!directory.isDirectory) {
            return ExecutionResult.Failure("创建失败", "路径不是目录: $dirPath")
        }

        val newFile = java.io.File(directory, fileName)

        // 检查文件是否已存在
        if (newFile.exists()) {
            return ExecutionResult.Failure("创建失败", "文件已存在: $fileName")
        }

        // 创建文件并写入内容
        if (content.isNotEmpty()) {
            newFile.writeText(content, java.nio.charset.Charset.forName(encoding))
        } else {
            newFile.createNewFile()
        }

        onProgress(ProgressUpdate("文件创建成功: $fileName", 100))

        return ExecutionResult.Success(mapOf(
            "success" to true,
            "message" to "文件创建成功: $fileName",
            "file_path" to newFile.toURI().toString(),
            "file_name" to fileName
        ))
    }

    /**
     * 解析目录路径为 URI
     * 对于 file:// 路径，返回 null 并在调用处使用 File API
     */
    private fun parseDirectoryUri(context: Context, path: String): Uri? {
        return try {
            when {
                path.startsWith("content://") -> path.toUri()
                path.startsWith("file://") -> null // 需要使用 File API
                else -> Uri.fromFile(java.io.File(path))
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 判断是否为 file:// URI（需要使用传统 File API）
     */
    private fun isFileUri(path: String): Boolean {
        return path.startsWith("file://")
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

    /**
     * 从 URI 获取文件名
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            // 尝试使用 ContentResolver.query 获取显示名称
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        } ?: run {
            // 备选：从 URI 路径提取
            uri.lastPathSegment ?: uri.path?.substringAfterLast('/')
        }
    }

    /**
     * 获取文件的 MIME 类型
     */
    private fun getMimeType(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
    }
}
