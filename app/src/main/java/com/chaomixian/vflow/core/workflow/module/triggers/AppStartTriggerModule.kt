// 文件: AppStartTriggerModule.kt
// 描述: 定义了当指定应用或Activity启动时触发工作流的模块。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class AppStartTriggerModule : BaseModule() {

    override val id = "vflow.trigger.app_start"
    override val metadata = ActionMetadata(
        name = "应用/Activity启动",
        description = "当指定的应用程序或页面启动时，触发此工作流。",
        iconRes = R.drawable.rounded_activity_zone_24, // 使用新图标
        category = "触发器"
    )

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider = AppStartTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
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
            defaultValue = "LAUNCH", // "LAUNCH" 是一个特殊值，代表仅启动应用
            acceptsMagicVariable = false
        )
    )

    // 这个触发器不产生任何有意义的输出
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packageName = step.parameters["packageName"] as? String
        val activityName = step.parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            return "选择一个应用或Activity"
        }

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // 如果找不到应用，就显示包名
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            appName
        } else {
            // 如果Activity名称过长，只显示类名
            activityName.substringAfterLast('.')
        }

        return PillUtil.buildSpannable(context,
            "当启动 ",
            // 创建一个药丸(Pill)，让用户可以点击它来重新选择应用/Activity
            PillUtil.Pill(displayText, false, "packageName")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 触发器模块的执行逻辑由系统服务处理，此处仅需成功返回即可
        onProgress(ProgressUpdate("应用/Activity 启动事件已触发"))
        return ExecutionResult.Success()
    }
}