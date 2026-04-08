// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/WifiTriggerModule.kt
// 描述: 增加了触发类型，现在支持 Wi-Fi 开启/关闭状态触发 和 网络连接/断开触发。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.WifiTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WifiTriggerModule : BaseModule() {
    companion object {
        const val TRIGGER_TYPE_CONNECTION = "connection"
        const val TRIGGER_TYPE_STATE = "state"
        const val CONNECTION_EVENT_CONNECTED = "connected"
        const val CONNECTION_EVENT_DISCONNECTED = "disconnected"
        const val STATE_EVENT_ON = "on"
        const val STATE_EVENT_OFF = "off"

        fun normalizeTriggerType(value: String?): String? {
            return when (value) {
                TRIGGER_TYPE_CONNECTION, "网络连接", "Network Connection" -> TRIGGER_TYPE_CONNECTION
                TRIGGER_TYPE_STATE, "Wi-Fi状态", "Wi-Fi State" -> TRIGGER_TYPE_STATE
                else -> null
            }
        }

        fun normalizeConnectionEvent(value: String?): String? {
            return when (value) {
                CONNECTION_EVENT_CONNECTED, "连接到", "Connect to" -> CONNECTION_EVENT_CONNECTED
                CONNECTION_EVENT_DISCONNECTED, "断开连接", "Disconnect from" -> CONNECTION_EVENT_DISCONNECTED
                else -> null
            }
        }

        fun normalizeStateEvent(value: String?): String? {
            return when (value) {
                STATE_EVENT_ON, "开启时", "Turned On" -> STATE_EVENT_ON
                STATE_EVENT_OFF, "关闭时", "Turned Off" -> STATE_EVENT_OFF
                else -> null
            }
        }
    }
    override val id = "vflow.trigger.wifi"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_wifi_name,
        descriptionStringRes = R.string.module_vflow_trigger_wifi_desc,
        name = "Wi-Fi触发器",  // Fallback
        description = "当Wi-Fi状态或网络连接变化时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions = listOf(PermissionManager.LOCATION)
    override val uiProvider: ModuleUIProvider = WifiTriggerUIProvider()

    private val triggerTypeOptions by lazy { listOf(TRIGGER_TYPE_CONNECTION, TRIGGER_TYPE_STATE) }
    private val connectionEventOptions by lazy { listOf(CONNECTION_EVENT_CONNECTED, CONNECTION_EVENT_DISCONNECTED) }
    private val stateEventOptions by lazy { listOf(STATE_EVENT_ON, STATE_EVENT_OFF) }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "trigger_type",
            "触发类型",
            ParameterType.ENUM,
            TRIGGER_TYPE_CONNECTION,
            options = triggerTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_wifi_type_connection,
                R.string.option_vflow_trigger_wifi_type_state
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_wifi_type_connection) to TRIGGER_TYPE_CONNECTION,
                appContext.getString(R.string.option_vflow_trigger_wifi_type_state) to TRIGGER_TYPE_STATE
            ),
            nameStringRes = R.string.param_vflow_trigger_wifi_trigger_type_name
        ),
        // 网络连接
        InputDefinition(
            "connection_event",
            "事件",
            ParameterType.ENUM,
            CONNECTION_EVENT_CONNECTED,
            options = connectionEventOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_wifi_connection_connected,
                R.string.option_vflow_trigger_wifi_connection_disconnected
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_wifi_connection_connected) to CONNECTION_EVENT_CONNECTED,
                appContext.getString(R.string.option_vflow_trigger_wifi_connection_disconnected) to CONNECTION_EVENT_DISCONNECTED
            ),
            nameStringRes = R.string.param_vflow_trigger_wifi_connection_event_name
        ),
        InputDefinition("network_target", "网络", ParameterType.STRING, defaultValue = WifiTriggerHandler.ANY_WIFI_TARGET, isHidden = true, nameStringRes = R.string.param_vflow_trigger_wifi_network_target_name),
        // Wi-Fi状态
        InputDefinition(
            "state_event",
            "事件",
            ParameterType.ENUM,
            STATE_EVENT_ON,
            options = stateEventOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_wifi_state_on,
                R.string.option_vflow_trigger_wifi_state_off
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_wifi_state_on) to STATE_EVENT_ON,
                appContext.getString(R.string.option_vflow_trigger_wifi_state_off) to STATE_EVENT_OFF
            ),
            nameStringRes = R.string.param_vflow_trigger_wifi_state_event_name
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: TRIGGER_TYPE_CONNECTION
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == TRIGGER_TYPE_CONNECTION) {
            dynamicInputs.add(all.first { it.id == "connection_event" })
            dynamicInputs.add(all.first { it.id == "network_target" })
        } else {
            dynamicInputs.add(all.first { it.id == "state_event" })
        }
        return dynamicInputs
    }


    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("ssid", "网络SSID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_wifi_ssid_name),
        OutputDefinition("bssid", "网络BSSID", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_wifi_bssid_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val triggerType = step.parameters["trigger_type"] as? String ?: TRIGGER_TYPE_CONNECTION
        val anyWifi = context.getString(R.string.summary_vflow_trigger_wifi_any_wifi)
        val connectionPrefix = context.getString(R.string.summary_vflow_trigger_wifi_connection_prefix)
        val statePrefix = context.getString(R.string.summary_vflow_trigger_wifi_state_prefix)

        return if (triggerType == TRIGGER_TYPE_CONNECTION) {
            val event = step.parameters["connection_event"] as? String ?: CONNECTION_EVENT_CONNECTED
            val target = step.parameters["network_target"] as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET
            val eventPill = PillUtil.createPillFromParam(event, getInputs().find { it.id == "connection_event" }, isModuleOption = true)
            val targetDescription = if (target == WifiTriggerHandler.ANY_WIFI_TARGET) anyWifi else target
            val targetPill = PillUtil.Pill(targetDescription, "network_target")
            PillUtil.buildSpannable(context, "$connectionPrefix ", eventPill, " ", targetPill)
        } else {
            val event = step.parameters["state_event"] as? String ?: STATE_EVENT_ON
            val eventPill = PillUtil.createPillFromParam(event, getInputs().find { it.id == "state_event" }, isModuleOption = true)
            PillUtil.buildSpannable(context, "$statePrefix ", eventPill)
        }
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_wifi_triggered)))
        val ssid = context.triggerData as? VString
        // 现在 variables 是 Map<String, VObject>，使用 getVariable 获取并检查类型
        val bssid = context.getVariable("bssid") as? VString
        return ExecutionResult.Success(outputs = mapOf(
            "ssid" to (ssid ?: VString("")),
            "bssid" to (bssid ?: VString(""))
        ))
    }
}
