// 文件: main/java/com/chaomixian/vflow/services/PermissionGuardianService.kt
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
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.settings.PermissionGuardianActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 权限守护服务
 * 在后台定期检查并自动授予权限，防止权限被系统撤销
 */
class PermissionGuardianService : Service() {

    companion object {
        private const val TAG = "PermissionGuardianService"
        private const val CHANNEL_ID = "permission_guardian_channel"
        private const val NOTIFICATION_ID = 1001

        // 守护检查间隔（毫秒）
        private const val GUARD_CHECK_INTERVAL = 10000L

        // 可守护的权限列表
        private val GUARDABLE_PERMISSIONS = listOf(
            PermissionManager.ACCESSIBILITY,
            PermissionManager.OVERLAY,
            PermissionManager.WRITE_SETTINGS,
            PermissionManager.IGNORE_BATTERY_OPTIMIZATIONS,
            PermissionManager.NOTIFICATION_LISTENER_SERVICE,
            PermissionManager.EXACT_ALARM,
            PermissionManager.STORAGE,
            PermissionManager.USAGE_STATS
        )

        fun start(context: Context) {
            val intent = Intent(context, PermissionGuardianService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PermissionGuardianService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var guardJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.i(TAG, "权限守护服务启动")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startGuard()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 确保守护在运行
        if (guardJob == null || guardJob?.isActive != true) {
            startGuard()
        }
        return START_STICKY // 服务被杀掉后自动重启
    }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DebugLogger.i(TAG, "权限守护服务停止")
        stopGuard()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.permission_guardian_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.permission_guardian_description)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, PermissionGuardianActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.permission_guardian_service_running))
            .setContentText(getString(R.string.permission_guardian_service_protecting))
            .setSmallIcon(R.drawable.ic_workflows)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 启动权限守护
     */
    private fun startGuard() {
        stopGuard() // 先停止之前的

        guardJob = serviceScope.launch {
            // 立即检查并授权一次
            checkAndGrantPermissions()

            // 进入轮询模式
            while (isActive) {
                delay(GUARD_CHECK_INTERVAL)
                checkAndGrantPermissions()
            }
        }

        DebugLogger.i(TAG, "权限守护已启动，检查间隔: ${GUARD_CHECK_INTERVAL}ms")
    }

    /**
     * 停止权限守护
     */
    private fun stopGuard() {
        guardJob?.cancel()
        guardJob = null
    }

    /**
     * 检查并授权所有可守护的权限
     */
    private suspend fun checkAndGrantPermissions() {
        for (permission in GUARDABLE_PERMISSIONS) {
            try {
                // 检查权限是否已授予
                if (!PermissionManager.isGranted(this@PermissionGuardianService, permission)) {
                    DebugLogger.d(TAG, "权限丢失: ${permission.name}，尝试授权...")

                    // 尝试自动授权
                    val success = PermissionManager.autoGrantPermission(this@PermissionGuardianService, permission)

                    if (success) {
                        DebugLogger.i(TAG, "权限恢复成功: ${permission.name}")
                    } else {
                        DebugLogger.w(TAG, "权限恢复失败: ${permission.name}")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "检查权限出错: ${permission.name}", e)
            }
        }
    }
}
