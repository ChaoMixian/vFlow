// 文件: main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt

package com.chaomixian.vflow.core.execution

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.modules.logic.LOOP_START_ID
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default)

    fun execute(workflow: Workflow, context: Context) {
        executorScope.launch {
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name}")

            val services = ExecutionServices()
            ServiceStateBus.getAccessibilityService()?.let { services.add(it) }

            val stepOutputs = mutableMapOf<String, Map<String, Any?>>()
            val loopStack = Stack<LoopState>() // 新增：将循环堆栈移到这里
            var pc = 0 // 程序计数器

            while (pc < workflow.steps.size) {
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
                    loopStack = loopStack // 将循环堆栈传递给上下文
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
            Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
        }
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