package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 读取剪贴板模块（Beta）。
 * 使用 vFlow Core 读取剪贴板内容。
 */
class CoreGetClipboardModule : BaseModule() {

    override val id = "vflow.core.get_clipboard"
    override val metadata = ActionMetadata(
        name = "读取剪贴板",  // Fallback
        nameStringRes = R.string.module_vflow_core_get_clipboard_name,
        description = "使用 vFlow Core 读取剪贴板内容。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_get_clipboard_desc,
        iconRes = R.drawable.rounded_content_paste_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("text", "剪贴板内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_get_clipboard_text_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_get_clipboard_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_get_clipboard))
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 确保 Core 连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已授予 Shizuku 或 Root 权限。"
            )
        }

        // 2. 执行操作
        onProgress(ProgressUpdate("正在使用 vFlow Core 读取剪贴板..."))
        val text = VFlowCoreBridge.getClipboard()

        onProgress(ProgressUpdate("剪贴板内容: $text"))
        return ExecutionResult.Success(mapOf(
            "success" to VBoolean(true),
            "text" to VString(text)
        ))
    }
}
