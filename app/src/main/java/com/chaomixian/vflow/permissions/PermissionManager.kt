// 文件: main/java/com/chaomixian/vflow/permissions/PermissionManager.kt
package com.chaomixian.vflow.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.AccessibilityService
import com.chaomixian.vflow.services.ShizukuManager
import rikka.shizuku.Shizuku

object PermissionManager {

    val ACCESSIBILITY = Permission(
        id = "vflow.permission.ACCESSIBILITY_SERVICE",
        name = "无障碍服务",
        description = "实现自动化点击、查找、输入等核心功能所必需的权限。",
        type = PermissionType.SPECIAL
    )

    val NOTIFICATIONS = Permission(
        id = Manifest.permission.POST_NOTIFICATIONS,
        name = "通知权限",
        description = "用于显示Toast提示、发送任务结果通知等。",
        type = PermissionType.RUNTIME
    )

    // 定义悬浮窗权限
    val OVERLAY = Permission(
        id = "vflow.permission.SYSTEM_ALERT_WINDOW",
        name = "悬浮窗权限",
        description = "允许应用在后台执行时显示输入框等窗口，这是实现复杂自动化流程的关键。",
        type = PermissionType.SPECIAL
    )

    // 定义存储权限
    val STORAGE = Permission(
        id = Manifest.permission.READ_EXTERNAL_STORAGE, // 使用读取权限作为ID
        name = "存储权限",
        description = "用于读取和保存图片、文件等数据。",
        type = PermissionType.RUNTIME
    )

    // 蓝牙权限 (Android 12+)
    val BLUETOOTH = Permission(
        id = Manifest.permission.BLUETOOTH_CONNECT,
        name = "蓝牙权限",
        description = "用于控制设备的蓝牙开关状态。",
        type = PermissionType.RUNTIME
    )

    // 修改系统设置权限
    val WRITE_SETTINGS = Permission(
        id = "vflow.permission.WRITE_SETTINGS",
        name = "修改系统设置",
        description = "用于调整屏幕亮度等系统级别的设置。",
        type = PermissionType.SPECIAL
    )

    // 定义 Shizuku 权限
    val SHIZUKU = Permission(
        id = "vflow.permission.SHIZUKU",
        name = "Shizuku",
        description = "允许应用通过 Shizuku 执行需要更高权限的操作，例如 Shell 命令。",
        type = PermissionType.SPECIAL
    )

    // 定义电池优化白名单权限
    val IGNORE_BATTERY_OPTIMIZATIONS = Permission(
        id = "vflow.permission.IGNORE_BATTERY_OPTIMIZATIONS",
        name = "后台运行权限",
        description = "将应用加入电池优化白名单，确保后台触发器（如按键监听）能长时间稳定运行。",
        type = PermissionType.SPECIAL
    )

    // 定义精确闹钟权限
    val EXACT_ALARM = Permission(
        id = "vflow.permission.SCHEDULE_EXACT_ALARM",
        name = "闹钟和提醒",
        description = "用于“定时触发”功能，确保工作流可以在精确的时间被唤醒和执行。",
        type = PermissionType.SPECIAL
    )


    /**
     * 获取单个权限的当前状态。
     */
    fun isGranted(context: Context, permission: Permission): Boolean {
        return when (permission.id) {
            ACCESSIBILITY.id -> isAccessibilityServiceEnabledInSettings(context)
            // 检查悬浮窗权限
            OVERLAY.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true // 6.0以下版本默认授予
            }
            WRITE_SETTINGS.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else {
                true // 6.0以下版本默认授予
            }
            // 检查电池优化
            IGNORE_BATTERY_OPTIMIZATIONS.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true // 6.0以下版本无此限制
                }
            }
            // 检查精确闹钟权限
            EXACT_ALARM.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true // Android 12 以下版本默认授予
                }
            }
            NOTIFICATIONS.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                } else {
                    true
                }
            }
            // 检查存储权限
            STORAGE.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13 (API 33) 及以上，检查新的图片权限
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    // 旧版本，检查旧的存储权限
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            BLUETOOTH.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true // 12以下版本不需要此运行时权限
                }
            }
            // Shizuku 权限检查逻辑
            SHIZUKU.id -> ShizukuManager.isShizukuActive(context)
            else -> {
                ContextCompat.checkSelfPermission(context, permission.id) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * 获取一个工作流所需但尚未授予的所有权限。
     */
    fun getMissingPermissions(context: Context, workflow: Workflow): List<Permission> {
        val requiredPermissions = workflow.steps
            .mapNotNull { ModuleRegistry.getModule(it.moduleId)?.requiredPermissions }
            .flatten()
            .distinct()

        return requiredPermissions.filter { !isGranted(context, it) }
    }

    /**
     * 获取应用中所有模块定义的所有权限。
     */
    fun getAllRegisteredPermissions(): List<Permission> {
        // 将电池优化权限也加入到权限管理器中
        return (ModuleRegistry.getAllModules()
            .map { it.requiredPermissions }
            .flatten() + IGNORE_BATTERY_OPTIMIZATIONS)
            .distinct()
    }

    /**
     * 检查无障碍服务是否在系统设置中被启用。
     */
    fun isAccessibilityServiceEnabledInSettings(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${AccessibilityService::class.java.name}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServicesSetting)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}