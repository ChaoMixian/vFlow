// 文件: main/java/com/chaomixian/vflow/services/AccessibilityService.kt
package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AccessibilityService : AccessibilityService() {

    // 为服务创建一个独立的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务连接成功，立即向总线上报自己的实例
        ServiceStateBus.onAccessibilityServiceConnected(this, this)
        DebugLogger.d("VFlowAccessibility", "无障碍服务已连接。")
    }

    /**
     * onAccessibilityEvent 现在将事件推送到 ServiceStateBus 的 Flow 中，
     * 而不是发送广播。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (packageName != null && className != null) {
                // 在协程中发出事件
                serviceScope.launch {
                    ServiceStateBus.postWindowChangeEvent(packageName, className)
                }
            }
        }
    }

    override fun onInterrupt() {
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        serviceScope.cancel() // 服务中断时取消所有协程
        DebugLogger.w("VFlowAccessibility", "无障碍服务被中断。")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        serviceScope.cancel() // 服务解绑时也取消所有协程
        DebugLogger.w("VFlowAccessibility", "无障碍服务已解绑。")
        return super.onUnbind(intent)
    }
}