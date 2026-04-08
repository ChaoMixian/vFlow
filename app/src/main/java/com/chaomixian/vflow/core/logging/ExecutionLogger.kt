// 文件: main/java/com/chaomixian/vflow/core/logging/ExecutionLogger.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.module.ModuleRegistry
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
                // 根据不同的执行状态，记录相应的日志
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
                                    message = context.getString(R.string.log_message_execution_completed),
                                    detailedLog = state.detailedLog,
                                    messageKey = LogMessageKey.EXECUTION_COMPLETED
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
                                    message = context.getString(R.string.log_message_execution_cancelled),
                                    detailedLog = state.detailedLog,
                                    messageKey = LogMessageKey.EXECUTION_CANCELLED
                                )
                            )
                        }
                    }
                    is ExecutionState.Failure -> {
                        // 当工作流执行失败时
                        val workflow = workflowManager.getWorkflow(state.workflowId)
                        if (workflow != null) {
                            // 尝试获取失败步骤的模块名称，以提供更详细的日志
                            val failedStep = workflow.steps.getOrNull(state.stepIndex)
                            val moduleName = failedStep?.let {
                                ModuleRegistry.getModule(it.moduleId)?.metadata?.getLocalizedName(context)
                            } ?: context.getString(R.string.ui_inspector_unknown)
                            val stepNumber = (state.stepIndex + 1).toString()
                            LogManager.addLog(
                                LogEntry(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    timestamp = System.currentTimeMillis(),
                                    status = LogStatus.FAILURE,
                                    // 在日志消息中包含失败的步骤和模块名称
                                    message = context.getString(
                                        R.string.log_message_execution_failed_at_step,
                                        stepNumber,
                                        moduleName
                                    ),
                                    detailedLog = state.detailedLog,
                                    messageKey = LogMessageKey.EXECUTION_FAILED_AT_STEP,
                                    messageArgs = listOf(stepNumber, moduleName)
                                )
                            )
                        }
                    }
                    is ExecutionState.Running -> {
                        // 通常只记录结束状态，但也可以在这里记录开始
                    }
                }
            }
        }
    }
}
