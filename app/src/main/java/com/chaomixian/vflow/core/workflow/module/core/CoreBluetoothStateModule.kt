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

/**
 * 读取蓝牙状态模块（Beta）。
 * 使用 vFlow Core 读取当前蓝牙开关状态。
 */
class CoreBluetoothStateModule : BaseModule() {

    override val id = "vflow.core.bluetooth_state"
    override val metadata = ActionMetadata(
        name = "读取蓝牙状态",
        description = "使用 vFlow Core 读取当前蓝牙开关状态。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "Core (Beta)"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "蓝牙状态", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "读取蓝牙状态"
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

        onProgress(ProgressUpdate("正在使用 vFlow Core 读取蓝牙状态..."))

        // 2. 执行操作
        val enabled = VFlowCoreBridge.isBluetoothEnabled()

        onProgress(ProgressUpdate("蓝牙状态: ${if (enabled) "已开启" else "已关闭"}"))
        return ExecutionResult.Success(mapOf("enabled" to VBoolean(enabled)))
    }
}
