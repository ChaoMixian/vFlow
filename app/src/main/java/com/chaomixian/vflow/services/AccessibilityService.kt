// 文件: main/java/com/chaomixian/vflow/services/AccessibilityService.kt
package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VFlowAccessibility"
    }

    private var serviceScope = createServiceScope()
    private var serviceStateCleaned = false
    private val instanceId = Integer.toHexString(System.identityHashCode(this))
    private var connectionSession = 0
    private var connectedAtElapsed = 0L
    private var eventCountSinceConnect = 0
    private var interruptCountSinceConnect = 0
    private var loggedFirstEventAfterConnect = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!serviceScope.isActive) {
            serviceScope = createServiceScope()
        }
        serviceStateCleaned = false
        connectionSession += 1
        connectedAtElapsed = SystemClock.elapsedRealtime()
        eventCountSinceConnect = 0
        interruptCountSinceConnect = 0
        loggedFirstEventAfterConnect = false
        ServiceStateBus.onAccessibilityServiceConnected(this, this)
        logLifecycle(
            level = "D",
            event = "onServiceConnected",
            extra = "settingsEnabled=${AccessibilityServiceStatus.isEnabledInSettings(this)} " +
                "scopeActive=${serviceScope.isActive} busConnected=${ServiceStateBus.isAccessibilityServiceRunning()}"
        )
    }

    /**
     * onAccessibilityEvent 现在将事件推送到 ServiceStateBus 的 Flow 中，
     * 而不是发送广播。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        eventCountSinceConnect += 1
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        if (!loggedFirstEventAfterConnect) {
            loggedFirstEventAfterConnect = true
            logLifecycle(
                level = "D",
                event = "firstEventAfterConnect",
                extra = "type=${eventTypeName(event.eventType)} package=$packageName class=$className"
            )
        }

        if (packageName == null || className == null) return

        // TYPE_WINDOW_STATE_CHANGED: 窗口状态变化（Activity切换、Dialog显示等）
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            serviceScope.launch {
                ServiceStateBus.postWindowChangeEvent(packageName, className)
            }
        }

        // TYPE_WINDOW_CONTENT_CHANGED: 窗口内容变化（控件出现/消失/更新）
        // 用于检测 vFlow 应用自己界面上的控件变化
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            serviceScope.launch {
                ServiceStateBus.postWindowContentChanged(packageName, className)
            }
        }
    }

    override fun onInterrupt() {
        interruptCountSinceConnect += 1
        logLifecycle(
            level = "W",
            event = "onInterrupt",
            extra = "interruptCount=$interruptCountSinceConnect settingsEnabled=${AccessibilityServiceStatus.isEnabledInSettings(this)}"
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupServiceState(
            event = "onUnbind",
            extra = "intentAction=${intent?.action ?: "null"}"
        )
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupServiceState(event = "onDestroy")
        super.onDestroy()
    }

    private fun cleanupServiceState(event: String, extra: String = "") {
        if (serviceStateCleaned) {
            logLifecycle(level = "D", event = "${event}_ignored", extra = "reason=already_cleaned")
            return
        }
        serviceStateCleaned = true
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        serviceScope.cancel()
        logLifecycle(level = "W", event = event, extra = extra)
    }

    private fun createServiceScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun logLifecycle(level: String, event: String, extra: String = "") {
        val connectedDuration = if (connectedAtElapsed == 0L) -1L else SystemClock.elapsedRealtime() - connectedAtElapsed
        val rootAvailable = runCatching { rootInActiveWindow != null }.getOrDefault(false)
        val baseMessage = buildString {
            append("event=")
            append(event)
            append(" instance=")
            append(instanceId)
            append(" session=")
            append(connectionSession)
            append(" connectedForMs=")
            append(connectedDuration)
            append(" eventCount=")
            append(eventCountSinceConnect)
            append(" rootAvailable=")
            append(rootAvailable)
            append(" serviceInfoNull=")
            append(serviceInfo == null)
            if (extra.isNotBlank()) {
                append(' ')
                append(extra)
            }
        }

        when (level) {
            "W" -> DebugLogger.w(TAG, baseMessage)
            "E" -> DebugLogger.e(TAG, baseMessage)
            "I" -> DebugLogger.i(TAG, baseMessage)
            else -> DebugLogger.d(TAG, baseMessage)
        }
    }

    private fun eventTypeName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            else -> "TYPE_$eventType"
        }
    }
}
