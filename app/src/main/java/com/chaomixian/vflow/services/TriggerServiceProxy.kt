// 文件: main/java/com/chaomixian/vflow/services/TriggerServiceProxy.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.workflow.model.Workflow

/**
 * TriggerService 的代理。
 * 用于从应用的其他部分（如 WorkflowManager）安全地与 TriggerService 通信。
 * 它负责构建 Intent 并启动服务来传递指令。
 */
object TriggerServiceProxy {

    private const val ACTION_WORKFLOW_CHANGED = "com.chaomixian.vflow.ACTION_WORKFLOW_CHANGED"
    private const val ACTION_WORKFLOW_REMOVED = "com.chaomixian.vflow.ACTION_WORKFLOW_REMOVED"
    private const val EXTRA_WORKFLOW = "extra_workflow"
    private const val EXTRA_OLD_WORKFLOW = "extra_old_workflow"

    fun notifyWorkflowChanged(context: Context, newWorkflow: Workflow, oldWorkflow: Workflow?) {
        val intent = Intent(context, TriggerService::class.java).apply {
            action = ACTION_WORKFLOW_CHANGED
            putExtra(EXTRA_WORKFLOW, newWorkflow)
            putExtra(EXTRA_OLD_WORKFLOW, oldWorkflow)
        }
        context.startService(intent)
    }

    fun notifyWorkflowRemoved(context: Context, removedWorkflow: Workflow) {
        val intent = Intent(context, TriggerService::class.java).apply {
            action = ACTION_WORKFLOW_REMOVED
            putExtra(EXTRA_WORKFLOW, removedWorkflow)
        }
        context.startService(intent)
    }
}