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
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.AccessibilityServiceStatus
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataOutputStream
import androidx.core.net.toUri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.core.logging.DebugLogger

object PermissionManager {

    private const val TAG = "PermissionManager"

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    // --- 权限定义 ---
    /**
     * vFlow Core 服务权限。
     * 只要 Core 服务正在运行（无论是通过 Shizuku 还是 Root 启动），此权限即视为满足。
     * 适用于：模拟点击、按键输入、普通 Shell 命令等。
     */
    val CORE = Permission(
        id = "vflow.permission.CORE",
        name = "vFlow Core 服务",
        description = "vFlow 的核心后台服务，用于执行模拟点击、系统操作等高级功能。可以通过 Shizuku 或 Root 启动。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_core,
        descriptionStringRes = R.string.permission_desc_core
    )

    /**
     * vFlow Core Root 权限。
     * 要求 Core 服务必须以 Root 身份运行。
     * 适用于：修改系统文件、高级系统设置等必须 Root 才能执行的操作。
     */
    val CORE_ROOT = Permission(
        id = "vflow.permission.CORE_ROOT",
        name = "vFlow Core (Root)",
        description = "需要 vFlow Core 以 Root 权限运行才能使用的功能。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_core_root,
        descriptionStringRes = R.string.permission_desc_core_root
    )


    val ACCESSIBILITY = Permission(
        id = "vflow.permission.ACCESSIBILITY_SERVICE",
        name = "无障碍服务",
        description = "实现自动化点击、查找、输入等核心功能所必需的权限。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_accessibility,
        descriptionStringRes = R.string.permission_desc_accessibility
    )

    val NOTIFICATIONS = Permission(
        id = Manifest.permission.POST_NOTIFICATIONS,
        name = "通知权限",
        description = "用于显示Toast提示、发送任务结果通知等。",
        type = PermissionType.RUNTIME,
        nameStringRes = R.string.permission_name_notifications,
        descriptionStringRes = R.string.permission_desc_notifications
    )

    val MICROPHONE = Permission(
        id = Manifest.permission.RECORD_AUDIO,
        name = "麦克风权限",
        description = "用于语音转文字等语音输入功能。",
        type = PermissionType.RUNTIME,
        nameStringRes = R.string.permission_name_microphone,
        descriptionStringRes = R.string.permission_desc_microphone
    )

    // 定义悬浮窗权限
    val OVERLAY = Permission(
        id = "vflow.permission.SYSTEM_ALERT_WINDOW",
        name = "悬浮窗权限",
        description = "允许应用在后台执行时显示输入框等窗口，这是实现复杂自动化流程的关键。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_overlay,
        descriptionStringRes = R.string.permission_desc_overlay
    )

    // 定义通知使用权
    val NOTIFICATION_LISTENER_SERVICE = Permission(
        id = "vflow.permission.NOTIFICATION_LISTENER_SERVICE",
        name = "通知使用权",
        description = "允许应用读取和操作状态栏通知，用于实现通知触发器、查找和移除通知等功能。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_notification_listener,
        descriptionStringRes = R.string.permission_desc_notification_listener
    )

    // 存储权限现在优先请求"所有文件访问权限"
    val STORAGE = Permission(
        id = "vflow.permission.STORAGE",
        name = "文件访问权限",
        description = "允许应用读写 /sdcard/vFlow 目录下的脚本和资源文件。",
        type = PermissionType.SPECIAL, // 改为 SPECIAL，因为 Android 11+ 需要跳转设置
        // 兼容旧版本
        runtimePermissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
        nameStringRes = R.string.permission_name_storage,
        descriptionStringRes = R.string.permission_desc_storage
    )


    // 明确短信权限是一个权限组
    val SMS = Permission(
        id = Manifest.permission.RECEIVE_SMS,
        name = "短信权限",
        description = "用于接收和读取短信，以触发相应的工作流。此权限组包含接收、读取短信。",
        type = PermissionType.RUNTIME,
        // 定义此权限对象实际包含的系统权限列表
        runtimePermissions = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
        nameStringRes = R.string.permission_name_sms,
        descriptionStringRes = R.string.permission_desc_sms
    )

    // 蓝牙权限 (Android 12+)
    val BLUETOOTH = Permission(
        id = Manifest.permission.BLUETOOTH_CONNECT,
        name = "蓝牙权限",
        description = "用于控制设备的蓝牙开关状态。",
        type = PermissionType.RUNTIME,
        nameStringRes = R.string.permission_name_bluetooth,
        descriptionStringRes = R.string.permission_desc_bluetooth
    )

