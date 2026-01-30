// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/SetClipboardModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver // 引入解析器
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
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
        nameStringRes = R.string.module_vflow_system_set_clipboard_name,
        descriptionStringRes = R.string.module_vflow_system_set_clipboard_desc,
        name = "写入剪贴板",  // Fallback
        description = "将指定的文本或图片内容写入系统剪贴板",  // Fallback
        iconRes = R.drawable.rounded_content_copy_24,
        category = "应用与系统"
    )

    // 定义输入参数，现在可以接受文本或图片
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            name = "内容",  // Fallback
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.IMAGE.id),
            supportsRichText = false,
            nameStringRes = R.string.param_vflow_system_set_clipboard_content_name
        )
    )

    // 定义输出参数
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_set_clipboard_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )

        val prefix = context.getString(R.string.summary_vflow_system_set_clipboard_prefix)
        val suffix = context.getString(R.string.summary_vflow_system_set_clipboard_suffix)

        return PillUtil.buildSpannable(context, prefix, contentPill, suffix)
    }

    /**
     * 执行写入剪贴板的逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 逻辑分支判断：如果是变量对象，直接使用；如果是文本，尝试解析
        val rawContent = context.variables["content"]
        val magicContent = context.magicVariables["content"]

        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 优先使用魔法变量对象（例如图片变量）
        val contentObj = magicContent ?: rawContent

        val clip: ClipData? = when (contentObj) {
            is VImage -> {
                try {
                    val imageFile = File(URI(contentObj.uriString))
                    val authority = "${appContext.packageName}.provider"
                    // 使用 FileProvider 生成安全的 content:// URI
                    val safeUri = FileProvider.getUriForFile(appContext, authority, imageFile)
                    // 创建包含 content URI 的 ClipData
                    ClipData.newUri(appContext.contentResolver, "vFlow Image", safeUri).apply {
                        // 授予接收方读取此URI的临时权限
                        appContext.grantUriPermission(
                            "com.android.clipboard",
                            safeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (e: Exception) {
                    // 捕获所有可能的异常，包括 URI 语法错误和文件不存在
                    return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_system_set_clipboard_uri_error),
                        String.format(appContext.getString(R.string.error_vflow_system_set_clipboard_uri_create_failed), e.message ?: "")
                    )
                }
            }
            // 对于其他情况（文本变量、字符串、数字等），统一视为文本并解析
            else -> {
                val resolvedText = VariableResolver.resolve(rawContent?.toString() ?: "", context)
                ClipData.newPlainText("vFlow Text", resolvedText)
            }
        }

        if (clip != null) {
            clipboard.setPrimaryClip(clip)
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_set_clipboard_success)))
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }

        return ExecutionResult.Failure(
            appContext.getString(R.string.error_vflow_system_set_clipboard_unknown_error),
            appContext.getString(R.string.error_vflow_system_set_clipboard_create_failed)
        )
    }
}