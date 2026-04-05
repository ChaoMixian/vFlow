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
    companion object {
        const val TRIGGER_TYPE_STATE = "state"
        const val TRIGGER_TYPE_DEVICE = "device"
        const val STATE_EVENT_ON = "on"
        const val STATE_EVENT_OFF = "off"
        const val DEVICE_EVENT_CONNECTED = "connected"
        const val DEVICE_EVENT_DISCONNECTED = "disconnected"
    }
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

    private val triggerTypeOptions by lazy { listOf(TRIGGER_TYPE_STATE, TRIGGER_TYPE_DEVICE) }
    private val stateEventOptions by lazy { listOf(STATE_EVENT_ON, STATE_EVENT_OFF) }
    private val deviceEventOptions by lazy { listOf(DEVICE_EVENT_CONNECTED, DEVICE_EVENT_DISCONNECTED) }

    // 定义所有可能的输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "trigger_type",
            "触发类型",
            ParameterType.ENUM,
            TRIGGER_TYPE_STATE,
            options = triggerTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_bluetooth_type_state,
                R.string.option_vflow_trigger_bluetooth_type_device
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_bluetooth_type_state) to TRIGGER_TYPE_STATE,
                appContext.getString(R.string.option_vflow_trigger_bluetooth_type_device) to TRIGGER_TYPE_DEVICE
            ),
            nameStringRes = R.string.param_vflow_trigger_bluetooth_trigger_type_name
        ),
        // 蓝牙状态
        InputDefinition(
            "state_event",
            "事件",
            ParameterType.ENUM,
            STATE_EVENT_ON,
            options = stateEventOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_bluetooth_state_on,
                R.string.option_vflow_trigger_bluetooth_state_off
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_bluetooth_state_on) to STATE_EVENT_ON,
                appContext.getString(R.string.option_vflow_trigger_bluetooth_state_off) to STATE_EVENT_OFF
            ),
            nameStringRes = R.string.param_vflow_trigger_bluetooth_state_event_name
        ),
        // 设备连接
        InputDefinition(
            "device_event",
            "事件",
            ParameterType.ENUM,
            DEVICE_EVENT_CONNECTED,
            options = deviceEventOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_bluetooth_device_connected,
                R.string.option_vflow_trigger_bluetooth_device_disconnected
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_bluetooth_device_connected) to DEVICE_EVENT_CONNECTED,
                appContext.getString(R.string.option_vflow_trigger_bluetooth_device_disconnected) to DEVICE_EVENT_DISCONNECTED
            ),
            nameStringRes = R.string.param_vflow_trigger_bluetooth_device_event_name
        ),
        InputDefinition("device_address", "设备地址", ParameterType.STRING, isHidden = true, nameStringRes = R.string.param_vflow_trigger_bluetooth_device_address_name),
        InputDefinition("device_name", "设备名称", ParameterType.STRING, defaultValue = "任何设备", nameStringRes = R.string.param_vflow_trigger_bluetooth_device_name_name)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: TRIGGER_TYPE_STATE
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == TRIGGER_TYPE_STATE) {
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
        val triggerType = step.parameters["trigger_type"] as? String ?: TRIGGER_TYPE_STATE
        val statePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_state_prefix)
        val devicePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_device_prefix)

        return if (triggerType == TRIGGER_TYPE_STATE) {
            val event = step.parameters["state_event"] as? String ?: STATE_EVENT_ON
            val eventPill = PillUtil.createPillFromParam(event, getInputs().find { it.id == "state_event" }, isModuleOption = true)
            PillUtil.buildSpannable(context, "$statePrefix ", eventPill)
        } else {
            val event = step.parameters["device_event"] as? String ?: DEVICE_EVENT_CONNECTED
            val deviceName = step.parameters["device_name"] as? String ?: "任何设备"
            val eventPill = PillUtil.createPillFromParam(event, getInputs().find { it.id == "device_event" }, isModuleOption = true)
            val devicePill = PillUtil.Pill(deviceName, "device_name")
            PillUtil.buildSpannable(context, "$devicePrefix ", eventPill, " ", devicePill)
        }
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_bluetooth_triggered)))
        val deviceName = context.triggerData as? VDictionary
        val outputs = mapOf(
            "device_name" to (deviceName?.raw?.get("name") as? VString ?: VString("")),
            "device_address" to (deviceName?.raw?.get("address") as? VString ?: VString(""))
        )
        return ExecutionResult.Success(outputs)
    }
}
