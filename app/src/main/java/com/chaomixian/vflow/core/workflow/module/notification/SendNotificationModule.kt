package com.chaomixian.vflow.core.workflow.module.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "发送通知"模块。
 * 在系统通知栏中显示一个自定义通知。
 */
class SendNotificationModule : BaseModule() {

    override val id = "vflow.notification.send_notification"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_notification_send_notification_name,
        descriptionStringRes = R.string.module_vflow_notification_send_notification_desc,
        name = "发送通知",  // Fallback
        description = "在系统通知栏中创建一个自定义通知",  // Fallback
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "应用与系统"
    )

    // 需要通知权限
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    companion object {
        private const val CHANNEL_ID = "vflow_custom_notifications"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "title",
            name = "标题",
            nameStringRes = R.string.param_vflow_notification_send_notification_title_name,
            staticType = ParameterType.STRING,
            defaultValue = appContext.getString(R.string.param_vflow_notification_send_notification_title_default),
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true
        ),
        InputDefinition(
            id = "message",
            name = "内容",
            nameStringRes = R.string.param_vflow_notification_send_notification_message_name,
            staticType = ParameterType.STRING,
            defaultValue = appContext.getString(R.string.param_vflow_notification_send_notification_message_default),
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_notification_send_notification_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val titlePill = PillUtil.createPillFromParam(
            step.parameters["title"],
            inputs.find { it.id == "title" }
        )
        val messagePill = PillUtil.createPillFromParam(
            step.parameters["message"],
            inputs.find { it.id == "message" }
        )

        return PillUtil.buildSpannable(context,
            "发送通知: ",
            titlePill,
            " - ",
            messagePill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val titleDefault = appContext.getString(R.string.param_vflow_notification_send_notification_title_default)
        val messageDefault = appContext.getString(R.string.param_vflow_notification_send_notification_message_default)

        val title = context.getVariableAsString("title", titleDefault)
        val message = context.getVariableAsString("message", messageDefault)

        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 为 Android O (API 26) 及以上版本创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = appContext.getString(R.string.channel_vflow_notification_custom)
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workflows)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // 使用唯一的ID来发送通知，避免相互覆盖
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        onProgress(ProgressUpdate("已发送通知: $title"))

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}