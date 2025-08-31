package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.lang.ref.WeakReference

/**
 * 一个全局的服务状态总线，用于可靠地追踪和提供服务实例。
 * 它取代了不稳定的静态实例方案，解决了服务连接延迟导致的时序问题。
 */
object ServiceStateBus {

    // 使用弱引用来持有服务实例，防止内存泄漏
    private var accessibilityServiceRef: WeakReference<AccessibilityService>? = null

    // 定义广播动作
    const val ACTION_ACCESSIBILITY_SERVICE_STATE_CHANGED = "vflow.action.ACCESSIBILITY_SERVICE_STATE_CHANGED"
    const val EXTRA_IS_CONNECTED = "is_connected"

    /**
     * 当无障碍服务连接或断开时，由服务自身调用此方法来更新状态。
     */
    fun onAccessibilityServiceConnected(context: Context, service: AccessibilityService) {
        accessibilityServiceRef = WeakReference(service)
        sendBroadcast(context, true)
    }

    fun onAccessibilityServiceDisconnected(context: Context) {
        accessibilityServiceRef?.clear()
        accessibilityServiceRef = null
        sendBroadcast(context, false)
    }

    /**
     * 获取当前可用的无障碍服务实例。
     * 这是模块执行器获取服务的最可靠方式。
     */
    fun getAccessibilityService(): AccessibilityService? {
        return accessibilityServiceRef?.get()
    }

    /**
     * 检查无障碍服务当前是否正在运行。
     */
    fun isAccessibilityServiceRunning(): Boolean {
        return getAccessibilityService() != null
    }

    /**
     * 发送服务状态变更的本地广播。
     * UI层可以监听此广播来实时更新界面。
     */
    private fun sendBroadcast(context: Context, isConnected: Boolean) {
        val intent = Intent(ACTION_ACCESSIBILITY_SERVICE_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_CONNECTED, isConnected)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}