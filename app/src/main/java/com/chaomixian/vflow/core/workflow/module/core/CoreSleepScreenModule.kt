package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 关闭屏幕模块（Beta）。
 * 使用 vFlow Core 关闭设备屏幕。
 */
class CoreSleepScreenModule : BaseModule() {

    override val id = "vflow.core.sleep_screen"
    override val metadata = ActionMetadata(
        name = "关闭屏幕",
        description = "使用 vFlow Core 关闭设备屏幕。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "Core (Beta)"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "关闭屏幕"
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

        onProgress(ProgressUpdate("正在使用 vFlow Core 关闭屏幕..."))

        // 2. 执行操作
        val success = VFlowCoreBridge.goToSleep()

        return if (success) {
            onProgress(ProgressUpdate("屏幕关闭成功"))
            ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 屏幕关闭失败")
        }
    }
}
