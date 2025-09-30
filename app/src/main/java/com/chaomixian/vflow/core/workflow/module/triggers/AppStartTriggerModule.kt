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
        name = "应用事件", // 名称更新
        description = "当指定的应用程序打开或关闭时，触发此工作流。", // 描述更新
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider = AppStartTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        // 新增事件选择
        InputDefinition(
            id = "event",
            name = "事件",
            staticType = ParameterType.ENUM,
            defaultValue = "打开时",
            options = listOf("打开时", "关闭时"),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "packageName",
            name = "应用包名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",
            staticType = ParameterType.STRING,
            defaultValue = "LAUNCH", // "LAUNCH" 代表任意Activity或仅应用本身
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val event = step.parameters["event"] as? String ?: "打开时"
        val packageName = step.parameters["packageName"] as? String
        val activityName = step.parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            return "选择一个应用"
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

        return PillUtil.buildSpannable(context, "当 ", appPill, " ", eventPill)
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("应用事件已触发"))
        return ExecutionResult.Success()
    }
}