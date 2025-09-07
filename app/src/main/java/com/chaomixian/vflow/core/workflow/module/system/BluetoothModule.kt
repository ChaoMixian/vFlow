// 文件: BluetoothModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BluetoothModule : BaseModule() {

    override val id = "vflow.system.bluetooth"
    override val metadata = ActionMetadata(
        name = "蓝牙设置",
        description = "开启、关闭或切换蓝牙状态。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.BLUETOOTH)

    private val stateOptions = listOf("开启", "关闭", "切换")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "state",
            name = "状态",
            staticType = ParameterType.ENUM,
            defaultValue = "切换",
            options = stateOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val state = step.parameters["state"] as? String ?: "切换"
        val statePill = PillUtil.Pill(state, false, "state", true)
        return PillUtil.buildSpannable(context, statePill, " 蓝牙")
    }

    @SuppressLint("MissingPermission")
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val state = context.variables["state"] as? String ?: "切换"
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            return ExecutionResult.Failure("硬件不支持", "该设备不支持蓝牙。")
        }

        onProgress(ProgressUpdate("正在尝试 $state 蓝牙..."))

        val targetState = when (state) {
            "开启" -> true
            "关闭" -> false
            "切换" -> !bluetoothAdapter.isEnabled
            else -> return ExecutionResult.Failure("参数错误", "无效的状态: $state")
        }

        val success = if (targetState) {
            bluetoothAdapter.enable()
        } else {
            bluetoothAdapter.disable()
        }

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(success)))
    }
}