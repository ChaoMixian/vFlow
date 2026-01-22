// 文件: main/java/com/chaomixian/vflow/core/workflow/module/notification/FindNotificationModule.kt
package com.chaomixian.vflow.core.workflow.module.notification

import android.app.Notification
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.NotificationTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class FindNotificationModule : BaseModule() {
    override val id = "vflow.notification.find"
    override val metadata = ActionMetadata(
        name = "查找通知",
        description = "查找当前状态栏中所有可见的通知。",
        iconRes = R.drawable.rounded_search_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("app_filter", "应用包名", ParameterType.STRING),
        InputDefinition("title_filter", "标题包含", ParameterType.STRING),
        InputDefinition("content_filter", "内容包含", ParameterType.STRING)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("notifications", "找到的通知", VTypeRegistry.LIST.id)
    )

    /**
     * [已修改] 更新摘要逻辑，使其能够反映已设置的过滤条件。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val appFilter = step.parameters["app_filter"]
        val titleFilter = step.parameters["title_filter"]
        val contentFilter = step.parameters["content_filter"]

        val parts = mutableListOf<Any>("查找")
        var hasCondition = false

        if (appFilter is String && appFilter.isNotBlank()) {
            parts.add("来自 ")
            parts.add(PillUtil.createPillFromParam(appFilter, getInputs().find { it.id == "app_filter" }))
            hasCondition = true
        }

        if (titleFilter is String && titleFilter.isNotBlank()) {
            if (hasCondition) parts.add(" 且")
            parts.add(" 标题含 ")
            parts.add(PillUtil.createPillFromParam(titleFilter, getInputs().find { it.id == "title_filter" }))
            hasCondition = true
        }

        if (contentFilter is String && contentFilter.isNotBlank()) {
            if (hasCondition) parts.add(" 且")
            parts.add(" 内容含 ")
            parts.add(PillUtil.createPillFromParam(contentFilter, getInputs().find { it.id == "content_filter" }))
            hasCondition = true
        }

        if (hasCondition) {
            parts.add(" 的通知")
        } else {
            return "查找所有可见的通知"
        }

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val listener = NotificationTriggerHandler.notificationListener
            ?: return ExecutionResult.Failure("服务未连接", "需要通知使用权才能查找通知。")

        val appFilter = (context.magicVariables["app_filter"] as? VString)?.raw ?: context.variables["app_filter"] as? String
        val titleFilter = (context.magicVariables["title_filter"] as? VString)?.raw ?: context.variables["title_filter"] as? String
        val contentFilter = (context.magicVariables["content_filter"] as? VString)?.raw ?: context.variables["content_filter"] as? String

        onProgress(ProgressUpdate("正在查找通知..."))

        val foundNotifications = listener.activeNotifications.mapNotNull { sbn ->
            val notification = sbn.notification
            val extras = notification.extras
            val packageName = sbn.packageName
            val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
            val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            val appMatches = appFilter.isNullOrBlank() || appFilter == packageName
            val titleMatches = titleFilter.isNullOrBlank() || title.contains(titleFilter, ignoreCase = true)
            val contentMatches = contentFilter.isNullOrBlank() || content.contains(contentFilter, ignoreCase = true)

            if (appMatches && titleMatches && contentMatches) {
                NotificationObject(sbn.key, packageName, title, content)
            } else {
                null
            }
        }

        onProgress(ProgressUpdate("找到了 ${foundNotifications.size} 条通知。"))

        return ExecutionResult.Success(mapOf("notifications" to VList(foundNotifications.map { VNotification(it) })))
    }
}