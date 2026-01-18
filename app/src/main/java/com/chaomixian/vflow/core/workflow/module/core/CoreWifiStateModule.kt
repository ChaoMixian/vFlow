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
 * 读取WiFi状态模块（Beta）。
 * 使用 vFlow Core 读取当前WiFi开关状态。
 */
class CoreWifiStateModule : BaseModule() {

    override val id = "vflow.core.wifi_state"
    override val metadata = ActionMetadata(
        name = "读取WiFi状态",
        description = "使用 vFlow Core 读取当前WiFi开关状态。",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "Core (Beta)"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "WiFi状态", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "读取WiFi状态"
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

        onProgress(ProgressUpdate("正在使用 vFlow Core 读取WiFi状态..."))

        // 2. 执行操作
        val enabled = VFlowCoreBridge.isWifiEnabled()

        onProgress(ProgressUpdate("WiFi状态: ${if (enabled) "已开启" else "已关闭"}"))
        return ExecutionResult.Success(mapOf("enabled" to BooleanVariable(enabled)))
    }
}
