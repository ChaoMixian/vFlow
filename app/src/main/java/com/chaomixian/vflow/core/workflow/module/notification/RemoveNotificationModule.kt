// 文件: main/java/com/chaomixian/vflow/core/workflow/module/notification/RemoveNotificationModule.kt
package com.chaomixian.vflow.core.workflow.module.notification

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.NotificationTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class RemoveNotificationModule : BaseModule() {
    override val id = "vflow.notification.remove"
    override val metadata = ActionMetadata(
        name = "移除通知",
        description = "从状态栏移除一个或多个通知。",
        iconRes = R.drawable.rounded_close_small_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_LISTENER_SERVICE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标通知",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NotificationObject.TYPE_NAME, ListVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetPill = PillUtil.createPillFromParam(step.parameters["target"], getInputs().find { it.id == "target" })
        return PillUtil.buildSpannable(context, "移除通知 ", targetPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val listener = NotificationTriggerHandler.notificationListener
            ?: return ExecutionResult.Failure("服务未连接", "需要通知使用权才能移除通知。")

        val target = context.magicVariables["target"]

        val notificationsToRemove = when (target) {
            is NotificationObject -> listOf(target)
            is VList -> target.raw.filterIsInstance<NotificationObject>()
            else -> emptyList()
        }

        if (notificationsToRemove.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "输入不是有效的通知对象或通知列表。")
        }

        onProgress(ProgressUpdate("正在移除 ${notificationsToRemove.size} 条通知..."))

        notificationsToRemove.forEach {
            listener.cancelNotification(it.id)
        }

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}