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
        const val ANY_DEVICE_ADDRESS = "any"
        private val TRIGGER_TYPE_LEGACY_MAP = mapOf(
            "蓝牙状态" to TRIGGER_TYPE_STATE,
            "设备连接" to TRIGGER_TYPE_DEVICE
        )
        private val STATE_EVENT_LEGACY_MAP = mapOf(
            "开启时" to STATE_EVENT_ON,
            "关闭时" to STATE_EVENT_OFF
        )
        private val DEVICE_EVENT_LEGACY_MAP = mapOf(
            "连接时" to DEVICE_EVENT_CONNECTED,
            "断开时" to DEVICE_EVENT_DISCONNECTED
        )
    }
    override val id = "vflow.trigger.bluetooth"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_bluetooth_name,
        descriptionStringRes = R.string.module_vflow_trigger_bluetooth_desc,
        name = "蓝牙触发器",  // Fallback
        description = "当蓝牙状态或设备连接变化时触发",  // Fallback
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "触发器",
        categoryId = "trigger"
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
            legacyValueMap = TRIGGER_TYPE_LEGACY_MAP,
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
            legacyValueMap = STATE_EVENT_LEGACY_MAP,
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
            legacyValueMap = DEVICE_EVENT_LEGACY_MAP,
            nameStringRes = R.string.param_vflow_trigger_bluetooth_device_event_name
        ),
        InputDefinition("device_address", "设备地址", ParameterType.STRING, defaultValue = ANY_DEVICE_ADDRESS, isHidden = true, nameStringRes = R.string.param_vflow_trigger_bluetooth_device_address_name),
        InputDefinition("device_name", "设备名称", ParameterType.STRING, defaultValue = "", nameStringRes = R.string.param_vflow_trigger_bluetooth_device_name_name)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerTypeInput = all.first { it.id == "trigger_type" }
        val rawTriggerType = step?.parameters?.get("trigger_type") as? String ?: TRIGGER_TYPE_STATE
        val triggerType = triggerTypeInput.normalizeEnumValue(rawTriggerType) ?: rawTriggerType
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
        val allInputs = getInputs()
        val triggerTypeInput = allInputs.first { it.id == "trigger_type" }
        val rawTriggerType = step.parameters["trigger_type"] as? String ?: TRIGGER_TYPE_STATE
        val triggerType = triggerTypeInput.normalizeEnumValue(rawTriggerType) ?: rawTriggerType
        val statePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_state_prefix)
        val devicePrefix = context.getString(R.string.summary_vflow_trigger_bluetooth_device_prefix)

        return if (triggerType == TRIGGER_TYPE_STATE) {
            val rawEvent = step.parameters["state_event"] as? String ?: STATE_EVENT_ON
            val eventPill = PillUtil.createPillFromParam(rawEvent, allInputs.find { it.id == "state_event" }, isModuleOption = true)
            PillUtil.buildSpannable(context, "$statePrefix ", eventPill)
        } else {
            val rawEvent = step.parameters["device_event"] as? String ?: DEVICE_EVENT_CONNECTED
            val deviceAddress = step.parameters["device_address"] as? String
            val deviceName = step.parameters["device_name"] as? String
            val eventPill = PillUtil.createPillFromParam(rawEvent, allInputs.find { it.id == "device_event" }, isModuleOption = true)
            val devicePill = PillUtil.Pill(
                if (deviceAddress == ANY_DEVICE_ADDRESS || (deviceAddress.isNullOrBlank() && deviceName.isNullOrBlank())) {
                    context.getString(R.string.summary_vflow_trigger_bluetooth_any_device)
                } else {
                    deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress.orEmpty()
                },
                "device_name"
            )
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
