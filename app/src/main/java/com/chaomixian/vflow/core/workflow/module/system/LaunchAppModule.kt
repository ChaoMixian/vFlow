// 文件: LaunchAppModule.kt
// 描述: 定义了启动指定应用或Activity的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class LaunchAppModule : BaseModule() {

    override val id = "vflow.system.launch_app"
    override val metadata = ActionMetadata(
        name = "启动应用/活动",
        description = "启动一个指定的应用程序或其内部的某个页面(Activity)。",
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "应用与系统"
    )

    // 复用AppStartTrigger的UIProvider
    override val uiProvider: ModuleUIProvider = com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerUIProvider()

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
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )


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
            "启动 ",
            // 创建一个药丸(Pill)，让用户可以点击它来重新选择应用/Activity
            PillUtil.Pill(displayText, "packageName")
        )
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val packageName = context.variables["packageName"] as? String
        val activityName = context.variables["activityName"] as? String

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "应用包名不能为空。")
        }

        val intent = if (activityName == "LAUNCH" || activityName.isNullOrBlank()) {
            context.applicationContext.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent().apply {
                component = ComponentName(packageName, activityName)
            }
        }

        if (intent == null) {
            return ExecutionResult.Failure("启动失败", "无法找到应用的启动意图。请确认应用已安装或Activity名称正确。")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.applicationContext.startActivity(intent)
            onProgress(ProgressUpdate("已启动: $packageName"))
            ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure("启动异常", e.localizedMessage ?: "发生了未知错误")
        }
    }
}