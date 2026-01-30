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
    override val id = "vflow.trigger.wifi"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_wifi_name,
        descriptionStringRes = R.string.module_vflow_trigger_wifi_desc,
        name = "Wi-Fi触发器",  // Fallback
        description = "当Wi-Fi状态或网络连接变化时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.LOCATION)
    override val uiProvider: ModuleUIProvider = WifiTriggerUIProvider()

    private val triggerTypeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_wifi_type_connection),
            appContext.getString(R.string.option_vflow_trigger_wifi_type_state)
        )
    }
    private val connectionEventOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_wifi_connection_connected),
            appContext.getString(R.string.option_vflow_trigger_wifi_connection_disconnected)
        )
    }
    private val stateEventOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_wifi_state_on),
            appContext.getString(R.string.option_vflow_trigger_wifi_state_off)
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("trigger_type", "触发类型", ParameterType.ENUM, triggerTypeOptions[0], options = triggerTypeOptions, nameStringRes = R.string.param_vflow_trigger_wifi_trigger_type_name),
        // 网络连接
        InputDefinition("connection_event", "事件", ParameterType.ENUM, connectionEventOptions[0], options = connectionEventOptions, nameStringRes = R.string.param_vflow_trigger_wifi_connection_event_name),
        InputDefinition("network_target", "网络", ParameterType.STRING, defaultValue = WifiTriggerHandler.ANY_WIFI_TARGET, isHidden = true, nameStringRes = R.string.param_vflow_trigger_wifi_network_target_name),
        // Wi-Fi状态
        InputDefinition("state_event", "事件", ParameterType.ENUM, stateEventOptions[0], options = stateEventOptions, nameStringRes = R.string.param_vflow_trigger_wifi_state_event_name)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: triggerTypeOptions[0]
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == triggerTypeOptions[0]) { // 网络连接
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
        val triggerType = step.parameters["trigger_type"] as? String ?: triggerTypeOptions[0]
        val anyWifi = context.getString(R.string.summary_vflow_trigger_wifi_any_wifi)
        val connectionPrefix = context.getString(R.string.summary_vflow_trigger_wifi_connection_prefix)
        val statePrefix = context.getString(R.string.summary_vflow_trigger_wifi_state_prefix)

        return if (triggerType == triggerTypeOptions[0]) { // 网络连接
            val event = step.parameters["connection_event"] as? String ?: connectionEventOptions[0]
            val target = step.parameters["network_target"] as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET
            val eventPill = PillUtil.Pill(event, "connection_event", isModuleOption = true)
            val targetDescription = if (target == WifiTriggerHandler.ANY_WIFI_TARGET) anyWifi else target
            val targetPill = PillUtil.Pill(targetDescription, "network_target")
            PillUtil.buildSpannable(context, "$connectionPrefix ", eventPill, " ", targetPill)
        } else {
            val event = step.parameters["state_event"] as? String ?: stateEventOptions[0]
            val eventPill = PillUtil.Pill(event, "state_event", isModuleOption = true)
            PillUtil.buildSpannable(context, "$statePrefix ", eventPill)
        }
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("Wi-Fi 事件已触发"))
        val ssid = context.triggerData as? VString
        val bssid = context.variables["bssid"] as? VString
        return ExecutionResult.Success(outputs = mapOf(
            "ssid" to (ssid ?: VString("")),
            "bssid" to (bssid ?: VString(""))
        ))
    }
}