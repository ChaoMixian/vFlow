// 文件: main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt

package com.chaomixian.vflow.core.execution

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.logic.LOOP_START_ID
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // 使用 ConcurrentHashMap 来安全地跟踪正在运行的工作流及其对应的 Job
    private val runningWorkflows = ConcurrentHashMap<String, Job>()

    /**
     * 检查一个工作流当前是否正在运行。
     * @param workflowId 要检查的工作流的ID。
     * @return 如果正在运行，则返回 true。
     */
    fun isRunning(workflowId: String): Boolean {
        return runningWorkflows.containsKey(workflowId)
    }

    /**
     * 停止一个正在执行的工作流。
     * @param workflowId 要停止的工作流的ID。
     */
    fun stopExecution(workflowId: String) {
        runningWorkflows[workflowId]?.let {
            it.cancel() // 取消 Coroutine Job
            runningWorkflows.remove(workflowId)
            Log.d("WorkflowExecutor", "工作流 '$workflowId' 已被用户手动停止。")
            // 发送取消状态的广播
            executorScope.launch {
                ExecutionStateBus.postState(ExecutionState.Cancelled(workflowId))
            }
        }
    }

    /**
     * 执行一个工作流。
     * @param workflow 要执行的工作流。
     * @param context Android 上下文。
     */
    fun execute(workflow: Workflow, context: Context) {
        // 如果工作流已在运行，则不允许重复执行
        if (isRunning(workflow.id)) {
            Log.w("WorkflowExecutor", "工作流 '${workflow.name}' 已在运行，忽略新的执行请求。")
            return
        }

        val job = executorScope.launch {
            // 广播开始执行的状态
            ExecutionStateBus.postState(ExecutionState.Running(workflow.id))
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name} (ID: ${workflow.id})")

            try {
                // 创建并注册执行期间所需的服务
                val services = ExecutionServices()
                ServiceStateBus.getAccessibilityService()?.let { services.add(it) }
                services.add(ExecutionUIService(context.applicationContext))

                val stepOutputs = mutableMapOf<String, Map<String, Any?>>()
                val loopStack = Stack<LoopState>()
                var pc = 0 // 程序计数器

                while (pc < workflow.steps.size && isActive) { // 检查 coroutine 是否仍然 active
                    val step = workflow.steps[pc]
                    val module = ModuleRegistry.getModule(step.moduleId)
                    if (module == null) {
                        Log.w("WorkflowExecutor", "模块未找到: ${step.moduleId}")
                        pc++
                        continue
                    }

                    val executionContext = ExecutionContext(
                        applicationContext = context.applicationContext,
                        variables = step.parameters.toMutableMap(),
                        magicVariables = mutableMapOf(),
                        services = services,
                        allSteps = workflow.steps,
                        currentStepIndex = pc,
                        stepOutputs = stepOutputs,
                        loopStack = loopStack
                    )

                    step.parameters.forEach { (key, value) ->
                        if (value is String && value.startsWith("{{") && value.endsWith("}}")) {
                            val parts = value.removeSurrounding("{{", "}}").split('.')
                            val sourceStepId = parts.getOrNull(0)
                            val sourceOutputId = parts.getOrNull(1)
                            if (sourceStepId != null && sourceOutputId != null) {
                                val sourceOutputValue = stepOutputs[sourceStepId]?.get(sourceOutputId)
                                if (sourceOutputValue != null) {
                                    executionContext.magicVariables[key] = sourceOutputValue
                                }
                            }
                        }
                    }


                    Log.d("WorkflowExecutor", "[$pc] -> 执行: ${module.metadata.name}")
                    val result = module.execute(executionContext) { progress ->
                        Log.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progress.message}")
                    }

                    when (result) {
                        is ExecutionResult.Success -> {
                            if (result.outputs.isNotEmpty()) {
                                stepOutputs[step.id] = result.outputs
                            }
                            pc++
                        }
                        is ExecutionResult.Failure -> {
                            Log.e("WorkflowExecutor", "模块执行失败: ${result.errorTitle} - ${result.errorMessage}")
                            break // 终止工作流
                        }
                        is ExecutionResult.Signal -> {
                            when (val signal = result.signal) {
                                is ExecutionSignal.Jump -> {
                                    pc = signal.pc
                                }
                                is ExecutionSignal.Loop -> {
                                    when (signal.action) {
                                        LoopAction.START -> pc++ // 循环开始，进入循环体第一个模块
                                        LoopAction.END -> {
                                            val loopState = loopStack.peek()
                                            if (loopState.currentIteration < loopState.totalIterations) {
                                                pc = findBlockStartPosition(workflow.steps, pc, LOOP_START_ID) + 1
                                            } else {
                                                loopStack.pop()
                                                pc++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 已被取消。")
                // 状态已经在 stopExecution 中发送
            } catch (e: Exception) {
                Log.e("WorkflowExecutor", "工作流 '${workflow.name}' 执行时发生未捕获的异常。", e)
            } finally {
                // 确保 job 从 map 中移除，并广播最终状态
                if (runningWorkflows.containsKey(workflow.id)) {
                    runningWorkflows.remove(workflow.id)
                    ExecutionStateBus.postState(ExecutionState.Finished(workflow.id))
                    Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
                }
            }
        }
        // 将 Job 实例存入 map
        runningWorkflows[workflow.id] = job
    }

    private fun findBlockStartPosition(steps: List<ActionStep>, startPosition: Int, targetId: String): Int {
        val pairingId = steps[startPosition].moduleId.let { ModuleRegistry.getModule(it)?.blockBehavior?.pairingId } ?: return -1
        for (i in (startPosition - 1) downTo 0) {
            val currentStep = steps[i]
            val currentModule = ModuleRegistry.getModule(currentStep.moduleId) ?: continue
            if (currentModule.blockBehavior.pairingId == pairingId && currentModule.id == targetId) {
                return i
            }
        }
        return -1
    }
}