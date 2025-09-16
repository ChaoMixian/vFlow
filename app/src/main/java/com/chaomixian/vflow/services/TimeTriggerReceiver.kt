// 文件: main/java/com/chaomixian/vflow/services/TimeTriggerReceiver.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TimeTriggerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeTriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER = "com.chaomixian.vflow.ACTION_TIME_TRIGGER"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER) {
            val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
            if (workflowId == null) return

            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)

            scope.launch {
                try {
                    val workflowManager = WorkflowManager(context.applicationContext)
                    val workflow = workflowManager.getWorkflow(workflowId)

                    if (workflow != null && workflow.isEnabled) {
                        // 执行工作流
                        WorkflowExecutor.execute(workflow, context.applicationContext)
                        // 重新调度下一次闹钟
                        TimeTriggerHandler.rescheduleAlarm(context.applicationContext, workflow)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}