// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/BluetoothTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BluetoothTriggerModule : BaseModule() {
    override val id = "vflow.trigger.bluetooth"
    override val metadata = ActionMetadata(
        name = "蓝牙触发器",
        description = "当蓝牙状态或设备连接变化时触发。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.BLUETOOTH)
    override val uiProvider: ModuleUIProvider = BluetoothTriggerUIProvider()

    // 定义所有可能的输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("trigger_type", "触发类型", ParameterType.ENUM, "蓝牙状态", options = listOf("蓝牙状态", "设备连接")),
        // 蓝牙状态
        InputDefinition("state_event", "事件", ParameterType.ENUM, "开启时", options = listOf("开启时", "关闭时")),
        // 设备连接
        InputDefinition("device_event", "事件", ParameterType.ENUM, "连接时", options = listOf("连接时", "断开时")),
        InputDefinition("device_address", "设备地址", ParameterType.STRING, isHidden = true),
        InputDefinition("device_name", "设备名称", ParameterType.STRING, defaultValue = "任何设备")
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: "蓝牙状态"
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == "蓝牙状态") {
            dynamicInputs.add(all.first { it.id == "state_event" })
        } else {
            dynamicInputs.add(all.first { it.id == "device_event" })
            dynamicInputs.add(all.first { it.id == "device_name" })
            dynamicInputs.add(all.first { it.id == "device_address" })
        }
        return dynamicInputs
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        return listOf(
            OutputDefinition("device_name", "设备名称", TextVariable.TYPE_NAME),
            OutputDefinition("device_address", "设备地址", TextVariable.TYPE_NAME)
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val triggerType = step.parameters["trigger_type"] as? String ?: "蓝牙状态"

        return if (triggerType == "蓝牙状态") {
            val event = step.parameters["state_event"] as? String ?: "开启时"
            val eventPill = PillUtil.Pill(event, "state_event", isModuleOption = true)
            PillUtil.buildSpannable(context, "当蓝牙 ", eventPill)
        } else {
            val event = step.parameters["device_event"] as? String ?: "连接时"
            val deviceName = step.parameters["device_name"] as? String ?: "任何设备"
            val eventPill = PillUtil.Pill(event, "device_event", isModuleOption = true)
            val devicePill = PillUtil.Pill(deviceName, "device_name")
            PillUtil.buildSpannable(context, "当 ", eventPill, " ", devicePill)
        }
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("蓝牙事件已触发"))
        val deviceName = context.triggerData as? DictionaryVariable
        val outputs = mapOf(
            "device_name" to (deviceName?.value?.get("name") ?: TextVariable("")),
            "device_address" to (deviceName?.value?.get("address") ?: TextVariable(""))
        )
        return ExecutionResult.Success(outputs)
    }
}