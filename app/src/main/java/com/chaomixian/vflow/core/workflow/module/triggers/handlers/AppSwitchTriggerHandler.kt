package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppSwitchTriggerHandler : ListeningTriggerHandler() {
    private var collectorJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var launcherPackageName: String? = null
    private var inputMethodPackageName: String? = null
    private var lastTriggerPair: Set<String>? = null
    private var lastTriggerTime: Long = 0L

    companion object {
        private const val TAG = "AppSwitchTriggerHandler"
        private const val DEBOUNCE_MS = 1000L
    }

    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller"
    )

    override fun startListening(context: Context) {
        if (collectorJob != null) return
        launcherPackageName = resolveLauncherPackageName(context)
        inputMethodPackageName = resolveInputMethodPackageName(context)
        DebugLogger.d(TAG, "启动应用切换监听...")
        lastForegroundPackage = getCurrentForegroundApp()

        collectorJob = ServiceStateBus.windowChangeEventFlow
            .filter { (packageName, _) -> packageName.isNotBlank() && packageName !in ignoredPackages && packageName != launcherPackageName && packageName != inputMethodPackageName }
            .onEach { (packageName, className) ->
                val previousPackage = lastForegroundPackage
                if (packageName == previousPackage) return@onEach

                lastForegroundPackage = packageName
                if (previousPackage == null) return@onEach

                handleAppSwitch(context, previousPackage, packageName, className)
            }
            .launchIn(triggerScope)
    }

    override fun stopListening(context: Context) {
        collectorJob?.cancel()
        collectorJob = null
        lastForegroundPackage = null
        launcherPackageName = null
        inputMethodPackageName = null
        lastTriggerPair = null
        lastTriggerTime = 0L
        DebugLogger.d(TAG, "应用切换监听已停止。")
    }

    private fun resolveLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun resolveInputMethodPackageName(context: Context): String? {
        val imId = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return imId?.split("/")?.firstOrNull()
    }

    private fun handleAppSwitch(
        context: Context,
        previousPackageName: String,
        packageName: String,
        className: String
    ) {
        val pair = setOf(previousPackageName, packageName)
        val now = System.currentTimeMillis()
        if (pair == lastTriggerPair && now - lastTriggerTime < DEBOUNCE_MS) {
            DebugLogger.d(TAG, "防抖跳过: $previousPackageName -> $packageName, 距上次触发 ${now - lastTriggerTime}ms")
            return
        }
        lastTriggerPair = pair
        lastTriggerTime = now

        listeningTriggers.forEach { trigger ->
            @Suppress("UNCHECKED_CAST")
            val excludedPackages = trigger.parameters["excludedPackageNames"] as? List<String> ?: emptyList()
            if (packageName in excludedPackages || previousPackageName in excludedPackages) return@forEach

            DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}', 应用切换: $previousPackageName -> $packageName")
            executeTrigger(
                context,
                trigger,
                VDictionary(
                    mapOf(
                        "package_name" to VString(packageName),
                        "app_name" to VString(resolveAppName(context, packageName)),
                        "previous_package_name" to VString(previousPackageName),
                        "previous_app_name" to VString(resolveAppName(context, previousPackageName)),
                        "class_name" to VString(className)
                    )
                )
            )
        }
    }

    private fun getCurrentForegroundApp(): String? {
        return try {
            ServiceStateBus.getAccessibilityService()?.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            DebugLogger.w(TAG, "获取当前前台应用失败: ${e.message}")
            null
        }
    }

    private fun resolveAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
