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
 * 读取蓝牙状态模块（Beta）。
 * 使用 vFlow Core 读取当前蓝牙开关状态。
 */
class CoreBluetoothStateModule : BaseModule() {

    override val id = "vflow.core.bluetooth_state"
    override val metadata = ActionMetadata(
        name = "读取蓝牙状态",
        description = "使用 vFlow Core 读取当前蓝牙开关状态。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "Core (Beta)",
        nameStringRes = R.string.module_vflow_core_bluetooth_state_name,
        descriptionStringRes = R.string.module_vflow_core_bluetooth_state_desc
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "蓝牙状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_bluetooth_state_enabled_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_core_bluetooth_state)
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

        onProgress(ProgressUpdate(context.applicationContext.getString(R.string.msg_vflow_core_bluetooth_state_reading)))

        // 2. 执行操作
        val enabled = VFlowCoreBridge.isBluetoothEnabled()

        val statusMsg = if (enabled) "已开启" else "已关闭"
        onProgress(ProgressUpdate("蓝牙状态: $statusMsg"))
        return ExecutionResult.Success(mapOf("enabled" to VBoolean(enabled)))
    }
}
