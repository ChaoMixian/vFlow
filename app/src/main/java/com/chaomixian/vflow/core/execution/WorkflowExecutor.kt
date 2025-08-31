package com.chaomixian.vflow.core.execution

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.modules.logic.ELSE_ID
import com.chaomixian.vflow.modules.logic.IF_END_ID
import com.chaomixian.vflow.modules.logic.LOOP_END_ID
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.NumberVariable
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Stack

// --- 控制流状态定义 ---
private sealed class ControlFlowState
private data class LoopState(val startPc: Int, val endPc: Int, val totalIterations: Long, var currentIteration: Long = 0) : ControlFlowState()
private data class IfState(val jumped: Boolean) : ControlFlowState()
// --- 状态定义结束 ---


object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default)

    fun execute(workflow: Workflow, context: Context) {
        executorScope.launch {
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name}")

            val services = ExecutionServices()
            ServiceStateBus.getAccessibilityService()?.let { services.add(it) }

            val stepOutputs = mutableMapOf<String, Map<String, Any?>>()
            val controlFlowStack = Stack<ControlFlowState>()
            var pc = 0 // 程序计数器 (Program Counter)

            while (pc < workflow.steps.size) {
                val step = workflow.steps[pc]
                val module = ModuleRegistry.getModule(step.moduleId)

                if (module == null) {
                    Log.w("WorkflowExecutor", "未找到模块: ${step.moduleId}，跳过。")
                    pc++
                    continue
                }

                // --- 预处理魔法变量 ---
                val executionContext = ExecutionContext(
                    applicationContext = context.applicationContext,
                    variables = step.parameters.toMutableMap(),
                    magicVariables = mutableMapOf(),
                    services = services
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
                // --- 预处理结束 ---

                Log.d("WorkflowExecutor", "[$pc] -> 执行: ${module.metadata.name}")
                val result = module.execute(executionContext) { progress ->
                    Log.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progress.message}")
                }

                when (result) {
                    is ExecutionResult.Success -> {
                        if (result.outputs.isNotEmpty()) {
                            stepOutputs[step.id] = result.outputs
                        }
                    }
                    is ExecutionResult.Failure -> {
                        Log.e("WorkflowExecutor", "模块 ${module.metadata.name} 执行失败: ${result.errorTitle} - ${result.errorMessage}")
                        break // 出现错误则终止整个工作流
                    }
                }

                // --- 控制流逻辑 ---
                val behavior = module.blockBehavior
                var jumped = false

                when (behavior.type) {
                    BlockType.BLOCK_START -> {
                        when (module.id) {
                            "vflow.logic.if.start" -> {
                                val conditionMet = (result as? ExecutionResult.Success)?.outputs?.get("result") as? BooleanVariable
                                if (conditionMet?.value == false) {
                                    val jumpTo = findNextBlockPosition(workflow.steps, pc, setOf(ELSE_ID, IF_END_ID))
                                    if (jumpTo != -1) {
                                        pc = jumpTo
                                        jumped = true
                                    }
                                }
                                controlFlowStack.push(IfState(jumped))
                            }
                            "vflow.logic.loop.start" -> {
                                val countVar = executionContext.magicVariables["count"] ?: executionContext.variables["count"]
                                val count = when (countVar) {
                                    is NumberVariable -> countVar.value.toLong()
                                    is Number -> countVar.toLong()
                                    else -> countVar.toString().toLongOrNull() ?: 0
                                }
                                val endPc = findNextBlockPosition(workflow.steps, pc, setOf(LOOP_END_ID))
                                if (count <= 0 || endPc == -1) {
                                    pc = endPc + 1 // 跳过整个循环
                                    jumped = true
                                } else {
                                    controlFlowStack.push(LoopState(pc, endPc, count))
                                }
                            }
                        }
                    }
                    BlockType.BLOCK_MIDDLE -> {
                        if (module.id == ELSE_ID && controlFlowStack.peek() is IfState) {
                            if (!(controlFlowStack.peek() as IfState).jumped) {
                                val jumpTo = findNextBlockPosition(workflow.steps, pc, setOf(IF_END_ID))
                                if (jumpTo != -1) {
                                    pc = jumpTo
                                    jumped = true
                                }
                            }
                        }
                    }
                    BlockType.BLOCK_END -> {
                        if (controlFlowStack.isNotEmpty()) {
                            val state = controlFlowStack.peek()
                            if (state is LoopState && module.id == LOOP_END_ID) {
                                state.currentIteration++
                                if (state.currentIteration < state.totalIterations) {
                                    // --- 核心修复：跳转到循环体内的第一个模块，而不是循环开始模块 ---
                                    pc = state.startPc + 1
                                    jumped = true
                                } else {
                                    controlFlowStack.pop()
                                }
                            } else if (state is IfState && module.id == IF_END_ID) {
                                controlFlowStack.pop()
                            }
                        }
                    }
                    BlockType.NONE -> { /* 无操作 */ }
                }

                if (!jumped) {
                    pc++
                }
            }
            Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
        }
    }

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