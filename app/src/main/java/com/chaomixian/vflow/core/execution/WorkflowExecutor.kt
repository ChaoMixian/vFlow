package com.chaomixian.vflow.core.execution

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.modules.logic.LOOP_END_ID
import com.chaomixian.vflow.modules.logic.LOOP_START_ID
import com.chaomixian.vflow.modules.variable.NumberVariable
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Stack

// --- 控制流状态定义 ---
private sealed class ControlFlowState
private data class LoopState(val startPc: Int, val endPc: Int, val totalIterations: Long, var currentIteration: Long = 0) : ControlFlowState()

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default)

    fun execute(workflow: Workflow, context: Context) {
        executorScope.launch {
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name}")

            val services = ExecutionServices()
            ServiceStateBus.getAccessibilityService()?.let { services.add(it) }

            val stepOutputs = mutableMapOf<String, Map<String, Any?>>()
            val controlFlowStack = Stack<ControlFlowState>()
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
                    currentStepIndex = pc
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

                // --- 循环逻辑预处理 ---
                // 在执行LOOP_START之前，先建立循环状态
                if (module.id == LOOP_START_ID) {
                    val countVar = executionContext.magicVariables["count"] ?: executionContext.variables["count"]
                    val count = when (countVar) {
                        is NumberVariable -> countVar.value.toLong()
                        is Number -> countVar.toLong()
                        else -> countVar.toString().toLongOrNull() ?: 0
                    }
                    val endPc = findNextBlockPosition(workflow.steps, pc, setOf(LOOP_END_ID))
                    if (count > 0 && endPc != -1) {
                        controlFlowStack.push(LoopState(pc, endPc, count))
                    } else {
                        pc = endPc + 1 // 次数为0或找不到结尾，直接跳过整个循环
                        continue
                    }
                }

                Log.d("WorkflowExecutor", "[$pc] -> 执行: ${module.metadata.name}")
                val result = module.execute(executionContext) { progress ->
                    Log.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progress.message}")
                }

                var jumped = false
                when (result) {
                    is ExecutionResult.Success -> {
                        if (result.outputs.isNotEmpty()) {
                            stepOutputs[step.id] = result.outputs
                        }
                    }
                    is ExecutionResult.Failure -> {
                        Log.e("WorkflowExecutor", "模块执行失败: ${result.errorTitle} - ${result.errorMessage}")
                        break // 终止工作流
                    }
                    is ExecutionResult.Signal -> {
                        when (val signal = result.signal) {
                            is ExecutionSignal.Jump -> {
                                pc = signal.pc
                                jumped = true
                            }
                        }
                    }
                }

                if (jumped) continue

                // --- 循环逻辑后处理 ---
                if (module.id == LOOP_END_ID && controlFlowStack.isNotEmpty() && controlFlowStack.peek() is LoopState) {
                    val loopState = controlFlowStack.peek() as LoopState
                    loopState.currentIteration++
                    if (loopState.currentIteration < loopState.totalIterations) {
                        pc = loopState.startPc + 1 // 跳回循环体内第一个模块
                        continue
                    } else {
                        controlFlowStack.pop() // 循环结束
                    }
                }

                pc++
            }
            Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
        }
    }

    // 这个函数仍然有用，可以保留在Executor中作为一个公共工具
    private fun findNextBlockPosition(steps: List<ActionStep>, startPosition: Int, targetIds: Set<String>): Int {
        val startModule = ModuleRegistry.getModule(steps[startPosition].moduleId)
        val pairingId = startModule?.blockBehavior?.pairingId ?: return -1
        var openBlocks = 1
        for (i in (startPosition + 1) until steps.size) {
            val currentModule = ModuleRegistry.getModule(steps[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_START -> openBlocks++
                    BlockType.BLOCK_END -> {
                        openBlocks--
                        if (openBlocks == 0 && targetIds.contains(currentModule.id)) return i
                    }
                    BlockType.BLOCK_MIDDLE -> {
                        if (openBlocks == 1 && targetIds.contains(currentModule.id)) return i
                    }
                    else -> {}
                }
            }
        }
        return -1
    }
}