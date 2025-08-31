package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AccessibilityService : AccessibilityService() {

    // 移除不稳定的静态实例 companion object

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务连接成功，立即向总线上报自己的实例
        ServiceStateBus.onAccessibilityServiceConnected(this, this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 暂时不处理任何事件
    }

    override fun onInterrupt() {
        // 服务被中断，也视为断开
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // 服务解绑，向总线上报自己已断开
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        return super.onUnbind(intent)
    }
}