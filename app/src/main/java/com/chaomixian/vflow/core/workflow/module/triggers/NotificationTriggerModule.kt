// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/NotificationTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class NotificationTriggerModule : BaseModule() {
    override val id = "vflow.trigger.notification"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_notification_name,
        descriptionStringRes = R.string.module_vflow_trigger_notification_desc,
        name = "通知触发",  // Fallback
        description = "当收到符合条件的通知时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)
    override val uiProvider: ModuleUIProvider = NotificationTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("app_filter", "应用包名", ParameterType.STRING, nameStringRes = R.string.param_vflow_trigger_notification_app_filter_name),
        InputDefinition("title_filter", "标题包含", ParameterType.STRING, nameStringRes = R.string.param_vflow_trigger_notification_title_filter_name),
        InputDefinition("content_filter", "内容包含", ParameterType.STRING, nameStringRes = R.string.param_vflow_trigger_notification_content_filter_name)
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
        val appFilter = step.parameters["app_filter"] as? String
        val titleFilter = step.parameters["title_filter"] as? String
        val contentFilter = step.parameters["content_filter"] as? String

        val prefix = context.getString(R.string.summary_vflow_trigger_notification_prefix)
        val from = context.getString(R.string.summary_vflow_trigger_notification_from)
        val and = context.getString(R.string.summary_vflow_trigger_notification_and)
        val titleContains = context.getString(R.string.summary_vflow_trigger_notification_title_contains)
        val contentContains = context.getString(R.string.summary_vflow_trigger_notification_content_contains)
        val any = context.getString(R.string.summary_vflow_trigger_notification_any)
        val suffix = context.getString(R.string.summary_vflow_trigger_notification_suffix)

        val parts = mutableListOf<Any>(prefix)
        var hasCondition = false

        if (!appFilter.isNullOrBlank()) {
            parts.add(from)
            parts.add(PillUtil.Pill(appFilter, "app_filter"))
            hasCondition = true
        }

        if (!titleFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(and)
            parts.add(titleContains)
            parts.add(PillUtil.Pill(titleFilter, "title_filter"))
            hasCondition = true
        }

        if (!contentFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(and)
            parts.add(contentContains)
            parts.add(PillUtil.Pill(contentFilter, "content_filter"))
            hasCondition = true
        }

        if (!hasCondition) {
            parts.add(any)
        }

        parts.add(suffix)
        return PillUtil.buildSpannable(context, *parts.toTypedArray())
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
                "notification_object" to notificationObject,
                "package_name" to VString(packageName),
                "title" to VString(title),
                "content" to VString(content)
            )
        )
    }
}