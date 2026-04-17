package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.triggers.AppPackageTriggerModule
import kotlinx.coroutines.launch

class AppPackageTriggerHandler : ListeningTriggerHandler() {

    private var packageReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "AppPackageTriggerHandler"
    }

    override fun startListening(context: Context) {
        if (packageReceiver != null) return
        DebugLogger.d(TAG, "启动应用安装卸载更新监听...")

        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                handlePackageEvent(ctx, intent)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
        DebugLogger.d(TAG, "应用安装卸载更新监听已启动")
    }

    override fun stopListening(context: Context) {
        packageReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "应用安装卸载更新监听已停止。")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "注销 PackageReceiver 时出错: ${e.message}")
            } finally {
                packageReceiver = null
            }
        }
    }

    private fun handlePackageEvent(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val event = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                AppPackageTriggerModule.EVENT_INSTALLED
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                AppPackageTriggerModule.EVENT_REMOVED
            }
            Intent.ACTION_PACKAGE_REPLACED -> AppPackageTriggerModule.EVENT_UPDATED
            else -> return
        }

        val appName = resolveAppName(context, packageName)
        DebugLogger.d(TAG, "收到应用包事件: $event / $packageName")

        triggerScope.launch {
            listeningTriggers.forEach { trigger ->
                val configuredEvent = trigger.parameters["event"] as? String ?: AppPackageTriggerModule.EVENT_INSTALLED
                @Suppress("UNCHECKED_CAST")
                val configuredPackages = trigger.parameters["packageNames"] as? List<String> ?: emptyList()
                val packageMatches = configuredPackages.isEmpty() || configuredPackages.contains(packageName)

                if (configuredEvent == event && packageMatches) {
                    DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}', 事件: $event / $packageName")
                    executeTrigger(
                        context,
                        trigger,
                        VDictionary(
                            mapOf(
                                "package_name" to VString(packageName),
                                "app_name" to VString(appName),
                                "event" to VString(event)
                            )
                        )
                    )
                }
            }
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
