// 文件: main/java/com/chaomixian/vflow/permissions/PermissionManager.kt
package com.chaomixian.vflow.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.AccessibilityService
import com.chaomixian.vflow.services.ShellManager
import java.io.DataOutputStream
import androidx.core.net.toUri

object PermissionManager {

    // --- 权限定义 (保持不变) ---
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

    // 定义通知使用权
    val NOTIFICATION_LISTENER_SERVICE = Permission(
        id = "vflow.permission.NOTIFICATION_LISTENER_SERVICE",
        name = "通知使用权",
        description = "允许应用读取和操作状态栏通知，用于实现通知触发器、查找和移除通知等功能。",
        type = PermissionType.SPECIAL
    )

    // 存储权限现在优先请求“所有文件访问权限”
    val STORAGE = Permission(
        id = "vflow.permission.STORAGE",
        name = "文件访问权限",
        description = "允许应用读写 /sdcard/vFlow 目录下的脚本和资源文件。",
        type = PermissionType.SPECIAL, // 改为 SPECIAL，因为 Android 11+ 需要跳转设置
        // 兼容旧版本
        runtimePermissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    )


    // 明确短信权限是一个权限组
    val SMS = Permission(
        id = Manifest.permission.RECEIVE_SMS,
        name = "短信权限",
        description = "用于接收和读取短信，以触发相应的工作流。此权限组包含接收、读取短信。",
        type = PermissionType.RUNTIME,
        // 定义此权限对象实际包含的系统权限列表
        runtimePermissions = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
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

    // 定义精确定位权限
    val LOCATION = Permission(
        id = Manifest.permission.ACCESS_FINE_LOCATION,
        name = "精确定位",
        description = "在部分安卓版本上，获取已保存的Wi-Fi列表需要此权限。",
        type = PermissionType.RUNTIME
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
    val ROOT = Permission(
        id = "vflow.permission.ROOT",
        name = "Root 权限",
        description = "允许应用通过超级用户权限执行底层系统命令。",
        type = PermissionType.SPECIAL
    )

    // 使用情况访问权限
    val USAGE_STATS = Permission(
        id = Manifest.permission.PACKAGE_USAGE_STATS,
        name = "使用情况访问",
        description = "允许 vFlow 读取应用的使用统计信息（如使用时长、最后使用时间），提高 Agent 行为准确度。",
        type = PermissionType.SPECIAL
    )

    // 所有已知特殊权限的列表，用于 UI 展示和快速查找
    val allKnownPermissions = listOf(
        ACCESSIBILITY, NOTIFICATIONS, OVERLAY, NOTIFICATION_LISTENER_SERVICE,
        STORAGE, SMS, BLUETOOTH, WRITE_SETTINGS, LOCATION, SHIZUKU,
        IGNORE_BATTERY_OPTIMIZATIONS, EXACT_ALARM, ROOT, USAGE_STATS
    )


    /** 定义检查策略接口 */
    private interface PermissionStrategy {
        fun isGranted(context: Context, permission: Permission): Boolean
        fun createRequestIntent(context: Context, permission: Permission): Intent?
    }

    /** 标准运行时权限策略 */
    private val runtimeStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            // 特殊处理蓝牙
            if (permission.id == Manifest.permission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            // 特殊处理通知 (Android 13+)
            if (permission.id == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }

            val permsToCheck = permission.runtimePermissions.ifEmpty {
                listOf(permission.id)
            }
            return permsToCheck.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        override fun createRequestIntent(context: Context, permission: Permission): Intent? = null // 运行时权限不通过Intent请求
    }

    /** 无障碍服务策略 */
    private val accessibilityStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            isAccessibilityServiceEnabledInSettings(context)
        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /** 悬浮窗策略 */
    private val overlayStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            Settings.canDrawOverlays(context)

        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri())
    }

    /** 修改系统设置策略 */
    private val writeSettingsStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            Settings.System.canWrite(context)

        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
    }

    /** 电池优化策略 */
    private val batteryStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        }
        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri())
    }

    /** 通知使用权策略 */
    private val notificationListenerStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val componentName = "${context.packageName}/com.chaomixian.vflow.services.VFlowNotificationListenerService"
            return enabledListeners?.contains(componentName) == true
        }
        override fun createRequestIntent(context: Context, permission: Permission) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /** 精确闹钟策略 */
    private val exactAlarmStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
        override fun createRequestIntent(context: Context, permission: Permission) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) else null
    }

    /** Shizuku 策略 */
    private val shizukuStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean = ShellManager.isShizukuActive(context)
        override fun createRequestIntent(context: Context, permission: Permission): Intent? = null // Shizuku 有专门的 API 请求
    }

    /** Root 策略 */
    private val rootStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            // 简单的 Root 检查：尝试执行 'su'
            return try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("exit\n")
                os.flush()
                process.waitFor() == 0
            } catch (_: Exception) { false }
        }
        override fun createRequestIntent(context: Context, permission: Permission): Intent? = null
    }

    // 存储权限策略：Android 11+ 检查 MANAGE_EXTERNAL_STORAGE，否则检查旧权限
    private val storageStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                runtimeStrategy.isGranted(context, permission)
            }
        }
        override fun createRequestIntent(context: Context, permission: Permission): Intent? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
            } else {
                null // 旧版本通过 runtime 请求
            }
        }
    }

    // 使用情况权限策略
    private val usageStatsStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    // 策略映射表
    private val strategies = mapOf(
        ACCESSIBILITY.id to accessibilityStrategy,
        OVERLAY.id to overlayStrategy,
        WRITE_SETTINGS.id to writeSettingsStrategy,
        IGNORE_BATTERY_OPTIMIZATIONS.id to batteryStrategy,
        NOTIFICATION_LISTENER_SERVICE.id to notificationListenerStrategy,
        EXACT_ALARM.id to exactAlarmStrategy,
        SHIZUKU.id to shizukuStrategy,
        ROOT.id to rootStrategy,
        STORAGE.id to storageStrategy,
        USAGE_STATS.id to usageStatsStrategy
    )

    /**
     * 获取单个权限的当前状态。
     * 这里的逻辑变得非常清晰：如果是已知特殊权限，查表；否则默认为运行时权限。
     */
    fun isGranted(context: Context, permission: Permission): Boolean {
        val strategy = strategies[permission.id] ?: runtimeStrategy
        return strategy.isGranted(context, permission)
    }

    /**
     * 获取特殊权限的请求 Intent（供 PermissionActivity 使用）。
     */
    fun getSpecialPermissionIntent(context: Context, permission: Permission): Intent? {
        val strategy = strategies[permission.id]
        return strategy?.createRequestIntent(context, permission)
    }


    fun getMissingPermissions(context: Context, workflow: Workflow): List<Permission> {
        val requiredPermissions = workflow.steps
            .mapNotNull {
                ModuleRegistry.getModule(it.moduleId)?.getRequiredPermissions(it)
            }
            .flatten()
            .distinct()

        return requiredPermissions.filter { !isGranted(context, it) }
    }

    /**
     * 获取应用中所有模块定义的所有权限。
     */
    fun getAllRegisteredPermissions(): List<Permission> {
        return (ModuleRegistry.getAllModules()
            .map { it.getRequiredPermissions(null) }
            .flatten() + IGNORE_BATTERY_OPTIMIZATIONS + STORAGE)
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