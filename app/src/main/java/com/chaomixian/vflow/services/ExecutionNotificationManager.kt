// 文件: main/java/com/chaomixian/vflow/services/ExecutionNotificationManager.kt
package com.chaomixian.vflow.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.model.Workflow

/**
 * 表示通知的不同状态。
 */
sealed class ExecutionNotificationState {
    data class Running(val progress: Int, val message: String) : ExecutionNotificationState()
    data class Completed(val message: String) : ExecutionNotificationState()
    data class Cancelled(val message: String) : ExecutionNotificationState()
}

/**
 * 管理工作流执行期间的进度通知。
 * 这是一个单例对象，负责创建、更新和移除状态栏通知。
 */
object ExecutionNotificationManager {

    private const val CHANNEL_ID = "workflow_execution_channel"
    private const val CHANNEL_NAME = "工作流执行状态"
    private const val NOTIFICATION_ID = 1998 // 使用一个固定的ID

    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context

    /**
     * 初始化管理器。应在应用启动时调用。
     * @param context 应用上下文。
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    /**
     * 创建通知渠道。仅在 Android O (API 26) 及以上版本需要。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "显示正在执行的工作流的进度"
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 根据给定的状态显示或更新通知。
     * @param workflow 相关的工作流。
     * @param state 通知的当前状态 (Running, Completed, Cancelled)。
     */
    fun updateState(workflow: Workflow, state: ExecutionNotificationState) {
        val prefs = appContext.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("progressNotificationEnabled", true)) {
            return
        }

        // 使用 SDK 版本判断
        // 官方文档指出 API 级别为 35 (Android 15)，但为了兼容预览版，使用 36 也是安全的。
        if (Build.VERSION.SDK_INT >= 36) {
            buildStatusChipNotification(workflow, state)
        } else {
            buildLegacyNotification(workflow, state)
        }
    }

    /**
     * 为 Android 16+ 构建 "Status Chip" 样式的通知。
     * 根据官方文档，不再使用 ProgressStyle，而是直接在 Builder 上设置进度。
     */
    @RequiresApi(36)
    private fun buildStatusChipNotification(workflow: Workflow, state: ExecutionNotificationState) {
        val stopIntent = Intent(appContext, WorkflowActionReceiver::class.java).apply {
            action = WorkflowActionReceiver.ACTION_STOP_WORKFLOW
            putExtra(WorkflowActionReceiver.EXTRA_WORKFLOW_ID, workflow.id)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            appContext,
            workflow.id.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(workflow.name)
            .setSmallIcon(R.drawable.ic_workflows) // 这个是 Status Chip 收起时显示的图标
            .setOnlyAlertOnce(true)

        when (state) {
            is ExecutionNotificationState.Running -> {
                builder
                    .setContentText(state.message)
                    .setOngoing(true)
                    // 请求提升为高优持续性通知 (Status Chip)
                    .setRequestPromotedOngoing(true)
                    // [新增API] 设置在 Status Chip 进度条旁边显示的图标
                    // 直接在 Builder 上设置进度，系统会自动渲染为 Status Chip 进度条
                    .setProgress(100, state.progress, false)
                    // 添加"结束"操作按钮（显示在展开的通知中）
                    .addAction(
                        R.drawable.rounded_close_small_24,
                        "结束",
                        stopPendingIntent
                    )
            }
            is ExecutionNotificationState.Completed -> {
                builder
                    .setContentText(state.message)
                    // 任务完成，不再是持续性通知
                    .setOngoing(false)
                    .setRequestPromotedOngoing(false) // 取消提升请求
                    .setAutoCancel(true)
                    // 通过 setProgress(0, 0, false) 来移除进度条
                    .setProgress(0, 0, false)
                    // (可选) 可以临时将小图标变为完成状态，增强视觉反馈
                    .setSmallIcon(R.drawable.rounded_save_24)
            }
            is ExecutionNotificationState.Cancelled -> {
                builder
                    .setContentText(state.message)
                    // 任务取消，不再是持续性通知
                    .setOngoing(false)
                    .setRequestPromotedOngoing(false) // 取消提升请求
                    .setAutoCancel(true)
                    // 移除进度条
                    .setProgress(0, 0, false)
                    .setSmallIcon(R.drawable.rounded_close_small_24)
            }
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 为旧版本 Android 构建标准进度通知。
     */
    private fun buildLegacyNotification(workflow: Workflow, state: ExecutionNotificationState) {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(workflow.name)
            .setSmallIcon(R.drawable.ic_workflows)
            .setOnlyAlertOnce(true)

        when (state) {
            is ExecutionNotificationState.Running -> {
                builder
                    .setContentText(state.message)
                    .setProgress(100, state.progress, false)
                    .setOngoing(true)
            }
            is ExecutionNotificationState.Completed -> {
                builder
                    .setContentText(state.message)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
            }
            is ExecutionNotificationState.Cancelled -> {
                builder
                    .setContentText(state.message)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
            }
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }


    /**
     * 移除通知。
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}