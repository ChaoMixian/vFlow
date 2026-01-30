package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 唤醒屏幕模块（Beta）。
 * 使用 vFlow Core 唤醒设备屏幕。
 */
class CoreWakeScreenModule : BaseModule() {

    override val id = "vflow.core.wake_screen"
    override val metadata = ActionMetadata(
        name = "唤醒屏幕",  // Fallback
        nameStringRes = R.string.module_vflow_core_wake_screen_name,
        description = "使用 vFlow Core 唤醒设备屏幕。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_wake_screen_desc,
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_wake_screen_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_core_wake_screen)
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

        onProgress(ProgressUpdate("正在使用 vFlow Core 唤醒屏幕..."))

        // 2. 执行操作
        val success = VFlowCoreBridge.wakeUp()

        return if (success) {
            onProgress(ProgressUpdate("屏幕唤醒成功"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 屏幕唤醒失败")
        }
    }
}
