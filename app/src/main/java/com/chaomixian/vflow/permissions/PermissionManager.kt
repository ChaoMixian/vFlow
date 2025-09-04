package com.chaomixian.vflow.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.AccessibilityService

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

    // 新增：定义悬浮窗权限
    val OVERLAY = Permission(
        id = "vflow.permission.SYSTEM_ALERT_WINDOW",
        name = "悬浮窗权限",
        description = "允许应用在后台执行时显示输入框等窗口，这是实现复杂自动化流程的关键。",
        type = PermissionType.SPECIAL
    )


    /**
     * 获取单个权限的当前状态。
     */
    fun isGranted(context: Context, permission: Permission): Boolean {
        return when (permission.id) {
            ACCESSIBILITY.id -> isAccessibilityServiceEnabledInSettings(context)
            // 新增：检查悬浮窗权限
            OVERLAY.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true // 6.0以下版本默认授予
            }
            NOTIFICATIONS.id -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                } else {
                    true
                }
            }
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
        return ModuleRegistry.getAllModules()
            .map { it.requiredPermissions }
            .flatten()
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