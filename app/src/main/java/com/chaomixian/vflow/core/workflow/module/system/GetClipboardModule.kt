// 文件: GetClipboardModule.kt
// 描述: 定义了从系统剪贴板读取内容的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
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
        name = "读取剪贴板",
        description = "获取系统剪贴板的当前内容（文本或图片）。",
        iconRes = R.drawable.rounded_content_paste_24, // 使用新图标
        category = "应用与系统"
    )

    // 此模块没有输入参数
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 输出文本和图片两种变量
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("text_content", "剪贴板文本", TextVariable.TYPE_NAME),
        OutputDefinition("image_content", "剪贴板图片", ImageVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "读取剪贴板内容"
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
            onProgress(ProgressUpdate("剪贴板为空"))
            outputs["text_content"] = TextVariable("")
            return ExecutionResult.Success(outputs)
        }

        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            // 优先检查文本
            if (clipData.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                val text = item.text?.toString() ?: ""
                outputs["text_content"] = TextVariable(text)
                onProgress(ProgressUpdate("已读取剪贴板文本内容"))
            }

            // 检查URI（可能是图片）
            if (item.uri != null) {
                val mimeType = context.applicationContext.contentResolver.getType(item.uri)
                if (mimeType?.startsWith("image/") == true) {
                    // 为了持久化访问，将图片复制到应用缓存
                    val tempFile = File(context.applicationContext.cacheDir, "clipboard_${UUID.randomUUID()}.tmp")
                    try {
                        context.applicationContext.contentResolver.openInputStream(item.uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        outputs["image_content"] = ImageVariable(tempFile.toURI().toString())
                        onProgress(ProgressUpdate("已读取剪贴板图片内容"))
                    } catch (e: Exception) {
                        onProgress(ProgressUpdate("读取剪贴板图片失败: ${e.message}"))
                    }
                }
            }
        }

        // 确保即使没读到文本，也有默认的空文本输出
        if (!outputs.containsKey("text_content")) {
            outputs["text_content"] = TextVariable("")
        }

        return ExecutionResult.Success(outputs)
    }
}