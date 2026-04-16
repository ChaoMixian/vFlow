package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.WeakHashMap

object AccessibilityKeepAliveManager {

    private const val TAG = "AccessibilityKeepAlive"
    private const val PREFS_NAME = "vFlowPrefs"
    private const val PREF_FORCE_KEEP_ALIVE = "forceKeepAliveEnabled"
    private const val PREF_ACCESSIBILITY_GUARD = "accessibilityGuardEnabled"
    private const val QUICK_SETTINGS_RECOVERY_INTERVAL_MS = 3000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayLock = Any()
    private val overlayViews = WeakHashMap<AndroidAccessibilityService, View>()

    @Volatile
    private var lastQuickSettingsRecoveryElapsed = 0L

    fun onAccessibilityConnected(service: AndroidAccessibilityService) {
        if (!isOverlayKeepAliveEnabled(service)) {
            removeAliveOverlay(service)
            return
        }
        addAliveOverlay(service)
    }

    fun onAccessibilityDisconnected(service: AndroidAccessibilityService) {
        removeAliveOverlay(service)
    }

    fun refreshOverlayForCurrentService(context: Context) {
        val service = ServiceStateBus.getAccessibilityService() ?: return
        if (isOverlayKeepAliveEnabled(context.applicationContext)) {
            addAliveOverlay(service)
        } else {
            removeAliveOverlay(service)
        }
    }

    fun onQuickSettingsPanelVisible(context: Context) {
        if (!shouldAttemptQuickSettingsRecovery(context)) {
            return
        }

        ensureTriggerServiceRunning(context)

        if (AccessibilityServiceStatus.isRunning(context)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastQuickSettingsRecoveryElapsed < QUICK_SETTINGS_RECOVERY_INTERVAL_MS) {
                return
            }
            lastQuickSettingsRecoveryElapsed = now
        }

        val appContext = context.applicationContext
        scope.launch {
            val shellReady =
                ShellManager.isShizukuActive(appContext) || ShellManager.isRootAvailable()
            if (!shellReady) {
                DebugLogger.w(TAG, "跳过快捷开关恢复：Shizuku/Root 不可用")
                return@launch
            }

            val restored = ShellManager.ensureAccessibilityServiceRunning(appContext)
            DebugLogger.d(
                TAG,
                "通知栏快捷开关触发恢复: restored=$restored settingsEnabled=${
                    AccessibilityServiceStatus.isEnabledInSettings(appContext)
                }"
            )
        }
    }

    private fun isOverlayKeepAliveEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ACCESSIBILITY_GUARD, false)
    }

    private fun shouldAttemptQuickSettingsRecovery(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_FORCE_KEEP_ALIVE, false)
    }

    private fun ensureTriggerServiceRunning(context: Context) {
        val serviceIntent = Intent(context, TriggerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "启动 TriggerService 失败", t)
        }
    }

    private fun addAliveOverlay(service: AndroidAccessibilityService) {
        removeAliveOverlay(service)

        val overlayView = View(service)
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.START or Gravity.TOP
            width = 1
            height = 1
            packageName = service.packageName
        }

        val windowManager =
            service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        try {
            windowManager.addView(overlayView, layoutParams)
            synchronized(overlayLock) {
                overlayViews[service] = overlayView
            }
            DebugLogger.d(TAG, "已添加 1x1 accessibility overlay")
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "添加 1x1 accessibility overlay 失败", t)
        }
    }

    private fun removeAliveOverlay(service: AndroidAccessibilityService) {
        val overlayView = synchronized(overlayLock) {
            overlayViews.remove(service)
        } ?: return

        val windowManager =
            service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        try {
            windowManager.removeView(overlayView)
            DebugLogger.d(TAG, "已移除 1x1 accessibility overlay")
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "移除 1x1 accessibility overlay 失败", t)
        }
    }
}
