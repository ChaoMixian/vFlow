package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.chaomixian.vflow.ui.main.MainActivity

class AccessibilityService : AccessibilityService() {

    companion object {
        private var instance: AccessibilityService? = null
        fun getInstance(): AccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("vFlowService", "无障碍服务已连接。")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 暂时不处理任何事件
    }

    override fun onInterrupt() {
        Log.e("vFlowService", "无障碍服务被中断。")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("vFlowService", "无障碍服务已断开。")
        instance = null
        return super.onUnbind(intent)
    }
}