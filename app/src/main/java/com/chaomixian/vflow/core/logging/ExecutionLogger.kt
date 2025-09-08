// 文件: main/java/com/chaomixian/vflow/core/logging/ExecutionLogger.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.workflow.WorkflowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 监听工作流执行状态并记录日志。
 */
object ExecutionLogger {
    private lateinit var workflowManager: WorkflowManager

    fun initialize(context: Context, scope: CoroutineScope) {
        workflowManager = WorkflowManager(context)
        scope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                when (state) {
                    is ExecutionState.Finished -> {
                        val workflow = workflowManager.getWorkflow(state.workflowId)
                        if (workflow != null) {
                            LogManager.addLog(
                                LogEntry(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    timestamp = System.currentTimeMillis(),
                                    status = LogStatus.SUCCESS,
                                    message = "执行完毕"
                                )
                            )
                        }
                    }
                    is ExecutionState.Cancelled -> {
                        val workflow = workflowManager.getWorkflow(state.workflowId)
                        if (workflow != null) {
                            LogManager.addLog(
                                LogEntry(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    timestamp = System.currentTimeMillis(),
                                    status = LogStatus.CANCELLED,
                                    message = "执行已停止"
                                )
                            )
                        }
                    }
                    is ExecutionState.Running -> {
                        // 通常我们只记录结束状态，但也可以在这里记录开始
                    }
                }
            }
        }
    }
}