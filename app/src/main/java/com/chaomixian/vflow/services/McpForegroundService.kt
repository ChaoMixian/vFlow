package com.chaomixian.vflow.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.api.ApiService
import com.chaomixian.vflow.core.workflow.WorkflowManager

/**
 * MCP 前台服务
 *
 * MIUI 等系统会在应用退到后台后冻结普通进程，导致 MCP 端口虽然存在但无法响应。
 * 该服务用低优先级常驻通知保持进程处于前台服务状态，确保 AI 客户端在任意界面都能调用 MCP 工具。
 */
class McpForegroundService : Service() {

    companion object {
        private const val TAG = "McpForegroundService"
        private const val CHANNEL_ID = "mcp_server_channel"
        private const val NOTIFICATION_ID = 3003

        fun start(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(TAG, "MCP 前台服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("vflow_api", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        val apiService = ApiService.getInstance(
            applicationContext,
            WorkflowManager(applicationContext)
        )

        if (apiService.serverState.value != ApiService.ServerState.RUNNING) {
            if (!enabled) {
                Log.i(TAG, "MCP 未启用，停止前台服务")
                stopSelf()
                return START_NOT_STICKY
            }
            // 进程被系统回收后由 START_STICKY 拉起时，重新启动 MCP 服务器。
            apiService.startServer()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "MCP 前台服务已停止")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mcp_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mcp_service_notification_text)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mcp_service_notification_title))
            .setContentText(getString(R.string.mcp_service_notification_text))
            .setSmallIcon(R.drawable.ic_workflows)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        return builder.build()
    }
}
