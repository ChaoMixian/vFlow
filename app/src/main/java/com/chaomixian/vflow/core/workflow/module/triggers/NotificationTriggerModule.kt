// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/NotificationTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class NotificationTriggerModule : BaseModule() {
    companion object {
        const val APP_FILTER_INCLUDE = "app_include"
        const val APP_FILTER_EXCLUDE = "app_exclude"
        const val TEXT_FILTER_INCLUDE = "text_include"
        const val TEXT_FILTER_EXCLUDE = "text_exclude"

        private val APP_FILTER_OPTIONS = listOf(APP_FILTER_INCLUDE, APP_FILTER_EXCLUDE)
        private val TEXT_FILTER_OPTIONS = listOf(TEXT_FILTER_INCLUDE, TEXT_FILTER_EXCLUDE)
    }

    override val id = "vflow.trigger.notification"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_notification_name,
        descriptionStringRes = R.string.module_vflow_trigger_notification_desc,
        name = "通知触发",  // Fallback
        description = "当收到符合条件的通知时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)
    override val uiProvider: ModuleUIProvider = NotificationTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "app_filter_type",
            name = "应用过滤方式",
            staticType = ParameterType.ENUM,
            defaultValue = APP_FILTER_EXCLUDE,
            options = APP_FILTER_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_notification_include,
                R.string.option_vflow_trigger_notification_exclude
            ),
            nameStringRes = R.string.param_vflow_trigger_notification_app_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "packageNames",
            name = "应用列表",
            nameStringRes = R.string.param_vflow_trigger_notification_app_filter_name,
            staticType = ParameterType.ANY,
            defaultValue = listOf(appContext.packageName),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "title_filter_type",
            name = "标题过滤方式",
            staticType = ParameterType.ENUM,
            defaultValue = TEXT_FILTER_INCLUDE,
            options = TEXT_FILTER_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_notification_include,
                R.string.option_vflow_trigger_notification_exclude
            ),
            nameStringRes = R.string.param_vflow_trigger_notification_title_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = false
        ),
        InputDefinition("title_filter", "标题关键词", ParameterType.STRING, nameStringRes = R.string.param_vflow_trigger_notification_title_filter_name),
        InputDefinition(
            id = "content_filter_type",
            name = "内容过滤方式",
            staticType = ParameterType.ENUM,
            defaultValue = TEXT_FILTER_INCLUDE,
            options = TEXT_FILTER_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_notification_include,
                R.string.option_vflow_trigger_notification_exclude
            ),
            nameStringRes = R.string.param_vflow_trigger_notification_content_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = false
        ),
        InputDefinition("content_filter", "内容关键词", ParameterType.STRING, nameStringRes = R.string.param_vflow_trigger_notification_content_filter_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("notification_object", "通知对象", VTypeRegistry.NOTIFICATION.id, nameStringRes = R.string.output_vflow_trigger_notification_notification_object_name),
        OutputDefinition("package_name", "应用包名", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_notification_package_name_name),
        OutputDefinition("title", "通知标题", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_notification_title_name),
        OutputDefinition("content", "通知内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_notification_content_name)
    )

    /**
     * [已修改] 更新摘要逻辑，使其更清晰地显示所有过滤条件。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val appFilterType = normalizeEnum("app_filter_type", step.parameters["app_filter_type"] as? String, APP_FILTER_INCLUDE)
        @Suppress("UNCHECKED_CAST")
        val packageNames = step.parameters["packageNames"] as? List<String> ?: emptyList()
        val appFilter = step.parameters["app_filter"] as? String
        val effectivePackages = if (packageNames.isNotEmpty()) packageNames else listOfNotNull(appFilter?.takeIf { it.isNotBlank() })
        val titleFilterType = normalizeEnum("title_filter_type", step.parameters["title_filter_type"] as? String, TEXT_FILTER_INCLUDE)
        val titleFilter = step.parameters["title_filter"] as? String
        val contentFilterType = normalizeEnum("content_filter_type", step.parameters["content_filter_type"] as? String, TEXT_FILTER_INCLUDE)
        val contentFilter = step.parameters["content_filter"] as? String

        val prefix = context.getString(R.string.summary_vflow_trigger_notification_prefix)
        val appContains = context.getString(R.string.summary_vflow_trigger_notification_app_contains)
        val appNotContains = context.getString(R.string.summary_vflow_trigger_notification_app_not_contains)
        val and = context.getString(R.string.summary_vflow_trigger_notification_and)
        val titleContains = context.getString(R.string.summary_vflow_trigger_notification_title_contains)
        val titleNotContains = context.getString(R.string.summary_vflow_trigger_notification_title_not_contains)
        val contentContains = context.getString(R.string.summary_vflow_trigger_notification_content_contains)
        val contentNotContains = context.getString(R.string.summary_vflow_trigger_notification_content_not_contains)
        val any = context.getString(R.string.summary_vflow_trigger_notification_any)
        val suffix = context.getString(R.string.summary_vflow_trigger_notification_suffix)

        val parts = mutableListOf<Any>(prefix)
        var hasCondition = false

        if (effectivePackages.isNotEmpty()) {
            parts.add(if (appFilterType == APP_FILTER_EXCLUDE) appNotContains else appContains)
            parts.add(PillUtil.Pill(formatPackageSummary(context, effectivePackages), "packageNames"))
            hasCondition = true
        }

        if (!titleFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(and)
            parts.add(if (titleFilterType == TEXT_FILTER_EXCLUDE) titleNotContains else titleContains)
            parts.add(PillUtil.Pill(titleFilter, "title_filter"))
            hasCondition = true
        }

        if (!contentFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(and)
            parts.add(if (contentFilterType == TEXT_FILTER_EXCLUDE) contentNotContains else contentContains)
            parts.add(PillUtil.Pill(contentFilter, "content_filter"))
            hasCondition = true
        }

        if (!hasCondition) {
            parts.add(any)
        }

        parts.add(suffix)
        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    private fun normalizeEnum(inputId: String, rawValue: String?, defaultValue: String): String {
        val input = getInputs().firstOrNull { it.id == inputId } ?: return rawValue ?: defaultValue
        return input.normalizeEnumValue(rawValue ?: defaultValue) ?: defaultValue
    }

    private fun formatPackageSummary(context: Context, packageNames: List<String>): String {
        if (packageNames.isEmpty()) return context.getString(R.string.summary_vflow_trigger_notification_any)
        val pm = context.packageManager
        val appNames = packageNames.map { packageName ->
            try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (_: Exception) {
                packageName
            }
        }
        return when (appNames.size) {
            1 -> appNames[0]
            2 -> "${appNames[0]}、${appNames[1]}"
            else -> "${appNames[0]} 等 ${appNames.size} 个应用"
        }
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("通知已收到"))
        val triggerData = context.triggerData as? VDictionary
        val id = (triggerData?.raw?.get("id") as? VString)?.raw ?: ""
        val packageName = (triggerData?.raw?.get("package_name") as? VString)?.raw ?: ""
        val title = (triggerData?.raw?.get("title") as? VString)?.raw ?: ""
        val content = (triggerData?.raw?.get("content") as? VString)?.raw ?: ""

        val notificationObject = NotificationObject(id, packageName, title, content)

        return ExecutionResult.Success(
            outputs = mapOf(
                "notification_object" to VNotification(notificationObject),
                "package_name" to VString(packageName),
                "title" to VString(title),
                "content" to VString(content)
            )
        )
    }

    override fun createSteps(): List<ActionStep> {
        val defaultParams = getInputs()
            .filter { it.defaultValue != null }
            .associate { it.id to it.defaultValue!! }
            .toMutableMap()
        defaultParams["packageNames"] = listOf(appContext.packageName)
        return listOf(ActionStep(id, defaultParams))
    }
}
