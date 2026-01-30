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
        listOf(
            appContext.getString(R.string.option_vflow_trigger_app_start_event_opened),
            appContext.getString(R.string.option_vflow_trigger_app_start_event_closed)
        )
    }

    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider = AppStartTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        // 新增事件选择
        InputDefinition(
            id = "event",
            name = "事件",
            nameStringRes = R.string.param_vflow_trigger_app_start_event_name,
            staticType = ParameterType.ENUM,
            defaultValue = eventOptions[0],
            options = eventOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "packageName",
            name = "应用包名",
            nameStringRes = R.string.param_vflow_trigger_app_start_packageName_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",
            nameStringRes = R.string.param_vflow_trigger_app_start_activityName_name,
            staticType = ParameterType.STRING,
            defaultValue = "LAUNCH", // "LAUNCH" 代表任意Activity或仅应用本身
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val defaultEvent = eventOptions[0]
        val event = step.parameters["event"] as? String ?: defaultEvent
        val packageName = step.parameters["packageName"] as? String
        val activityName = step.parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            return context.getString(R.string.summary_vflow_trigger_app_start_select)
        }

        val pm = context.packageManager
        val appName = try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            appName
        } else {
            activityName.substringAfterLast('.')
        }

        val eventPill = PillUtil.Pill(event, "event", isModuleOption = true)
        val appPill = PillUtil.Pill(displayText, "packageName")

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