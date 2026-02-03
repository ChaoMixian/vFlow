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
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class LaunchAppModule : BaseModule() {

    override val id = "vflow.system.launch_app"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_launch_app_name,
        descriptionStringRes = R.string.module_vflow_system_launch_app_desc,
        name = "启动应用/活动",  // Fallback
        description = "启动一个指定的应用程序或其内部的某个页面(Activity)",  // Fallback
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "应用与系统"
    )

    // 复用AppStartTrigger的UIProvider
    override val uiProvider: ModuleUIProvider = com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "packageName",
            name = "应用包名",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_launch_app_packageName_name
        ),
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "LAUNCH", // "LAUNCH" 是一个特殊值，代表仅启动应用
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_launch_app_activityName_name
        )
    )
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_launch_app_success_name
        )
    )


    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packageName = step.parameters["packageName"] as? String
        val activityName = step.parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            return context.getString(R.string.summary_vflow_system_launch_app_select)
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

        val prefix = context.getString(R.string.summary_vflow_system_launch_app_prefix)

        return PillUtil.buildSpannable(context,
            "$prefix ",
            // 创建一个药丸，让用户可以点击它来重新选择应用/Activity
            PillUtil.Pill(displayText, "packageName")
        )
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 现在 variables 是 Map<String, VObject>，需要使用 getVariableAsString 获取
        val packageName = context.getVariableAsString("packageName", "")
        val activityName = context.getVariableAsString("activityName", "LAUNCH")

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_empty_package),
                appContext.getString(R.string.error_vflow_system_launch_app_package_required)
            )
        }

        val intent = if (activityName == "LAUNCH" || activityName.isNullOrBlank()) {
            context.applicationContext.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent().apply {
                component = ComponentName(packageName, activityName)
            }
        }

        if (intent == null) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                appContext.getString(R.string.error_vflow_system_launch_app_intent_not_found)
            )
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.applicationContext.startActivity(intent)

            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_system_launch_app_launched), packageName)))

            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_exception),
                e.localizedMessage ?: "发生了未知错误"
            )
        }
    }
}