// 文件: SetClipboardModule.kt
// 描述: 定义了将内容写入系统剪贴板的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.File
import java.net.URI

/**
 * “写入剪贴板”模块。
 * 将指定的文本或图片内容设置到系统剪贴板。
 */
class SetClipboardModule : BaseModule() {

    override val id = "vflow.system.set_clipboard"
    override val metadata = ActionMetadata(
        name = "写入剪贴板",
        description = "将指定的文本或图片内容写入系统剪贴板。",
        iconRes = R.drawable.rounded_content_copy_24, // 使用新图标
        category = "应用与系统"
    )

    // 定义输入参数，现在可以接受文本或图片
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            name = "内容",
            staticType = ParameterType.ANY, // 类型改为ANY以接受多种变量
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, ImageVariable.TYPE_NAME) // 接受文本和图片
        )
    )

    // 定义输出参数
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, "将 ", contentPill, " 写入剪贴板")
    }

    /**
     * 执行写入剪贴板的逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val content = context.magicVariables["content"] ?: context.variables["content"]
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip: ClipData? = when (content) {
            is TextVariable -> {
                ClipData.newPlainText("vFlow Text", content.value)
            }
            is String -> {
                ClipData.newPlainText("vFlow Text", content)
            }
            is ImageVariable -> {
                try {
                    // **核心修复**
                    val imageFile = File(URI(content.uri)) // 将 file URI 字符串转为 File 对象
                    val authority = "${appContext.packageName}.provider"
                    // 使用 FileProvider 生成安全的 content:// URI
                    val safeUri = FileProvider.getUriForFile(appContext, authority, imageFile)
                    // 创建包含 content URI 的 ClipData
                    ClipData.newUri(appContext.contentResolver, "vFlow Image", safeUri).apply {
                        // 授予接收方读取此URI的临时权限
                        appContext.grantUriPermission(
                            "com.android.clipboard", // 系统剪贴板的包名
                            safeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (e: Exception) {
                    // 捕获所有可能的异常，包括 URI 语法错误和文件不存在
                    return ExecutionResult.Failure("文件URI错误", "无法为剪贴板创建安全的图片URI: ${e.message}")
                }
            }
            else -> {
                return ExecutionResult.Failure("参数错误", "要写入剪贴板的内容类型不受支持。")
            }
        }

        if (clip != null) {
            clipboard.setPrimaryClip(clip)
            onProgress(ProgressUpdate("已将内容写入剪贴板"))
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        }

        return ExecutionResult.Failure("未知错误", "无法创建剪贴板数据。")
    }
}