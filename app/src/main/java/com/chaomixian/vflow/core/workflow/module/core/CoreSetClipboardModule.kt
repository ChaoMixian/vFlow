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
 * 设置剪贴板模块（Beta）。
 * 使用 vFlow Core 设置剪贴板内容。
 */
class CoreSetClipboardModule : BaseModule() {

    override val id = "vflow.core.set_clipboard"
    override val metadata = ActionMetadata(
        name = "设置剪贴板",
        description = "使用 vFlow Core 设置剪贴板内容。",
        iconRes = R.drawable.rounded_content_copy_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            name = "文本内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("text", "设置的文本内容", VTypeRegistry.STRING.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            inputs.find { it.id == "text" }
        )
        return PillUtil.buildSpannable(
            context,
            "vFlow Core 设置剪贴板：",
            textPill
        )
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

        // 2. 获取参数
        val step = context.allSteps[context.currentStepIndex]
        val text = (context.magicVariables["text"]
            ?: context.variables["text"])?.toString()

        if (text == null) {
            return ExecutionResult.Failure("参数错误", "文本内容不能为空")
        }

        // 3. 执行操作
        onProgress(ProgressUpdate("正在使用 vFlow Core 设置剪贴板..."))
        val success = VFlowCoreBridge.setClipboard(text)

        return if (success) {
            onProgress(ProgressUpdate("剪贴板设置成功"))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "text" to VString(text)
            ))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 剪贴板设置失败")
        }
    }
}
