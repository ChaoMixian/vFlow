// 文件: main/java/com/chaomixian/vflow/services/ServiceStateBus.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.lang.ref.WeakReference

/**
 * 一个全局的服务状态总线，现在也负责分发无障碍事件。
 */
object ServiceStateBus {

    // 使用弱引用来持有服务实例，防止内存泄漏
    private var accessibilityServiceRef: WeakReference<AccessibilityService>? = null

    // 创建一个 SharedFlow 用于广播窗口变化事件 (包名, 类名)
    private val _windowChangeEventFlow = MutableSharedFlow<Pair<String, String>>()
    val windowChangeEventFlow = _windowChangeEventFlow.asSharedFlow()

    // 创建一个 SharedFlow 用于广播窗口内容变化事件 (包名, 类名)
    private val _windowContentChangedFlow = MutableSharedFlow<Pair<String, String>>()
    val windowContentChangedFlow = _windowContentChangedFlow.asSharedFlow()

    // 缓存最后一个窗口类名，供 UIInspector 使用
    var lastWindowClassName: String? = null
        private set

    // 缓存最后一个包名，供模块使用
    var lastWindowPackageName: String? = null
        private set

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
     * 由 AccessibilityService 调用，用于发出窗口变化事件。
     */
    suspend fun postWindowChangeEvent(packageName: String, className: String) {
        lastWindowClassName = className
        lastWindowPackageName = packageName
        _windowChangeEventFlow.emit(packageName to className)
    }

    /**
     * 由 AccessibilityService 调用，用于发出窗口内容变化事件。
     */
    suspend fun postWindowContentChanged(packageName: String, className: String) {
        lastWindowClassName = className
        lastWindowPackageName = packageName
        _windowContentChangedFlow.emit(packageName to className)
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