// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/BluetoothTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BluetoothTriggerModule : BaseModule() {
    override val id = "vflow.trigger.bluetooth"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_bluetooth_name,
        descriptionStringRes = R.string.module_vflow_trigger_bluetooth_desc,
        name = "蓝牙触发器",  // Fallback
        description = "当蓝牙状态或设备连接变化时触发",  // Fallback
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.BLUETOOTH)
    override val uiProvider: ModuleUIProvider = BluetoothTriggerUIProvider()

    private val triggerTypeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_bluetooth_type_state),
            appContext.getString(R.string.option_vflow_trigger_bluetooth_type_device)
        )
    }
    private val stateEventOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_bluetooth_state_on),
            appContext.getString(R.string.option_vflow_trigger_bluetooth_state_off)
        )
    }
    private val deviceEventOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_bluetooth_device_connected),
            appContext.getString(R.string.option_vflow_trigger_bluetooth_device_disconnected)
        )
    }

    // 定义所有可能的输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("trigger_type", "触发类型", ParameterType.ENUM, triggerTypeOptions[0], options = triggerTypeOptions, nameStringRes = R.string.param_vflow_trigger_bluetooth_trigger_type_name),
        // 蓝牙状态
        InputDefinition("state_event", "事件", ParameterType.ENUM, stateEventOptions[0], options = stateEventOptions, nameStringRes = R.string.param_vflow_trigger_bluetooth_state_event_name),
        // 设备连接
        InputDefinition("device_event", "事件", ParameterType.ENUM, deviceEventOptions[0], options = deviceEventOptions, nameStringRes = R.string.param_vflow_trigger_bluetooth_device_event_name),
        InputDefinition("device_address", "设备地址", ParameterType.STRING, isHidden = true, nameStringRes = R.string.param_vflow_trigger_bluetooth_device_address_name),
        InputDefinition("device_name", "设备名称", ParameterType.STRING, defaultValue = "任何设备", nameStringRes = R.string.param_vflow_trigger_bluetooth_device_name_name)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: triggerTypeOptions[0]
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == triggerTypeOptions[0]) {
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
            OutputDefinition("device_name", "设备名称", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_bluetooth_device_name_name),
            OutputDefinition("device_address", "设备地址", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_bluetooth_device_address_name)
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val triggerType = step.parameters["trigger_type"] as? String ?: triggerTypeOptions[0]
        val statePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_state_prefix)
        val devicePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_device_prefix)

        return if (triggerType == triggerTypeOptions[0]) {
            val event = step.parameters["state_event"] as? String ?: stateEventOptions[0]
            val eventPill = PillUtil.Pill(event, "state_event", isModuleOption = true)
            PillUtil.buildSpannable(context, "$statePrefix ", eventPill)
        } else {
            val event = step.parameters["device_event"] as? String ?: deviceEventOptions[0]
            val deviceName = step.parameters["device_name"] as? String ?: "任何设备"
            val eventPill = PillUtil.Pill(event, "device_event", isModuleOption = true)
            val devicePill = PillUtil.Pill(deviceName, "device_name")
            PillUtil.buildSpannable(context, "$devicePrefix ", eventPill, " ", devicePill)
        }
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("蓝牙事件已触发"))
        val deviceName = context.triggerData as? VDictionary
        val outputs = mapOf(
            "device_name" to (deviceName?.raw?.get("name") as? VString ?: VString("")),
            "device_address" to (deviceName?.raw?.get("address") as? VString ?: VString(""))
        )
        return ExecutionResult.Success(outputs)
    }
}