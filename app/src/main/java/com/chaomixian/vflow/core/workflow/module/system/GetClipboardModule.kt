// 文件: GetClipboardModule.kt
// 描述: 定义了从系统剪贴板读取内容的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * “读取剪贴板”模块。
 * 获取系统剪贴板的当前文本或图片内容。
 */
class GetClipboardModule : BaseModule() {

    override val id = "vflow.system.get_clipboard"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_get_clipboard_name,
        descriptionStringRes = R.string.module_vflow_system_get_clipboard_desc,
        name = "读取剪贴板",  // Fallback
        description = "获取系统剪贴板的当前内容（文本或图片）",  // Fallback
        iconRes = R.drawable.rounded_content_paste_24,
        category = "应用与系统"
    )

    // 此模块没有输入参数
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 输出文本和图片两种变量
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "text_content",
            name = "剪贴板文本",  // Fallback
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_get_clipboard_text_content_name
        ),
        OutputDefinition(
            id = "image_content",
            name = "剪贴板图片",  // Fallback
            typeName = VTypeRegistry.IMAGE.id,
            nameStringRes = R.string.output_vflow_system_get_clipboard_image_content_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_system_get_clipboard)
    }

    /**
     * 执行读取剪贴板的逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val outputs = mutableMapOf<String, Any?>()

        if (!clipboard.hasPrimaryClip()) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_get_clipboard_empty)))
            outputs["text_content"] = VString("")
            return ExecutionResult.Success(outputs)
        }

        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            // 优先检查文本
            if (clipData.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                val text = item.text?.toString() ?: ""
                outputs["text_content"] = VString(text)

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_get_clipboard_text_read)))
            }

            // 检查URI（可能是图片）
            if (item.uri != null) {
                val mimeType = context.applicationContext.contentResolver.getType(item.uri)
                if (mimeType?.startsWith("image/") == true) {
                    // 为了持久化访问，将图片复制到应用缓存
                    val tempFile = File(context.workDir, "clipboard_${UUID.randomUUID()}.tmp")
                    try {
                        context.applicationContext.contentResolver.openInputStream(item.uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        outputs["image_content"] = VImage(tempFile.toURI().toString())

                        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_get_clipboard_image_read)))
                    } catch (e: Exception) {
                        onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_system_get_clipboard_image_failed), e.message ?: "")))
                    }
                }
            }
        }

        // 确保即使没读到文本，也有默认的空文本输出
        if (!outputs.containsKey("text_content")) {
            outputs["text_content"] = VString("")
        }

        return ExecutionResult.Success(outputs)
    }
}