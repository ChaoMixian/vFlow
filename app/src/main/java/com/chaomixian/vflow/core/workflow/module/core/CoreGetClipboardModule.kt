package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读取剪贴板模块（Beta）。
 * 使用 vFlow Core 读取剪贴板内容。
 */
class CoreGetClipboardModule : BaseModule() {

    override val id = "vflow.core.get_clipboard"
    override val metadata = ActionMetadata(
        name = "读取剪贴板",
        description = "使用 vFlow Core 读取剪贴板内容。",
        iconRes = R.drawable.rounded_content_paste_24,
        category = "Core (Beta)"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("text", "剪贴板内容", TextVariable.TYPE_NAME),
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return PillUtil.buildSpannable(context, "vFlow Core 读取剪贴板")
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
            "success" to BooleanVariable(true),
            "text" to TextVariable(text)
        ))
    }
}
