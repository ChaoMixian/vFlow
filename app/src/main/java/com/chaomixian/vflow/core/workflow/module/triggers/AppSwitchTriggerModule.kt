package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class AppSwitchTriggerModule : BaseModule() {
    override val id = "vflow.trigger.app_switch"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_app_switch_name,
        descriptionStringRes = R.string.module_vflow_trigger_app_switch_desc,
        name = "应用切换",
        description = "当前台应用发生切换时触发工作流，可排除指定应用",
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "触发器",
        categoryId = "trigger"
    )
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)
    override val uiProvider: ModuleUIProvider = AppSwitchTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "excludedPackageNames",
            name = "排除应用包名列表",
            nameStringRes = R.string.param_vflow_trigger_app_switch_excluded_package_names_name,
            staticType = ParameterType.ANY,
            defaultValue = listOf("com.chaomixian.vflow"),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("package_name", "当前应用包名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_switch_package_name_name),
        OutputDefinition("app_name", "当前应用名称", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_switch_app_name_name),
        OutputDefinition("previous_package_name", "上一应用包名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_switch_previous_package_name_name),
        OutputDefinition("previous_app_name", "上一应用名称", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_switch_previous_app_name_name),
        OutputDefinition("class_name", "当前窗口类名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_switch_class_name_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        @Suppress("UNCHECKED_CAST")
        val excludedPackageNames = step.parameters["excludedPackageNames"] as? List<String> ?: emptyList()
        val displayText = if (excludedPackageNames.isEmpty()) {
            context.getString(R.string.summary_vflow_trigger_app_switch_no_exclusions)
        } else {
            val pm = context.packageManager
            val appNames = excludedPackageNames.map { packageName ->
                resolveAppName(pm, packageName)
            }
            when (appNames.size) {
                1 -> appNames[0]
                2 -> context.getString(R.string.summary_vflow_trigger_two_apps, appNames[0], appNames[1])
                else -> context.getString(R.string.summary_vflow_trigger_many_apps, appNames[0], appNames.size)
            }
        }

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_app_switch_prefix),
            " ",
            PillUtil.Pill(displayText, "excludedPackageNames")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_app_switch_triggered)))
        val triggerData = context.triggerData as? VDictionary
        return ExecutionResult.Success(
            outputs = mapOf(
                "package_name" to VString(triggerData.stringValue("package_name")),
                "app_name" to VString(triggerData.stringValue("app_name")),
                "previous_package_name" to VString(triggerData.stringValue("previous_package_name")),
                "previous_app_name" to VString(triggerData.stringValue("previous_app_name")),
                "class_name" to VString(triggerData.stringValue("class_name"))
            )
        )
    }

    private fun resolveAppName(packageManager: PackageManager, packageName: String): String {
        return try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun VDictionary?.stringValue(key: String): String {
        return (this?.raw?.get(key) as? VString)?.raw.orEmpty()
    }
}
