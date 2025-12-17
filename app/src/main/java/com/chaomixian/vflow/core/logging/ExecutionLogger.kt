// 文件: main/java/com/chaomixian/vflow/core/logging/ExecutionLogger.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
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
                    is ExecutionState.Failure -> {
                        // 当工作流执行失败时
                        val workflow = workflowManager.getWorkflow(state.workflowId)
                        if (workflow != null) {
                            // 尝试获取失败步骤的模块名称，以提供更详细的日志
                            val failedStep = workflow.steps.getOrNull(state.stepIndex)
                            val moduleName = failedStep?.let { ModuleRegistry.getModule(it.moduleId)?.metadata?.name } ?: "未知模块"
                            LogManager.addLog(
                                LogEntry(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    timestamp = System.currentTimeMillis(),
                                    status = LogStatus.FAILURE,
                                    // 在日志消息中包含失败的步骤和模块名称
                                    message = "在步骤 #${state.stepIndex} (${moduleName}) 执行失败"
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