// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/BluetoothModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BluetoothModule : BaseModule() {
    companion object {
        private const val STATE_ON = "on"
        private const val STATE_OFF = "off"
        private const val STATE_TOGGLE = "toggle"
    }

    override val id = "vflow.system.bluetooth"
    override val metadata = ActionMetadata(
        name = "蓝牙设置",  // Fallback
        nameStringRes = R.string.module_vflow_system_bluetooth_name,
        description = "开启、关闭或切换蓝牙状态。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_bluetooth_desc,
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Turn Bluetooth on, off, or toggle it.",
        workflowStepDescription = "Change Bluetooth state.",
        requiredInputIds = setOf("state"),
    )

    // 动态获取 Shell 权限 + 蓝牙权限
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.BLUETOOTH) + ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    private val stateOptions = listOf(STATE_ON, STATE_OFF, STATE_TOGGLE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "state",
            name = "状态",
            staticType = ParameterType.ENUM,
            defaultValue = STATE_TOGGLE,
            options = stateOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_bluetooth_state_name,
            optionsStringRes = listOf(
                R.string.option_vflow_system_bluetooth_state_on,
                R.string.option_vflow_system_bluetooth_state_off,
                R.string.option_vflow_system_bluetooth_state_toggle
            ),
            legacyValueMap = mapOf(
                "开启" to STATE_ON,
                "On" to STATE_ON,
                "关闭" to STATE_OFF,
                "Off" to STATE_OFF,
                "切换" to STATE_TOGGLE,
                "Toggle" to STATE_TOGGLE
            )
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_system_bluetooth_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val statePill = PillUtil.createPillFromParam(
            step.parameters["state"],
            getInputs().find { it.id == "state" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, statePill, context.getString(R.string.summary_vflow_system_bluetooth_suffix))
    }

    @SuppressLint("MissingPermission")
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val stateInput = getInputs().first { it.id == "state" }
        val rawState = context.getVariableAsString("state", STATE_TOGGLE)
        val state = stateInput.normalizeEnumValue(rawState) ?: rawState

        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            return ExecutionResult.Failure("硬件不支持", "该设备不支持蓝牙。")
        }

        onProgress(ProgressUpdate("正在尝试 $state 蓝牙..."))

        // 计算出目标状态
        val targetState = when (state) {
            STATE_ON -> true
            STATE_OFF -> false
            STATE_TOGGLE -> !bluetoothAdapter.isEnabled
            else -> return ExecutionResult.Failure("参数错误", "无效的状态: $state")
        }

        // 1. 优先尝试 Shell
        onProgress(ProgressUpdate("尝试通过 Shell 执行..."))
        val command = if (targetState) "svc bluetooth enable" else "svc bluetooth disable"
        val shellResult = ShellManager.execShellCommand(appContext, command, ShellManager.ShellMode.AUTO)

        if (!shellResult.startsWith("Error")) {
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }

        DebugLogger.w("BluetoothModule", "Shell 执行失败: $shellResult，尝试回退到标准 API。")

        // 2. 回退到标准 API
        val success = if (targetState) {
            bluetoothAdapter.enable()
        } else {
            bluetoothAdapter.disable()
        }
        return ExecutionResult.Success(mapOf("success" to VBoolean(success)))
    }
}
