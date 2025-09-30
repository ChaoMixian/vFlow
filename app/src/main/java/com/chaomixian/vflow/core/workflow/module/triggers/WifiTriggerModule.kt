// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/WifiTriggerModule.kt
// 描述: [重构] 增加了触发类型，现在支持 Wi-Fi 开启/关闭状态触发 和 网络连接/断开触发。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.WifiTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WifiTriggerModule : BaseModule() {
    override val id = "vflow.trigger.wifi"
    override val metadata = ActionMetadata(
        name = "Wi-Fi触发器",
        description = "当Wi-Fi状态或网络连接变化时触发工作流。",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.LOCATION)
    override val uiProvider: ModuleUIProvider = WifiTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("trigger_type", "触发类型", ParameterType.ENUM, "网络连接", options = listOf("网络连接", "Wi-Fi状态")),
        // 网络连接
        InputDefinition("connection_event", "事件", ParameterType.ENUM, "连接到", options = listOf("连接到", "断开连接")),
        InputDefinition("network_target", "网络", ParameterType.STRING, defaultValue = WifiTriggerHandler.ANY_WIFI_TARGET, isHidden = true),
        // Wi-Fi状态
        InputDefinition("state_event", "事件", ParameterType.ENUM, "开启时", options = listOf("开启时", "关闭时"))
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val triggerType = step?.parameters?.get("trigger_type") as? String ?: "网络连接"
        val dynamicInputs = mutableListOf(all.first { it.id == "trigger_type" })

        if (triggerType == "网络连接") {
            dynamicInputs.add(all.first { it.id == "connection_event" })
            dynamicInputs.add(all.first { it.id == "network_target" })
        } else {
            dynamicInputs.add(all.first { it.id == "state_event" })
        }
        return dynamicInputs
    }


    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("ssid", "网络SSID", TextVariable.TYPE_NAME),
        OutputDefinition("bssid", "网络BSSID", TextVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val triggerType = step.parameters["trigger_type"] as? String ?: "网络连接"

        return if (triggerType == "网络连接") {
            val event = step.parameters["connection_event"] as? String ?: "连接到"
            val target = step.parameters["network_target"] as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET
            val eventPill = PillUtil.Pill(event, "connection_event", isModuleOption = true)
            val targetDescription = if (target == WifiTriggerHandler.ANY_WIFI_TARGET) "任意 Wi-Fi" else target
            val targetPill = PillUtil.Pill(targetDescription, "network_target")
            PillUtil.buildSpannable(context, "当 ", eventPill, " ", targetPill)
        } else {
            val event = step.parameters["state_event"] as? String ?: "开启时"
            val eventPill = PillUtil.Pill(event, "state_event", isModuleOption = true)
            PillUtil.buildSpannable(context, "当 Wi-Fi ", eventPill)
        }
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("Wi-Fi 事件已触发"))
        val ssid = context.triggerData as? TextVariable
        val bssid = context.variables["bssid"] as? TextVariable
        return ExecutionResult.Success(outputs = mapOf(
            "ssid" to (ssid ?: TextVariable("")),
            "bssid" to (bssid ?: TextVariable(""))
        ))
    }
}