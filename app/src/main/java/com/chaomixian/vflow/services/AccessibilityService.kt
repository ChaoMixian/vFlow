// 文件: main/java/com/chaomixian/vflow/services/AccessibilityService.kt
package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.recording.RecordingNodeUtils
import com.chaomixian.vflow.core.recording.UiRecordingEvent
import com.chaomixian.vflow.core.recording.UiRecordingEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class AccessibilityService : AccessibilityService() {

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
    private val packageActivityCache = mutableMapOf<String, Set<String>>()

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
        AccessibilityKeepAliveManager.onAccessibilityConnected(this)
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
            val confirmedActivityClassName = resolveConfirmedActivityClassName(packageName, className)
            serviceScope.launch {
                ServiceStateBus.postWindowChangeEvent(packageName, className, confirmedActivityClassName)
            }
        }

        // TYPE_WINDOW_CONTENT_CHANGED: 窗口内容变化（控件出现/消失/更新）
        // 用于检测 vFlow 应用自己界面上的控件变化
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            serviceScope.launch {
                ServiceStateBus.postWindowContentChanged(packageName, className)
            }
        }

        // UI 录制事件：仅在录制服务注册了监听者时才做额外处理，
        // 异常全部吞掉，绝不影响原有逻辑。
        emitUiRecordingEvent(event)
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
        AccessibilityKeepAliveManager.onAccessibilityDisconnected(this)
        ServiceStateBus.onAccessibilityServiceDisconnected(this, this)
        serviceScope.cancel()
        logLifecycle(level = "W", event = event, extra = extra)
    }

    private fun createServiceScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun resolveConfirmedActivityClassName(packageName: String, className: String): String? {
        val activityClassNames = packageActivityCache.getOrPut(packageName) {
            loadActivityClassNames(packageName)
        }
        return className.takeIf { it in activityClassNames }
    }

    private fun loadActivityClassNames(packageName: String): Set<String> {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            extractActivityClassNames(packageInfo)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun extractActivityClassNames(packageInfo: PackageInfo): Set<String> {
        val activities = packageInfo.activities ?: return emptySet()
        return activities.mapNotNull { it.name }.toSet()
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

    /**
     * 将无障碍事件转发给 UI 录制服务。
     * 只处理点击、文本变化、滚动、窗口状态变化四类事件。
     */
    @Suppress("DEPRECATION")
    private fun emitUiRecordingEvent(event: AccessibilityEvent) {
        if (!UiRecordingEventBus.hasListener) return
        // 录制悬浮窗自身的按钮（开始/暂停/继续/结束）也会产生无障碍点击事件，
        // 不能把它们写进录制结果，否则生成的工作流会去点击录制悬浮窗。
        if (isOwnRecordingOverlayEvent(event)) return

        val type = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> UiRecordingEvent.CLICK
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> UiRecordingEvent.TEXT_CHANGED
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> UiRecordingEvent.SCROLL
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> UiRecordingEvent.WINDOW_CHANGED
            else -> return
        }

        runCatching {
            var node: AccessibilityNodeInfo? = runCatching { event.source }.getOrNull()
            if (node == null) {
                node = runCatching { rootInActiveWindow }.getOrNull()
            }
            try {
                val recorded = RecordingNodeUtils.buildEvent(event, type, node)
                UiRecordingEventBus.emit(recorded)
            } finally {
                runCatching { node?.recycle() }
            }
        }
    }

    /** 判断事件是否来自 UI 录制悬浮窗自身的控制按钮，避免把控制操作录进目标工作流。 */
    private fun isOwnRecordingOverlayEvent(event: AccessibilityEvent): Boolean {
        if (event.packageName?.toString() != packageName) return false
        val overlayLabels = setOf("开始", "暂停", "继续", "结束", "UI 录制")
        if (event.text.any { it?.toString()?.trim() in overlayLabels }) return true
        val description = event.contentDescription?.toString()?.trim()
        return description in overlayLabels
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
