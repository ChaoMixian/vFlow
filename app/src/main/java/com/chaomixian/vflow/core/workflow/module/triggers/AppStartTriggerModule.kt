// 文件: AppStartTriggerModule.kt
// 描述: 定义了当指定应用或Activity启动或关闭时触发工作流的模块。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class AppStartTriggerModule : BaseModule() {

    override val id = "vflow.trigger.app_start"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_app_start_name,
        descriptionStringRes = R.string.module_vflow_trigger_app_start_desc,
        name = "应用事件",  // Fallback
        description = "当指定的应用程序打开或关闭时，触发此工作流",  // Fallback
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "触发器"
    )

    private val eventOptions by lazy {
        listOf("打开时", "关闭时")
    }

    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider = AppStartTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "event",
            name = "事件",
            nameStringRes = R.string.param_vflow_trigger_app_start_event_name,
            staticType = ParameterType.ENUM,
            defaultValue = "打开时",
            options = eventOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "packageNames",
            name = "应用包名列表",
            nameStringRes = R.string.param_vflow_trigger_app_start_packageName_name,
            staticType = ParameterType.ANY,
            defaultValue = emptyList<String>(),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val defaultEvent = eventOptions[0]
        val event = step.parameters["event"] as? String ?: defaultEvent

        @Suppress("UNCHECKED_CAST")
        val packageNames = step.parameters["packageNames"] as? List<String> ?: emptyList()

        if (packageNames.isEmpty()) {
            return context.getString(R.string.summary_vflow_trigger_app_start_select)
        }

        val pm = context.packageManager
        val appNames = packageNames.mapNotNull { packageName ->
            try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        val displayText = when (appNames.size) {
            1 -> appNames[0]
            2 -> "${appNames[0]} 和 ${appNames[1]}"
            else -> "${appNames[0]} 等 ${appNames.size} 个应用"
        }

        val eventPill = PillUtil.Pill(event, "event", isModuleOption = true)
        val appPill = PillUtil.Pill(displayText, "packageNames")

        val prefix = context.getString(R.string.summary_vflow_trigger_app_start_prefix)

        return PillUtil.buildSpannable(context, "$prefix ", appPill, " ", eventPill)
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("应用事件已触发"))
        return ExecutionResult.Success()
    }
}