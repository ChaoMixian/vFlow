// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/WifiTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.WifiTriggerReceiver
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WifiTriggerModule : BaseModule() {
    override val id = "vflow.trigger.wifi"
    override val metadata = ActionMetadata(
        name = "Wi-Fi触发",
        description = "当设备连接或断开Wi-Fi时触发工作流。",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "触发器"
    )

    // [修改] 将所需权限改为精确定位，这是获取Wi-Fi列表所必需的
    override val requiredPermissions = listOf(PermissionManager.LOCATION)
    override val uiProvider: ModuleUIProvider = WifiTriggerUIProvider()

    private val eventOptions = listOf("连接到", "断开连接")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "event",
            name = "事件",
            staticType = ParameterType.ENUM,
            defaultValue = "连接到",
            options = eventOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "network_target",
            name = "网络",
            staticType = ParameterType.STRING,
            defaultValue = WifiTriggerReceiver.ANY_WIFI_TARGET,
            acceptsMagicVariable = false,
            isHidden = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("ssid", "网络SSID", TextVariable.TYPE_NAME),
        OutputDefinition("bssid", "网络BSSID", TextVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val event = step.parameters["event"] as? String ?: "连接到"
        val target = step.parameters["network_target"] as? String ?: WifiTriggerReceiver.ANY_WIFI_TARGET

        val eventPill = PillUtil.Pill(event, false, "event", isModuleOption = true)

        val targetDescription = if (target == WifiTriggerReceiver.ANY_WIFI_TARGET) "任意 Wi-Fi" else target
        val targetPill = PillUtil.Pill(targetDescription, false, "network_target")

        return PillUtil.buildSpannable(context,
            "当 ",
            eventPill,
            " ",
            targetPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("Wi-Fi 状态变化事件已触发"))
        val ssid = context.triggerData as? TextVariable
        val bssid = context.variables["bssid"] as? TextVariable
        return ExecutionResult.Success(outputs = mapOf(
            "ssid" to (ssid ?: TextVariable("")),
            "bssid" to (bssid ?: TextVariable(""))
        ))
    }
}