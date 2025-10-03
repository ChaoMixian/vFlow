// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/NotificationTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class NotificationTriggerModule : BaseModule() {
    override val id = "vflow.trigger.notification"
    override val metadata = ActionMetadata(
        name = "通知触发",
        description = "当收到符合条件的通知时触发工作流。",
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)
    override val uiProvider: ModuleUIProvider = NotificationTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("app_filter", "应用包名", ParameterType.STRING),
        InputDefinition("title_filter", "标题包含", ParameterType.STRING),
        InputDefinition("content_filter", "内容包含", ParameterType.STRING)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("notification_object", "通知对象", NotificationObject.TYPE_NAME),
        OutputDefinition("package_name", "应用包名", TextVariable.TYPE_NAME),
        OutputDefinition("title", "通知标题", TextVariable.TYPE_NAME),
        OutputDefinition("content", "通知内容", TextVariable.TYPE_NAME)
    )

    /**
     * [已修改] 更新摘要逻辑，使其更清晰地显示所有过滤条件。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val appFilter = step.parameters["app_filter"] as? String
        val titleFilter = step.parameters["title_filter"] as? String
        val contentFilter = step.parameters["content_filter"] as? String

        val parts = mutableListOf<Any>("当收到")
        var hasCondition = false

        if (!appFilter.isNullOrBlank()) {
            parts.add("来自 ")
            parts.add(PillUtil.Pill(appFilter, "app_filter"))
            hasCondition = true
        }

        if (!titleFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(" 且")
            parts.add(" 标题含 ")
            parts.add(PillUtil.Pill(titleFilter, "title_filter"))
            hasCondition = true
        }

        if (!contentFilter.isNullOrBlank()) {
            if (hasCondition) parts.add(" 且")
            parts.add(" 内容含 ")
            parts.add(PillUtil.Pill(contentFilter, "content_filter"))
            hasCondition = true
        }

        if (!hasCondition) {
            parts.add("任意")
        }

        parts.add(" 的通知时")
        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("通知已收到"))
        val triggerData = context.triggerData as? DictionaryVariable
        val id = (triggerData?.value?.get("id") as? TextVariable)?.value ?: ""
        val packageName = (triggerData?.value?.get("package_name") as? TextVariable)?.value ?: ""
        val title = (triggerData?.value?.get("title") as? TextVariable)?.value ?: ""
        val content = (triggerData?.value?.get("content") as? TextVariable)?.value ?: ""

        val notificationObject = NotificationObject(id, packageName, title, content)

        return ExecutionResult.Success(
            outputs = mapOf(
                "notification_object" to notificationObject,
                "package_name" to TextVariable(packageName),
                "title" to TextVariable(title),
                "content" to TextVariable(content)
            )
        )
    }
}