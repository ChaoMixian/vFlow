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
    private const val VOICE_TRIGGER_MODULE_ID = "vflow.trigger.voice_template"

    private const val ACTION_WORKFLOW_CHANGED = "com.chaomixian.vflow.ACTION_WORKFLOW_CHANGED"
    private const val ACTION_WORKFLOW_REMOVED = "com.chaomixian.vflow.ACTION_WORKFLOW_REMOVED"
    private const val ACTION_RELOAD_TRIGGERS = "com.chaomixian.vflow.ACTION_RELOAD_TRIGGERS"
    private const val EXTRA_TRIGGER_DELTA = "extra_trigger_delta"

    fun notifyWorkflowChanged(context: Context, newWorkflow: Workflow, oldWorkflow: Workflow?) {
        val delta = WorkflowTriggerDelta(
            workflowId = newWorkflow.id,
            oldTriggerRefs = oldWorkflow
                ?.toAutoTriggerSpecs()
                ?.map { WorkflowTriggerRef(triggerId = it.triggerId, type = it.type) }
                .orEmpty()
        )
        val intent = Intent(context, TriggerService::class.java).apply {
            action = ACTION_WORKFLOW_CHANGED
            putExtra(EXTRA_TRIGGER_DELTA, delta)
        }
        context.startService(intent)

        if (newWorkflow.hasTriggerType(VOICE_TRIGGER_MODULE_ID) ||
            oldWorkflow?.hasTriggerType(VOICE_TRIGGER_MODULE_ID) == true
        ) {
            VoiceTriggerService.notifyWorkflowChanged(context, newWorkflow, oldWorkflow)
        }
    }

    fun notifyWorkflowRemoved(context: Context, removedWorkflow: Workflow) {
        val delta = WorkflowTriggerDelta(
            workflowId = removedWorkflow.id,
            oldTriggerRefs = removedWorkflow
                .toAutoTriggerSpecs()
                .map { WorkflowTriggerRef(triggerId = it.triggerId, type = it.type) }
        )
        val intent = Intent(context, TriggerService::class.java).apply {
            action = ACTION_WORKFLOW_REMOVED
            putExtra(EXTRA_TRIGGER_DELTA, delta)
        }
        context.startService(intent)

        if (removedWorkflow.hasTriggerType(VOICE_TRIGGER_MODULE_ID)) {
            VoiceTriggerService.notifyWorkflowRemoved(context, removedWorkflow)
        }
    }

    fun reloadTriggers(context: Context) {
        val intent = Intent(context, TriggerService::class.java).apply {
            action = ACTION_RELOAD_TRIGGERS
        }
        context.startService(intent)

        VoiceTriggerService.reloadTriggers(context)
    }
}
