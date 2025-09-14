// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BaseTriggerHandler.kt
// 描述: 触发器处理器的基类，提供了默认的 onWorkflowsUpdated 实现。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class BaseTriggerHandler : ITriggerHandler {

    protected val triggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    protected lateinit var workflowManager: WorkflowManager

    override fun start(context: Context) {
        workflowManager = WorkflowManager(context.applicationContext)
    }

    override fun stop(context: Context) {
        triggerScope.cancel()
    }

}