// 文件: main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt

package com.chaomixian.vflow.core.execution

import android.content.Context
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.logic.LOOP_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.WHILE_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.WHILE_PAIRING_ID
import com.chaomixian.vflow.core.workflow.module.logic.LOOP_PAIRING_ID
import com.chaomixian.vflow.services.ExecutionNotificationManager
import com.chaomixian.vflow.services.ExecutionNotificationState
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // 使用 ConcurrentHashMap 来安全地跟踪正在运行的工作流及其对应的 Job
    private val runningWorkflows = ConcurrentHashMap<String, Job>()
    // 用于标记工作流是否被 Stop 信号正常终止
    private val stoppedWorkflows = ConcurrentHashMap<String, Boolean>()

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
            Log.d("WorkflowExecutor", "工作流 '$workflowId' 已被用户手动停止。")
        }
    }

    /**
     * 执行一个工作流。
     * @param workflow 要执行的工作流。
     * @param context Android 上下文。
     * @param triggerData (可选) 触发器传入的外部数据。
     */
    fun execute(workflow: Workflow, context: Context, triggerData: Parcelable? = null) {
        // 如果工作流已在运行，则不允许重复执行
        if (isRunning(workflow.id)) {
            Log.w("WorkflowExecutor", "工作流 '${workflow.name}' 已在运行，忽略新的执行请求。")
            return
        }
        // 重置停止标记
        stoppedWorkflows.remove(workflow.id)

        val job = executorScope.launch {
            // 广播开始执行的状态
            ExecutionStateBus.postState(ExecutionState.Running(workflow.id))
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name} (ID: ${workflow.id})")
            ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(0, "正在开始..."))

            try {
                // 创建并注册执行期间所需的服务
                val services = ExecutionServices()
                ServiceStateBus.getAccessibilityService()?.let { services.add(it) }
                services.add(ExecutionUIService(context.applicationContext))

                val stepOutputs = mutableMapOf<String, Map<String, Any?>>()
                // [新增] 为本次执行创建一个独立的命名变量容器
                val namedVariables = ConcurrentHashMap<String, Any?>()
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

                    // 更新进度通知
                    val progress = (pc * 100) / workflow.steps.size
                    val progressMessage = "步骤 ${pc + 1}/${workflow.steps.size}: ${module.metadata.name}"
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(progress, progressMessage))

                    val executionContext = ExecutionContext(
                        applicationContext = context.applicationContext,
                        variables = step.parameters.toMutableMap(),
                        magicVariables = mutableMapOf(),
                        services = services,
                        allSteps = workflow.steps,
                        currentStepIndex = pc,
                        stepOutputs = stepOutputs,
                        loopStack = loopStack,
                        triggerData = triggerData,
                        namedVariables = namedVariables // [新增] 将容器传递给上下文
                    )

                    // 1. 解析魔法变量 ({{...}})
                    step.parameters.forEach { (key, value) ->
                        if (value is String && value.isMagicVariable()) {
                            val parts = value.removeSurrounding("{{", "}}").split('.')
                            val sourceStepId = parts.getOrNull(0)
                            val sourceOutputId = parts.getOrNull(1)
                            if (sourceStepId != null && sourceOutputId != null) {
                                val sourceOutputValue = stepOutputs[sourceStepId]?.get(sourceOutputId)
                                if (sourceOutputValue != null) {
                                    // 放入 magicVariables 供模块内部使用
                                    executionContext.magicVariables[key] = sourceOutputValue
                                }
                            }
                        }
                    }

                    // 2. [新增] 解析命名变量引用
                    // 遍历模块的静态参数，如果值是一个字符串且在命名变量表里，就替换它
                    executionContext.variables.forEach { (key, value) ->
                        if (value is String && !value.isMagicVariable()) {
                            if (namedVariables.containsKey(value)) {
                                // 如果一个静态参数的值恰好是一个已定义的命名变量的名称
                                // 就将这个命名变量的真实值放入 magicVariables 中
                                // 这样对模块来说，它就像一个魔法变量一样是透明的
                                executionContext.magicVariables[key] = namedVariables[value]
                            }
                        }
                    }

                    step.parameters.forEach { (key, value) ->
                        if (value is String && value.isMagicVariable()) {
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
                    val result = module.execute(executionContext) { progressUpdate ->
                        Log.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progressUpdate.message}")
                        // 在模块内部进度更新时，也刷新通知
                        ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(progress, progressUpdate.message))
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
                            ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("失败: ${result.errorMessage}"))
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
                                            // 此逻辑仅用于固定次数循环 (LoopModule)
                                            val loopState = loopStack.peek()
                                            if (loopState.currentIteration < loopState.totalIterations) {
                                                val loopStartPos = findBlockStartPosition(workflow.steps, pc, setOf(LOOP_START_ID))
                                                if (loopStartPos != -1) {
                                                    // 跳转到循环体内的第一个步骤，而不是循环模块本身
                                                    pc = loopStartPos + 1
                                                } else {
                                                    Log.w("WorkflowExecutor", "找不到循环起点，异常退出循环。")
                                                    pc++ // 异常情况，避免死循环
                                                }
                                            } else {
                                                loopStack.pop() // 循环结束
                                                pc++
                                            }
                                        }
                                    }
                                }
                                is ExecutionSignal.Break -> {
                                    val currentLoopPairingId = findCurrentLoopPairingId(workflow.steps, pc)
                                    val endBlockPosition = findEndBlockPosition(workflow.steps, pc, currentLoopPairingId)
                                    if(endBlockPosition != -1) {
                                        pc = endBlockPosition + 1
                                        Log.d("WorkflowExecutor", "接收到Break信号，跳出循环 '$currentLoopPairingId' 到步骤 $pc")
                                    } else {
                                        Log.w("WorkflowExecutor", "接收到Break信号，但找不到匹配的结束循环块。")
                                        pc++ // 找不到就继续执行，避免卡住
                                    }
                                }
                                is ExecutionSignal.Continue -> {
                                    // 区分 While 和 Loop 的行为
                                    val loopStartPos = findBlockStartPosition(workflow.steps, pc, setOf(LOOP_START_ID, WHILE_START_ID))
                                    if (loopStartPos != -1) {
                                        val loopModuleId = workflow.steps[loopStartPos].moduleId
                                        if (loopModuleId == WHILE_START_ID) {
                                            // 对于 While 循环，跳回到自身以重新评估条件
                                            pc = loopStartPos
                                        } else {
                                            // 对于固定次数循环，跳到其结束块以更新计数器并判断
                                            val endLoopPos = findEndBlockPosition(workflow.steps, loopStartPos, LOOP_PAIRING_ID)
                                            if (endLoopPos != -1) {
                                                pc = endLoopPos
                                            } else {
                                                pc++ // 找不到则异常继续
                                            }
                                        }
                                        Log.d("WorkflowExecutor", "接收到Continue信号，跳转到步骤 $pc")
                                    } else {
                                        Log.w("WorkflowExecutor", "接收到Continue信号，但找不到循环起点。")
                                        pc++
                                    }
                                }
                                is ExecutionSignal.Stop -> {
                                    Log.d("WorkflowExecutor", "接收到Stop信号，正常终止工作流。")
                                    stoppedWorkflows[workflow.id] = true
                                    pc = workflow.steps.size // 设置pc越界以跳出主循环
                                }
                            }
                        }
                    }
                }
                // 如果循环正常结束（没有break），则显示完成通知
                if (pc >= workflow.steps.size) {
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Completed("执行完毕"))
                }

            } catch (e: CancellationException) {
                Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 已被取消。")
                ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("已停止"))
                // 状态在 finally 块中发送
            } catch (e: Exception) {
                Log.e("WorkflowExecutor", "工作流 '${workflow.name}' 执行时发生未捕获的异常。", e)
                ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("执行异常"))
            } finally {
                // 广播最终状态
                val wasStopped = stoppedWorkflows[workflow.id] == true
                val wasCancelled = !isActive && !wasStopped

                if (runningWorkflows.containsKey(workflow.id)) {
                    runningWorkflows.remove(workflow.id)
                    stoppedWorkflows.remove(workflow.id)
                    if(wasCancelled) {
                        ExecutionStateBus.postState(ExecutionState.Cancelled(workflow.id))
                    } else {
                        // 正常结束或Stop信号都视为Finished
                        ExecutionStateBus.postState(ExecutionState.Finished(workflow.id))
                    }
                    Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
                }
                // 延迟后取消通知，给用户时间查看最终状态
                delay(3000)
                ExecutionNotificationManager.cancelNotification()
            }
        }
        // 将 Job 实例存入 map
        runningWorkflows[workflow.id] = job
    }

    /**
     * 辅助函数：向前查找指定ID的积木块的起始位置。
     * 这个函数是为在处理 LoopAction.END 和 Jump 信号时使用的。
     * @param steps 步骤列表。
     * @param startPosition 当前步骤的索引。
     * @param targetIds 目标模块ID的集合。
     * @return 找到的步骤索引，未找到则返回 -1。
     */
    private fun findBlockStartPosition(steps: List<ActionStep>, endPosition: Int, targetIds: Set<String>): Int {
        val endModule = ModuleRegistry.getModule(steps.getOrNull(endPosition)?.moduleId ?: return -1)
        val pairingId = endModule?.blockBehavior?.pairingId ?: return -1
        var openBlocks = 1 // 从结束块开始，计数器为1

        for (i in (endPosition - 1) downTo 0) {
            val currentModule = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            if (currentModule.blockBehavior.pairingId == pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_END -> openBlocks++
                    BlockType.BLOCK_START -> {
                        openBlocks--
                        if (openBlocks == 0 && targetIds.contains(currentModule.id)) return i
                    }
                    else -> {}
                }
            }
        }
        return -1
    }

    /**
     * 辅助函数：查找当前执行点所在的最近的循环块的配对ID。
     * 这个函数专门为处理 Break 信号而设计，它从当前位置向前查找最近的循环块的起始点。
     */
    private fun findCurrentLoopPairingId(steps: List<ActionStep>, position: Int): String? {
        var openCount = 0
        for (i in position downTo 0) {
            val module = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            val behavior = module.blockBehavior
            val isLoopBlock = behavior.pairingId == LOOP_PAIRING_ID || behavior.pairingId == WHILE_PAIRING_ID

            if (isLoopBlock) {
                if (behavior.type == BlockType.BLOCK_END) {
                    openCount++
                } else if (behavior.type == BlockType.BLOCK_START) {
                    if (openCount == 0) {
                        return behavior.pairingId // 找到最内层循环
                    }
                    openCount--
                }
            }
        }
        return null
    }

    /**
     * 辅助函数：查找与给定起始块配对的结束块位置。
     * 这个函数是为处理 Break 信号时，从找到的起始块配对ID，继续向后查找其对应的结束块位置。
     */
    private fun findEndBlockPosition(steps: List<ActionStep>, startPosition: Int, pairingId: String?): Int {
        if (pairingId == null) return -1
        var openBlocks = 1
        for (i in (startPosition + 1) until steps.size) {
            val module = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            val behavior = module.blockBehavior
            if (behavior.pairingId == pairingId) {
                when (behavior.type) {
                    BlockType.BLOCK_START -> openBlocks++
                    BlockType.BLOCK_END -> {
                        openBlocks--
                        if (openBlocks == 0) return i
                    }
                    else -> {}
                }
            }
        }
        return -1
    }
}