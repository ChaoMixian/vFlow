// 文件: main/java/com/chaomixian/vflow/services/KeyEventReceiver.kt
// 描述: 接收由后台脚本发出的按键事件广播，并触发相应的工作流。
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager

class KeyEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KeyEventReceiver"
        private const val ACTION_KEY_EVENT = "com.chaomixian.vflow.KEY_EVENT_TRIGGERED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY_EVENT) {
            return
        }

        val device = intent.getStringExtra("device")
        val keyCode = intent.getStringExtra("key_code")

        if (device.isNullOrBlank() || keyCode.isNullOrBlank()) {
            Log.w(TAG, "接收到无效的按键事件广播: device=$device, keyCode=$keyCode")
            return
        }

        Log.d(TAG, "接收到按键事件: device=$device, keyCode=$keyCode")

        // 查找匹配此事件的工作流并执行
        val workflowManager = WorkflowManager(context)
        val workflowsToExecute = workflowManager.findKeyEventTriggerWorkflows()
            .filter {
                val config = it.triggerConfig
                config?.get("device") == device && config?.get("key_code") == keyCode
            }

        if (workflowsToExecute.isNotEmpty()) {
            Log.i(TAG, "找到 ${workflowsToExecute.size} 个匹配的工作流，准备执行。")
            workflowsToExecute.forEach {
                WorkflowExecutor.execute(it, context)
            }
        } else {
            Log.w(TAG, "未找到匹配此按键事件的工作流。")
        }
    }
}