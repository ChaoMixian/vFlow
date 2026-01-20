// 文件: main/java/com/chaomixian/vflow/services/CoreManagementService.kt
package com.chaomixian.vflow.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import kotlinx.coroutines.*
import java.io.File

/**
 * vFlowCore 管理服务。
 *
 * 职责：
 * - 作为 Android Service 组件的外壳，接收 Intent 请求
 * - 将具体逻辑委托给 CoreManager 和 CoreLauncher
 * - 处理自动启动失败的通知
 *
 * 不再负责：
 * - Core 的具体启动逻辑（由 CoreLauncher 负责）
 * - 生命周期管理（由 CoreManager 负责）
 * - 健康检查和自动重启（由 CoreManager 负责）
 */
class CoreManagementService : Service() {

    companion object {
        private const val TAG = "CoreManagementService"

        const val ACTION_START_CORE = "com.chaomixian.vflow.action.START_CORE"
        const val ACTION_STOP_CORE = "com.chaomixian.vflow.action.STOP_CORE"
        const val ACTION_RESTART_CORE = "com.chaomixian.vflow.action.RESTART_CORE"
        const val ACTION_CHECK_HEALTH = "com.chaomixian.vflow.action.CHECK_HEALTH"

        const val EXTRA_SHELL_MODE = "com.chaomixian.vflow.extra.SHELL_MODE"
        const val EXTRA_FORCE_RESTART = "com.chaomixian.vflow.extra.FORCE_RESTART"
        const val EXTRA_AUTO_START = "com.chaomixian.vflow.extra.AUTO_START"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CORE, ACTION_RESTART_CORE -> {
                val forceRestart = intent.action == ACTION_RESTART_CORE ||
                        intent.getBooleanExtra(EXTRA_FORCE_RESTART, false)

                val isAutoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)

                DebugLogger.d(TAG, "onStartCommand: action=${intent?.action}, isAutoStart=$isAutoStart, hasShellMode=${intent?.hasExtra(EXTRA_SHELL_MODE)}")

                // 确定启动模式
                val launchMode = determineLaunchMode(intent, isAutoStart)

                // 如果是自动启动且模式不可用，显示通知并返回
                if (isAutoStart && !isLaunchModeAvailable(launchMode)) {
                    val modeName = when (launchMode) {
                        CoreLauncher.LaunchMode.ROOT -> "root"
                        CoreLauncher.LaunchMode.SHIZUKU -> "shizuku"
                        else -> "unknown"
                    }
                    showAutoStartFailedNotification(modeName)
                    return START_NOT_STICKY
                }

                // 启动 Core
                serviceScope.launch {
                    CoreLauncher.launch(
                        context = applicationContext,
                        mode = launchMode,
                        forceRestart = forceRestart
                    )
                }
            }
            ACTION_STOP_CORE -> {
                serviceScope.launch {
                    CoreManager.stop(applicationContext)
                }
            }
            ACTION_CHECK_HEALTH -> {
                serviceScope.launch {
                    if (!CoreManager.healthCheck()) {
                        DebugLogger.w(TAG, "健康检查：Core 未响应，正在启动...")
                        CoreLauncher.launch(applicationContext)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        DebugLogger.d(TAG, "CoreManagementService 已销毁")
    }

    /**
     * 确定启动模式。
     */
    private fun determineLaunchMode(intent: Intent?, isAutoStart: Boolean): CoreLauncher.LaunchMode {
        return when {
            // Intent 中明确指定了模式
            intent?.hasExtra(EXTRA_SHELL_MODE) == true -> {
                val mode = intent.getStringExtra(EXTRA_SHELL_MODE)
                when (mode) {
                    ShellManager.ShellMode.AUTO.name -> CoreLauncher.LaunchMode.AUTO
                    ShellManager.ShellMode.ROOT.name -> CoreLauncher.LaunchMode.ROOT
                    ShellManager.ShellMode.SHIZUKU.name -> CoreLauncher.LaunchMode.SHIZUKU
                    else -> {
                        DebugLogger.w(TAG, "无效的模式名称: $mode，使用 AUTO")
                        CoreLauncher.LaunchMode.AUTO
                    }
                }
            }
            // 自动启动模式或手动启动 - 读取 Core 专用偏好
            else -> {
                val prefs = getSharedPreferences("vFlowPrefs", MODE_PRIVATE)
                val coreMode = prefs.getString("preferred_core_launch_mode", "shizuku")
                when (coreMode) {
                    "root" -> CoreLauncher.LaunchMode.ROOT
                    "shizuku" -> CoreLauncher.LaunchMode.SHIZUKU
                    else -> {
                        DebugLogger.w(TAG, "未知的启动模式: $coreMode，使用 SHIZUKU")
                        CoreLauncher.LaunchMode.SHIZUKU
                    }
                }
            }
        }
    }

    /**
     * 检查启动模式是否可用。
     */
    private fun isLaunchModeAvailable(mode: CoreLauncher.LaunchMode): Boolean {
        return when (mode) {
            CoreLauncher.LaunchMode.ROOT -> {
                val isRoot = ShellManager.isRootAvailable()
                DebugLogger.d(TAG, "Root 可用性检查: $isRoot")
                isRoot
            }
            CoreLauncher.LaunchMode.SHIZUKU -> {
                val isActive = ShellManager.isShizukuActive(applicationContext)
                DebugLogger.d(TAG, "Shizuku 可用性检查: $isActive")
                isActive
            }
            CoreLauncher.LaunchMode.AUTO -> true // AUTO 模式会自动选择
        }
    }

    /**
     * 显示自动启动失败通知（保存的模式不可用）
     */
    private fun showAutoStartFailedNotification(savedMode: String) {
        val notificationId = 3001
        val channelId = "core_autostart_channel"

        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "vFlow Core 自动启动",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "vFlow Core 自动启动状态通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val modeName = if (savedMode == "shizuku") "Shizuku" else "Root"
        val message = "保存的启动方式 ($modeName) 当前不可用，请手动启动 vFlow Core"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("vFlow Core 自动启动失败")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    /**
     * 显示无偏好设置通知
     */
    private fun showNoPreferenceNotification() {
        val notificationId = 3002
        val channelId = "core_autostart_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "vFlow Core 自动启动",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "vFlow Core 自动启动状态通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("vFlow Core 自动启动")
            .setContentText("请先手动启动一次 vFlow Core 以记录您的启动偏好")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}