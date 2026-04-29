// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/module/data/FileOperationModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.webkit.MimeTypeMap
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 文件操作模块
 * 支持文件的读取、写入、追加、删除操作
 */
class FileOperationModule : BaseModule() {
    companion object {
        private const val OP_READ = "read"
        private const val OP_WRITE = "write"
        private const val OP_DELETE = "delete"
        private const val OP_APPEND = "append"
        private const val OP_CREATE = "create"
    }

    override val id = "vflow.data.file_operation"
    override val metadata = com.chaomixian.vflow.core.module.ActionMetadata(
        nameStringRes = R.string.module_vflow_data_file_operation_name,
        descriptionStringRes = R.string.module_vflow_data_file_operation_desc,
        name = "文件操作",
        description = "对文件进行读取、写入等操作",
        iconRes = R.drawable.rounded_inbox_text_share_24,
        category = "数据",
        categoryId = "data"
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
            visibility = InputVisibility.notIn("operation", listOf(OP_CREATE))
        ),

        // 2. 操作类型（使用 CHIP_GROUP）
        InputDefinition(
            id = "operation",
            nameStringRes = R.string.param_vflow_data_file_operation_operation_name,
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = OP_READ,
            options = listOf(OP_READ, OP_WRITE, OP_DELETE, OP_APPEND, OP_CREATE),
            optionsStringRes = listOf(
                R.string.option_vflow_data_file_operation_operation_read,
                R.string.option_vflow_data_file_operation_operation_write,
                R.string.option_vflow_data_file_operation_operation_delete,
                R.string.option_vflow_data_file_operation_operation_append,
                R.string.option_vflow_data_file_operation_operation_create
            ),
            legacyValueMap = mapOf(
                "读取" to OP_READ,
                "Read" to OP_READ,
                "写入" to OP_WRITE,
                "Write" to OP_WRITE,
                "删除" to OP_DELETE,
                "Delete" to OP_DELETE,
                "追加" to OP_APPEND,
                "Append" to OP_APPEND,
                "创建" to OP_CREATE,
                "Create" to OP_CREATE
            ),
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
            visibility = InputVisibility.whenEquals("operation", OP_CREATE)
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
            visibility = InputVisibility.whenEquals("operation", OP_CREATE)
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
            visibility = InputVisibility.whenEquals("operation", OP_CREATE)
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
            visibility = InputVisibility.`in`("operation", listOf(OP_WRITE, OP_APPEND))
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
            visibility = InputVisibility.whenEquals("operation", OP_CREATE)
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
            visibility = InputVisibility.whenEquals("operation", OP_READ)
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
            visibility = InputVisibility.whenEquals("operation", OP_WRITE)
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
            visibility = InputVisibility.`in`("operation", listOf(OP_READ, OP_WRITE, OP_APPEND))
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val operationInput = getInputs().first { it.id == "operation" }
        val rawOperation = step?.parameters?.get("operation") as? String ?: OP_READ
        val operation = operationInput.normalizeEnumValue(rawOperation) ?: rawOperation

        return when (operation) {
            OP_READ -> listOf(
                OutputDefinition("content", "文件内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_content_name),
                OutputDefinition("file_name", "文件名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_file_name_name),
                OutputDefinition("mime_type", "MIME类型", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_mime_type_name),
                OutputDefinition("size", "文件大小", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_data_file_operation_size_name)
            )
            OP_CREATE -> listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_data_file_operation_success_name),
                OutputDefinition("message", "操作信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_message_name),
                OutputDefinition("file_path", "文件路径", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_file_path_name),
                OutputDefinition("file_name", "文件名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_file_name_name)
            )
            else -> listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_data_file_operation_success_name),
                OutputDefinition("message", "操作信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_file_operation_message_name)
            )
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val operation = step.parameters["operation"] as? String ?: OP_READ

        return when (operation) {
            OP_CREATE -> {
                val fileName = step.parameters["file_name"] as? String ?: context.getString(R.string.summary_vflow_data_file_operation_unspecified_file_name)
                val fileNamePill = PillUtil.Pill(fileName, "file_name")
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_file_operation_create_prefix), fileNamePill)
            }
            else -> {
                val filePath = step.parameters["file_path"] as? String ?: context.getString(R.string.summary_vflow_data_file_operation_no_file_selected)
                val normalizedPath = if (filePath.startsWith("file://")) {
                    filePath.removePrefix("file://")
                } else {
                    filePath
                }
                val fileName = File(normalizedPath).name.takeIf { it.isNotEmpty() } ?: filePath
                "${getOperationDisplayName(context, operation)}: $fileName"
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
                OP_READ -> {
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                OP_DELETE -> {
                    newParameters["content"] = ""
                    newParameters.remove("overwrite")
                    newParameters.remove("encoding")
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                OP_WRITE, OP_APPEND -> {
                    newParameters.remove("encoding")
                    newParameters.remove("directory_path")
                    newParameters.remove("file_name")
                    newParameters.remove("create_content")
                }
                OP_CREATE -> {
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
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_file_operation_execution_error),
                appContext.getString(R.string.error_vflow_data_file_operation_current_step_missing)
            )

        val operationInput = getInputs().first { it.id == "operation" }
        val rawOperation = currentStep.parameters["operation"] as? String ?: OP_READ
        val operation = operationInput.normalizeEnumValue(rawOperation) ?: rawOperation

        return when (operation) {
            OP_CREATE -> {
                val directoryPath = currentStep.parameters["directory_path"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_directory_missing))
                val rawFileName = currentStep.parameters["file_name"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_file_name_missing))
                val fileName = VariableResolver.resolve(rawFileName, context)
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val rawContent = currentStep.parameters["create_content"] as? String ?: ""
                val content = VariableResolver.resolve(rawContent, context)

                executeCreate(
                    context = context.applicationContext,
                    directoryPath = directoryPath,
                    fileName = fileName,
                    encoding = encoding,
                    content = content,
                    onProgress = onProgress
                )
            }
            OP_READ -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_file_path_missing))
                val encoding = currentStep.parameters["encoding_read"] as? String ?: "UTF-8"
                val bufferSize = (currentStep.parameters["buffer_size"] as? Number)?.toInt() ?: 8192

                executeRead(context.applicationContext, filePath, encoding, bufferSize, onProgress)
            }
            OP_WRITE -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_file_path_missing))
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val rawContent = currentStep.parameters["content"] as? String ?: ""
                val content = VariableResolver.resolve(rawContent, context)
                val overwrite = currentStep.parameters["overwrite"] as? Boolean ?: true

                executeWrite(context.applicationContext, filePath, content, encoding, overwrite, onProgress)
            }
            OP_APPEND -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_file_path_missing))
                val encoding = currentStep.parameters["encoding"] as? String ?: "UTF-8"
                val rawContent = currentStep.parameters["content"] as? String ?: ""
                val content = VariableResolver.resolve(rawContent, context)

                executeAppend(context.applicationContext, filePath, content, encoding, onProgress)
            }
            OP_DELETE -> {
                val filePath = currentStep.parameters["file_path"] as? String
                    ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_file_operation_execution_error), appContext.getString(R.string.error_vflow_data_file_operation_file_path_missing))

                executeDelete(context.applicationContext, filePath, onProgress)
            }
            else -> ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_file_operation_execution_error),
                appContext.getString(R.string.error_vflow_data_file_operation_unknown_operation, operation)
            )
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
        onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_reading)))

        val file = resolveLocalFile(filePath)
            ?: return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_execution_error), context.getString(R.string.error_vflow_data_file_operation_invalid_file_path))
        if (!file.exists() || !file.isFile) {
            return ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_file_operation_execution_error),
                context.getString(R.string.error_vflow_data_file_operation_open_failed)
            )
        }

        return try {
            val fileName = file.name.ifBlank { "unknown" }
            val mimeType = getMimeType(file)

            file.inputStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, encoding)).use { reader ->
                    val content = StringBuilder()
                    val buffer = CharArray(bufferSize)
                    var bytesRead: Int

                    while (reader.read(buffer).also { bytesRead = it } != -1) {
                        content.append(buffer, 0, bytesRead)
                    }

                    val size = content.length
                    onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_read_complete), 100))

                    ExecutionResult.Success(mapOf(
                        "content" to content.toString(),
                        "file_name" to fileName,
                        "mime_type" to mimeType,
                        "size" to size
                    ))
                }
            }
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_encoding_error), context.getString(R.string.error_vflow_data_file_operation_unsupported_encoding, encoding))
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
        val action = if (overwrite) OP_WRITE else OP_APPEND
        onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_writing, getOperationDisplayName(context, action))))

        val file = resolveLocalFile(filePath)
            ?: return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_execution_error), context.getString(R.string.error_vflow_data_file_operation_invalid_file_path))

        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            return ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_file_operation_execution_error),
                context.getString(R.string.error_vflow_data_file_operation_open_for_write_failed)
            )
        }

        return try {
            FileOutputStream(file, !overwrite).use { stream ->
                OutputStreamWriter(stream, encoding).use { writer ->
                    writer.write(content)
                }
            }

            val message = if (overwrite) {
                context.getString(R.string.progress_vflow_data_file_operation_write_complete)
            } else {
                context.getString(R.string.progress_vflow_data_file_operation_append_complete)
            }
            onProgress(ProgressUpdate(message, 100))

            ExecutionResult.Success(mapOf(
                "success" to true,
                "message" to "$message: $filePath"
            ))
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_encoding_error), context.getString(R.string.error_vflow_data_file_operation_unsupported_encoding, encoding))
        }
    }

    private fun getOperationDisplayName(context: Context, operation: String): String {
        return when (operation) {
            OP_WRITE -> context.getString(R.string.option_vflow_data_file_operation_operation_write)
            OP_DELETE -> context.getString(R.string.option_vflow_data_file_operation_operation_delete)
            OP_APPEND -> context.getString(R.string.option_vflow_data_file_operation_operation_append)
            OP_CREATE -> context.getString(R.string.option_vflow_data_file_operation_operation_create)
            else -> context.getString(R.string.option_vflow_data_file_operation_operation_read)
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
        onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_deleting)))

        val file = resolveLocalFile(filePath)
            ?: return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_execution_error), context.getString(R.string.error_vflow_data_file_operation_invalid_file_path))

        val deleted = try {
            file.exists() && file.delete()
        } catch (e: Exception) {
            false
        }

        return if (deleted) {
            onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_delete_complete), 100))
            ExecutionResult.Success(mapOf(
                "success" to true,
                "message" to context.getString(R.string.message_vflow_data_file_operation_deleted, filePath)
            ))
        } else {
            ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_delete_failed), context.getString(R.string.error_vflow_data_file_operation_cannot_delete, filePath))
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
        onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_creating, fileName)))

        return try {
            executeCreateWithFileApi(context, directoryPath, fileName, encoding, content, onProgress)
        } catch (e: java.io.UnsupportedEncodingException) {
            ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_encoding_error), context.getString(R.string.error_vflow_data_file_operation_unsupported_encoding, encoding))
        } catch (e: Exception) {
            ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_create_failed), context.getString(R.string.error_vflow_data_file_operation_create_failed_with_reason, e.message ?: ""))
        }
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
        val directory = resolveLocalFile(directoryPath)
            ?: return ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_file_operation_execution_error),
                context.getString(R.string.error_vflow_data_file_operation_invalid_directory_path, directoryPath)
            )
        val dirPath = directory.absolutePath

        if (!directory.exists()) {
            return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_create_failed), context.getString(R.string.error_vflow_data_file_operation_directory_not_exists, dirPath))
        }

        if (!directory.isDirectory) {
            return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_create_failed), context.getString(R.string.error_vflow_data_file_operation_path_not_directory, dirPath))
        }

        val newFile = java.io.File(directory, fileName)

        // 检查文件是否已存在
        if (newFile.exists()) {
            return ExecutionResult.Failure(context.getString(R.string.error_vflow_data_file_operation_create_failed), context.getString(R.string.error_vflow_data_file_operation_file_exists, fileName))
        }

        // 创建文件并写入内容
        if (content.isNotEmpty()) {
            newFile.writeText(content, java.nio.charset.Charset.forName(encoding))
        } else {
            newFile.createNewFile()
        }

        onProgress(ProgressUpdate(context.getString(R.string.progress_vflow_data_file_operation_create_complete, fileName), 100))

        return ExecutionResult.Success(mapOf(
            "success" to true,
            "message" to context.getString(R.string.message_vflow_data_file_operation_created, fileName),
            "file_path" to newFile.absolutePath,
            "file_name" to fileName
        ))
    }

    private fun resolveLocalFile(path: String): File? {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("content://")) return null

        val normalizedPath = if (trimmed.startsWith("file://")) {
            trimmed.removePrefix("file://")
        } else {
            trimmed
        }
        if (!normalizedPath.startsWith("/")) return null

        return try {
            File(normalizedPath)
        } catch (e: Exception) {
            null
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
