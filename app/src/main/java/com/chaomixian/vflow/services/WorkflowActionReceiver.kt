// 文件: main/java/com/chaomixian/vflow/services/WorkflowActionReceiver.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.execution.WorkflowExecutor

/**
 * 处理来自通知的广播事件（Android 16+ Live Activity 交互按钮）。
 */
class WorkflowActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_WORKFLOW = "com.chaomixian.vflow.action.STOP_WORKFLOW"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_WORKFLOW -> {
                val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
                if (!workflowId.isNullOrEmpty()) {
                    WorkflowExecutor.stopExecution(workflowId)
                }
            }
        }
    }
}
