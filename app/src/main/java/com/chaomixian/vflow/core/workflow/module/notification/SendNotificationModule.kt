package com.chaomixian.vflow.core.workflow.module.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “发送通知”模块。
 * 在系统通知栏中显示一个自定义通知。
 */
class SendNotificationModule : BaseModule() {

    override val id = "vflow.notification.send_notification"
    override val metadata = ActionMetadata(
        name = "发送通知",
        description = "在系统通知栏中创建一个自定义通知。",
        iconRes = R.drawable.rounded_notifications_unread_24, // 使用新图标
        category = "应用与系统"
    )

    // 需要通知权限
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    companion object {
        private const val CHANNEL_ID = "vflow_custom_notifications"
        private const val CHANNEL_NAME = "自定义通知"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "title",
            name = "标题",
            staticType = ParameterType.STRING,
            defaultValue = "vFlow 通知",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.Companion.TYPE_NAME)
        ),
        InputDefinition(
            id = "message",
            name = "内容",
            staticType = ParameterType.STRING,
            defaultValue = "这是一条来自 vFlow 的消息。",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.Companion.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.Companion.TYPE_NAME)
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
        val title = (context.magicVariables["title"] as? TextVariable)?.value
            ?: context.variables["title"] as? String ?: "vFlow 通知"
        val message = (context.magicVariables["message"] as? TextVariable)?.value
            ?: context.variables["message"] as? String ?: ""

        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 为 Android O (API 26) 及以上版本创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workflows) // 使用一个通用的应用图标
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // 使用唯一的ID来发送通知，避免相互覆盖
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        onProgress(ProgressUpdate("已发送通知: $title"))

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}