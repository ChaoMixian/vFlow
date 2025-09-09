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
import com.chaomixian.vflow.services.ShizukuManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BluetoothModule : BaseModule() {

    override val id = "vflow.system.bluetooth"
    override val metadata = ActionMetadata(
        name = "蓝牙设置",
        description = "开启、关闭或切换蓝牙状态。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "应用与系统"
    )

    // [修改] 同时需要蓝牙权限和可能的 Shizuku 权限
    override val requiredPermissions = listOf(PermissionManager.BLUETOOTH, PermissionManager.SHIZUKU)

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
        val statePill = PillUtil.createPillFromParam(
            step.parameters["state"],
            getInputs().find { it.id == "state" },
            isModuleOption = true
        )
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

        // 计算出目标状态
        val targetState = when (state) {
            "开启" -> true
            "关闭" -> false
            "切换" -> !bluetoothAdapter.isEnabled
            else -> return ExecutionResult.Failure("参数错误", "无效的状态: $state")
        }

        // 智能判断执行方式
        if (ShizukuManager.isShizukuActive(appContext)) {
            // 1. Shizuku 可用，使用 Shell 命令
            onProgress(ProgressUpdate("正在通过 Shizuku 执行..."))
            val command = if (targetState) "svc bluetooth enable" else "svc bluetooth disable"
            val result = ShizukuManager.execShellCommand(appContext, command)

            return if (result.startsWith("Error:")) {
                ExecutionResult.Failure("Shizuku 执行失败", result)
            } else {
                ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
            }
        } else {
            // 2. Shizuku 不可用，回退到原有 API 方式
            val success = if (targetState) {
                bluetoothAdapter.enable()
            } else {
                bluetoothAdapter.disable()
            }
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(success)))
        }
    }
}