    // 修改系统设置权限
    val WRITE_SETTINGS = Permission(
        id = "vflow.permission.WRITE_SETTINGS",
        name = "修改系统设置",
        description = "用于调整屏幕亮度等系统级别的设置。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_write_settings,
        descriptionStringRes = R.string.permission_desc_write_settings
    )

    // 定义精确定位权限
    val LOCATION = Permission(
        id = Manifest.permission.ACCESS_FINE_LOCATION,
        name = "精确定位",
        description = "在部分安卓版本上，获取已保存的Wi-Fi列表需要此权限。",
        type = PermissionType.RUNTIME,
        nameStringRes = R.string.permission_name_location,
        descriptionStringRes = R.string.permission_desc_location
    )

    // 定义 Shizuku 权限
    val SHIZUKU = Permission(
        id = "vflow.permission.SHIZUKU",
        name = "Shizuku",
        description = "允许应用通过 Shizuku 执行需要更高权限的操作，例如 Shell 命令。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_shizuku,
        descriptionStringRes = R.string.permission_desc_shizuku
    )

    // 定义电池优化白名单权限
    val IGNORE_BATTERY_OPTIMIZATIONS = Permission(
        id = "vflow.permission.IGNORE_BATTERY_OPTIMIZATIONS",
        name = "后台运行权限",
        description = "将应用加入电池优化白名单，确保后台触发器（如按键监听）能长时间稳定运行。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_battery,
        descriptionStringRes = R.string.permission_desc_battery
    )

    // 定义精确闹钟权限
    val EXACT_ALARM = Permission(
        id = "vflow.permission.SCHEDULE_EXACT_ALARM",
        name = "闹钟和提醒",
        description = "用于\"定时触发\"功能，确保工作流可以在精确的时间被唤醒和执行。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_alarm,
        descriptionStringRes = R.string.permission_desc_alarm
    )
    val ROOT = Permission(
        id = "vflow.permission.ROOT",
        name = "Root 权限",
        description = "允许应用通过超级用户权限执行底层系统命令。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_root,
        descriptionStringRes = R.string.permission_desc_root
    )

    // 使用情况访问权限
    val USAGE_STATS = Permission(
        id = Manifest.permission.PACKAGE_USAGE_STATS,
        name = "使用情况访问",
        description = "允许 vFlow 读取应用的使用统计信息（如使用时长、最后使用时间），提高 Agent 行为准确度。",
        type = PermissionType.SPECIAL,
        nameStringRes = R.string.permission_name_usage_stats,
        descriptionStringRes = R.string.permission_desc_usage_stats
    )

    // 电话状态权限
    val READ_PHONE_STATE = Permission(
        id = Manifest.permission.READ_PHONE_STATE,
        name = "电话状态",
        description = "用于监听电话状态变化，以实现电话触发器功能。",
        type = PermissionType.RUNTIME,
        nameStringRes = R.string.permission_name_read_phone_state,
        descriptionStringRes = R.string.permission_desc_read_phone_state
    )

    // 所有已知特殊权限的列表，用于 UI 展示和快速查找
    val allKnownPermissions = listOf(
        CORE, CORE_ROOT,
        ACCESSIBILITY, NOTIFICATIONS, MICROPHONE, OVERLAY, NOTIFICATION_LISTENER_SERVICE,
        STORAGE, SMS, READ_PHONE_STATE, BLUETOOTH, WRITE_SETTINGS, LOCATION, SHIZUKU,
        IGNORE_BATTERY_OPTIMIZATIONS, EXACT_ALARM, ROOT, USAGE_STATS
    )


    /** 定义检查策略接口 */
    private interface PermissionStrategy {
        fun isGranted(context: Context, permission: Permission): Boolean
        fun createRequestIntent(context: Context, permission: Permission): Intent?
        /**
         * 自动授予权限（通过 adb 命令）
         * @param context 上下文
         * @return 是否成功授予
         */
        suspend fun autoGrant(context: Context): Boolean = false
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

