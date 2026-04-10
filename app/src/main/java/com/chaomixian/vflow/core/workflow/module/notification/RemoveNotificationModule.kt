// 文件: main/java/com/chaomixian/vflow/core/workflow/module/notification/RemoveNotificationModule.kt
package com.chaomixian.vflow.core.workflow.module.notification

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.NotificationTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class RemoveNotificationModule : BaseModule() {
    override val id = "vflow.notification.remove"
    override val metadata = ActionMetadata(
        name = "移除通知",  // Fallback
        nameStringRes = R.string.module_vflow_notification_remove_name,
        description = "从状态栏移除一个或多个通知。",  // Fallback
        descriptionStringRes = R.string.module_vflow_notification_remove_desc,
        iconRes = R.drawable.rounded_close_small_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标通知",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NOTIFICATION.id, VTypeRegistry.LIST.id),
            nameStringRes = R.string.param_vflow_notification_remove_target_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_notification_remove_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetPill = PillUtil.createPillFromParam(step.parameters["target"], getInputs().find { it.id == "target" })
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_notification_remove), targetPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val listener = NotificationTriggerHandler.notificationListener
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_notification_remove_service_unavailable),
                appContext.getString(R.string.error_vflow_notification_remove_need_permission)
            )

        val notificationsToRemove = context.getVariableAsNotificationList("target")
            .map { it.notification }

        if (notificationsToRemove.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_notification_remove_invalid_param),
                appContext.getString(R.string.error_vflow_notification_remove_invalid_target)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_notification_remove_removing, notificationsToRemove.size)))

        notificationsToRemove.forEach {
            listener.cancelNotification(it.id)
        }

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
