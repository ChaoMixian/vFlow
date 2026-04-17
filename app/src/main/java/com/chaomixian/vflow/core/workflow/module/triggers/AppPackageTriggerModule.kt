package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class AppPackageTriggerModule : BaseModule() {
    companion object {
        const val EVENT_INSTALLED = "installed"
        const val EVENT_REMOVED = "removed"
        const val EVENT_UPDATED = "updated"

        private val EVENT_OPTIONS = listOf(EVENT_INSTALLED, EVENT_REMOVED, EVENT_UPDATED)
        private val EVENT_INPUT = InputDefinition(
            id = "event",
            name = "事件",
            nameStringRes = R.string.param_vflow_trigger_app_package_event_name,
            staticType = ParameterType.ENUM,
            defaultValue = EVENT_INSTALLED,
            options = EVENT_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_app_package_event_installed,
                R.string.option_vflow_trigger_app_package_event_removed,
                R.string.option_vflow_trigger_app_package_event_updated
            ),
            acceptsMagicVariable = false
        )
    }

    override val id = "vflow.trigger.app_package"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_app_package_name,
        descriptionStringRes = R.string.module_vflow_trigger_app_package_desc,
        name = "应用安装卸载更新",
        description = "当应用被安装、卸载或更新时触发工作流",
        iconRes = R.drawable.rounded_extension_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val uiProvider: ModuleUIProvider = AppPackageTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        EVENT_INPUT,
        InputDefinition(
            id = "packageNames",
            name = "应用包名列表",
            nameStringRes = R.string.param_vflow_trigger_app_package_package_name_name,
            staticType = ParameterType.ANY,
            defaultValue = emptyList<String>(),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("package_name", "应用包名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_package_package_name_name),
        OutputDefinition("app_name", "应用名称", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_package_app_name_name),
        OutputDefinition("event", "事件", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_app_package_event_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val event = EVENT_INPUT.normalizeEnumValueOrNull(step.parameters["event"] as? String) ?: EVENT_INSTALLED
        @Suppress("UNCHECKED_CAST")
        val packageNames = step.parameters["packageNames"] as? List<String> ?: emptyList()

        val displayText = if (packageNames.isEmpty()) {
            context.getString(R.string.summary_vflow_trigger_app_package_any_app)
        } else {
            val pm = context.packageManager
            val appNames = packageNames.map { packageName ->
                try {
                    pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    packageName
                }
            }
            when (appNames.size) {
                1 -> appNames[0]
                2 -> "${appNames[0]} 和 ${appNames[1]}"
                else -> "${appNames[0]} 等 ${appNames.size} 个应用"
            }
        }

        val eventPill = PillUtil.createPillFromParam(
            event,
            getInputs().find { it.id == "event" },
            isModuleOption = true
        )
        val appPill = PillUtil.Pill(displayText, "packageNames")
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_app_package_prefix),
            " ",
            appPill,
            " ",
            eventPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_app_package_triggered)))
        val triggerData = context.triggerData as? VDictionary
        val packageName = (triggerData?.raw?.get("package_name") as? VString)?.raw ?: ""
        val appName = (triggerData?.raw?.get("app_name") as? VString)?.raw ?: ""
        val event = (triggerData?.raw?.get("event") as? VString)?.raw ?: ""

        return ExecutionResult.Success(
            outputs = mapOf(
                "package_name" to VString(packageName),
                "app_name" to VString(appName),
                "event" to VString(event)
            )
        )
    }
}