        override suspend fun autoGrant(context: Context): Boolean {
            // 尝试通过 adb 授予 POST_NOTIFICATIONS 权限（Android 13+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val packageName = context.packageName
                    val command = "appops set $packageName android:post_notifications allow"
                    val result = ShellManager.execShellCommand(context, command)
                    return !result.startsWith("Error")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "自动授予通知权限失败", e)
                }
            }

            // 尝试通过 adb 授予其他运行时权限
            // 注意：这里需要根据具体的 permission.id 来判断授予哪个权限
            // 但由于 runtimeStrategy 是通用的，我们在 autoGrantPermission 方法中会单独处理
            return false
        }
    }

    /** 无障碍服务策略 */
    private val accessibilityStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            return AccessibilityServiceStatus.isRunning(context)
        }
        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        override suspend fun autoGrant(context: Context): Boolean {
            return ShellManager.ensureAccessibilityServiceRunning(context)
        }
    }

    /** 悬浮窗策略 */
    private val overlayStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            Settings.canDrawOverlays(context)

        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri())

        override suspend fun autoGrant(context: Context): Boolean {
            try {
                val packageName = context.packageName
                // Android 6.0+ 使用 appops 授予悬浮窗权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val command = "appops set $packageName android:system_alert_window allow"
                    val result = ShellManager.execShellCommand(context, command)
                    return !result.startsWith("Error")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予悬浮窗权限失败", e)
            }
            return false
        }
    }

    /** 修改系统设置策略 */
    private val writeSettingsStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            Settings.System.canWrite(context)

        override fun createRequestIntent(context: Context, permission: Permission) =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

        override suspend fun autoGrant(context: Context): Boolean {
            try {
                val packageName = context.packageName
                // Android 6.0+ 使用 appops 授予修改设置权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val command = "appops set $packageName android:write_settings allow"
                    val result = ShellManager.execShellCommand(context, command)
                    return !result.startsWith("Error")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予修改设置权限失败", e)
            }
            return false
        }
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

        override suspend fun autoGrant(context: Context): Boolean {
            try {
                val packageName = context.packageName
                // 使用 adb 命令添加电池优化白名单
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val command = "dumpsys deviceidle whitelist +$packageName"
                    val result = ShellManager.execShellCommand(context, command)
                    return !result.startsWith("Error")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予电池优化白名单失败", e)
            }
            return false
        }
    }

    /** 通知使用权策略 */
    private val notificationListenerStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val componentName = "${context.packageName}/com.chaomixian.vflow.services.VFlowNotificationListenerService"
            return enabledListeners?.contains(componentName) == true
        }
        override fun createRequestIntent(context: Context, permission: Permission) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        override suspend fun autoGrant(context: Context): Boolean {
            try {
                val packageName = context.packageName
                val serviceName = "$packageName/com.chaomixian.vflow.services.VFlowNotificationListenerService"
                // 读取当前已启用的监听器列表
                val currentListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""

                // 如果已经在列表中，返回成功
                if (currentListeners.contains(serviceName)) {
                    return true
                }

                // 添加到列表中
                val newListeners = if (currentListeners.isEmpty()) serviceName else "$currentListeners:$serviceName"
                val command = "settings put secure enabled_notification_listeners \"$newListeners\""
                val result = ShellManager.execShellCommand(context, command)
                return !result.startsWith("Error")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予通知使用权失败", e)
            }
            return false
        }
    }

    /** 精确闹钟策略 */
    private val exactAlarmStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
        override fun createRequestIntent(context: Context, permission: Permission) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) else null

        override suspend fun autoGrant(context: Context): Boolean {
            try {
                val packageName = context.packageName
                // Android 12+ 使用 appops 授予精确闹钟权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val command = "appops set $packageName android:schedule_exact_alarm allow"
                    val result = ShellManager.execShellCommand(context, command)
                    return !result.startsWith("Error")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予精确闹钟权限失败", e)
            }
            return false
        }
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

        override suspend fun autoGrant(context: Context): Boolean {
            return try {
                val packageName = context.packageName
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ 使用 appops 授予所有文件访问权限
                    val command = "appops set $packageName android:manage_external_storage allow"
                    val result = ShellManager.execShellCommand(context, command)
                    !result.startsWith("Error")
                } else {
                    // Android 10 及以下，授予存储权限
                    val command = "pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE"
                    val result = ShellManager.execShellCommand(context, command)
                    !result.startsWith("Error")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予存储权限失败", e)
                false
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

        override suspend fun autoGrant(context: Context): Boolean {
            return try {
                val packageName = context.packageName
                // 使用 appops 授予使用情况访问权限
                val command = "appops set $packageName android:get_usage_stats allow"
                val result = ShellManager.execShellCommand(context, command)
                !result.startsWith("Error")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "自动授予使用情况权限失败", e)
                false
            }
        }
    }

    /** vFlow Core 策略 (Shell 或 Root 均可) */
    private val coreStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            // 主线程上只读取缓存状态，避免 localhost 连接超时时卡住 UI。
            val isRunning = if (isMainThread()) {
                VFlowCoreBridge.isConnected
            } else {
                VFlowCoreBridge.ping()
            }
            if (!isRunning) {
                DebugLogger.d("PermissionManager", "CORE 权限检查：vFlowCore 未运行")
            }
            return isRunning
        }
        override fun createRequestIntent(context: Context, permission: Permission): Intent? {
            // 跳转到 Core 管理页面
            val intent = Intent(context, com.chaomixian.vflow.ui.settings.CoreManagementActivity::class.java)
            // 添加标志，指示应该自动启动 Core
            intent.putExtra("auto_start", true)
            return intent
        }
    }

    /** vFlow Core Root 策略 (必须 Root) */
    private val coreRootStrategy = object : PermissionStrategy {
        override fun isGranted(context: Context, permission: Permission): Boolean {
            // 主线程上只读取缓存状态，避免 localhost 连接超时时卡住 UI。
            val isRunning = if (isMainThread()) {
                VFlowCoreBridge.isConnected
            } else {
                VFlowCoreBridge.ping()
            }
            if (!isRunning) {
                DebugLogger.d("PermissionManager", "CORE_ROOT 权限检查失败：vFlowCore 未运行")
                return false
            }

            // 检查权限模式是否为 ROOT
            val isRoot = VFlowCoreBridge.privilegeMode == VFlowCoreBridge.PrivilegeMode.ROOT
            if (!isRoot) {
                DebugLogger.d("PermissionManager", "CORE_ROOT 权限检查失败：不是 ROOT 模式，当前模式: ${VFlowCoreBridge.privilegeMode}")
            }
            return isRoot
        }
        override fun createRequestIntent(context: Context, permission: Permission): Intent? {
            // 跳转到 Core 管理页面
            val intent = Intent(context, com.chaomixian.vflow.ui.settings.CoreManagementActivity::class.java)
            intent.putExtra("auto_start", true)
            return intent
        }
    }

    // 策略映射表
    private val strategies = mapOf(
        CORE.id to coreStrategy,
        CORE_ROOT.id to coreRootStrategy,
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

    /**
     * 自动授予权限
     * @param context 上下文
     * @param permission 要授予的权限
     * @return 是否成功授予
     */
    suspend fun autoGrantPermission(context: Context, permission: Permission): Boolean {
        // 检查是否有可用的 Shell 方式
        val canUseShell = ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()
        if (!canUseShell) {
            DebugLogger.w(TAG, "无可用 Shell 方式，无法自动授予权限")
            return false
        }

        // 如果权限已经授予，直接返回成功
        if (isGranted(context, permission)) {
            DebugLogger.d(TAG, "权限 ${permission.name} 已授予，跳过")
            return true
        }

        DebugLogger.d(TAG, "开始自动授予权限: ${permission.name}")

        // 获取对应的策略并执行自动授予
        val strategy = strategies[permission.id]
        if (strategy != null) {
            val result = strategy.autoGrant(context)
            if (result) {
                DebugLogger.d(TAG, "成功自动授予权限: ${permission.name}")
                return true
            }
        }

        // 如果策略未实现 autoGrant 或失败，尝试授予运行时权限
        // 对于权限组（如 SMS），逐个授予其中的运行时权限
        val permissionsToGrant = if (permission.runtimePermissions.isNotEmpty()) {
            permission.runtimePermissions
        } else {
            listOf(permission.id)
        }

        val packageName = context.packageName
        var allGranted = true

        for (perm in permissionsToGrant) {
            // 检查此权限是否已授予
            if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                continue
            }

            try {
                val command = "pm grant $packageName $perm"
                val result = ShellManager.execShellCommand(context, command)
                if (result.startsWith("Error")) {
                    DebugLogger.w(TAG, "自动授予运行时权限失败: $perm")
                    allGranted = false
                } else {
                    DebugLogger.d(TAG, "成功授予运行时权限: $perm")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "授予运行时权限异常: $perm", e)
                allGranted = false
            }
        }

        if (allGranted) {
            DebugLogger.d(TAG, "成功自动授予权限: ${permission.name}")
        } else {
            DebugLogger.w(TAG, "自动授予权限失败: ${permission.name}")
        }
        return allGranted
    }


    fun getMissingPermissions(context: Context, workflow: Workflow): List<Permission> {
        val requiredPermissions = workflow.allSteps
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
            .flatten() + CORE + CORE_ROOT + IGNORE_BATTERY_OPTIMIZATIONS + STORAGE)
            .distinct()
    }

    /**
     * 检查无障碍服务是否在系统设置中被启用。
     */
    fun isAccessibilityServiceEnabledInSettings(context: Context): Boolean {
        return AccessibilityServiceStatus.isEnabledInSettings(context)
    }

}
