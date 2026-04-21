// 文件: main/java/com/chaomixian/vflow/services/TimeTriggerReceiver.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.workflow.TriggerExecutionCoordinator
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.IntervalTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.TimeTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.IntervalTriggerHandler
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TimeTriggerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeTriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER = "com.chaomixian.vflow.ACTION_TIME_TRIGGER"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_TRIGGER_STEP_ID = "trigger_step_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER) {
            val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
            val triggerStepId = intent.getStringExtra(EXTRA_TRIGGER_STEP_ID)
                ?: intent.getStringExtra(EXTRA_TRIGGER_ID)
            if (workflowId == null || triggerStepId == null) return

            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)

            scope.launch {
                try {
                    val workflowManager = WorkflowManager(context.applicationContext)
                    val workflow = workflowManager.getWorkflow(workflowId)
                    val triggerStep = workflow?.getTrigger(triggerStepId)

                    if (workflow != null && triggerStep != null && workflow.isEnabled) {
                        val appContext = context.applicationContext
                        val resolvedTrigger = TriggerSpec(workflow, triggerStep)
                        TriggerExecutionCoordinator.executeTrigger(
                            context = appContext,
                            trigger = resolvedTrigger
                        )

                        when (triggerStep.moduleId) {
                            TimeTriggerModule().id -> TimeTriggerHandler.rescheduleAlarm(appContext, resolvedTrigger)
                            IntervalTriggerModule().id -> IntervalTriggerHandler.rescheduleAlarm(appContext, resolvedTrigger)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
