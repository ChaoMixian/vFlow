// 文件: main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt
package com.chaomixian.vflow.core.execution

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.logic.*
import com.chaomixian.vflow.services.ExecutionNotificationManager
import com.chaomixian.vflow.services.ExecutionNotificationState
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.ui.workflow_editor.ActionEditorSheet
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import com.chaomixian.vflow.core.logging.DebugLogger as GlobalDebugLogger

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // 使用 ConcurrentHashMap 来安全地跟踪正在运行的工作流及其对应的 Job
    private val runningWorkflows = ConcurrentHashMap<String, Job>()
    // 用于标记工作流是否被 Stop 信号正常终止
    private val stoppedWorkflows = ConcurrentHashMap<String, Boolean>()

    // 用于存储每个正在运行的工作流的详细日志
    private val executionLogs = ConcurrentHashMap<String, StringBuilder>()

    // 用于在协程间传递当前 Root Workflow ID 的 ThreadLocal
    private val currentRootWorkflowId = ThreadLocal<String>()

    /**
     * 本地 DebugLogger 代理对象。
     * 它拦截所有的日志调用，转发给全局 Logger，同时记录到当前工作流的 executionLogs 中。
     */
    private object DebugLogger {
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        fun d(tag: String, message: String, throwable: Throwable? = null) {
            GlobalDebugLogger.d(tag, message, throwable)
            appendToLog("D", tag, message, throwable)
        }

        fun i(tag: String, message: String, throwable: Throwable? = null) {
            GlobalDebugLogger.i(tag, message, throwable)
            appendToLog("I", tag, message, throwable)
        }

        fun w(tag: String, message: String, throwable: Throwable? = null) {
            GlobalDebugLogger.w(tag, message, throwable)
            appendToLog("W", tag, message, throwable)
        }

        fun e(tag: String, message: String, throwable: Throwable? = null) {
            GlobalDebugLogger.e(tag, message, throwable)
            appendToLog("E", tag, message, throwable)
        }

        private fun appendToLog(level: String, tag: String, message: String, throwable: Throwable?) {
            // 从 ThreadLocal 中获取当前上下文的工作流 ID
            val workflowId = currentRootWorkflowId.get() ?: return
            val sb = executionLogs[workflowId] ?: return

            val time = dateFormat.format(Date())
            sb.append("[$time] $level/$tag: $message\n")
            if (throwable != null) {
                sb.append(throwable.stackTraceToString()).append("\n")
            }
        }
    }

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
            // 这里无法直接使用本地 DebugLogger 记录到日志，因为不在协程上下文中
            GlobalDebugLogger.d("WorkflowExecutor", "工作流 '$workflowId' 已被用户手动停止。")
        }
    }

    /**
     * 执行一个工作流。这是最外层的入口点。
     * @param workflow 要执行的工作流。
     * @param context Android 上下文。
     * @param triggerData (可选) 触发器传入的外部数据。
     */
    fun execute(workflow: Workflow, context: Context, triggerData: Parcelable? = null) {
        // 如果工作流已在运行，则不允许重复执行
        if (isRunning(workflow.id)) {
            // 这里无法直接使用本地 DebugLogger 记录到日志，因为不在协程上下文中
            GlobalDebugLogger.w("WorkflowExecutor", "工作流 '${workflow.name}' 已在运行，忽略新的执行请求。")
            return
        }
        // 重置停止标记
        stoppedWorkflows.remove(workflow.id)

        // 初始化日志缓冲区
        executionLogs[workflow.id] = StringBuilder().apply {
            append("--- 开始执行: ${workflow.name} ---\n")
            append("ID: ${workflow.id}\n")
            if (triggerData != null) append("触发数据: $triggerData\n")
        }

        val job = executorScope.launch {
            // 将 workflow.id 注入到当前协程及其子协程的上下文中
            // 这样，在此作用域内调用的所有本地 DebugLogger 方法都能通过 ThreadLocal 获取到 ID
            withContext(currentRootWorkflowId.asContextElement(value = workflow.id)) {

                // 广播开始执行的状态，初始索引为-1表示准备阶段
                ExecutionStateBus.postState(ExecutionState.Running(workflow.id, -1))
                DebugLogger.d("WorkflowExecutor", "开始执行主工作流: ${workflow.name} (ID: ${workflow.id})")
                ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(0, "正在开始..."))

                // 创建本次执行的独立工作目录
                val executionId = "${workflow.id}_${System.currentTimeMillis()}"
                val workDir = File(StorageManager.tempDir, "exec_$executionId")
                if (!workDir.exists()) workDir.mkdirs()

                try {
                    // 创建并注册执行期间所需的服务
                    val services = ExecutionServices()
                    ServiceStateBus.getAccessibilityService()?.let { services.add(it) }
                    services.add(ExecutionUIService(context.applicationContext))

                    // 创建初始执行上下文
                    val initialContext = ExecutionContext(
                        applicationContext = context.applicationContext,
                        variables = mutableMapOf(),
                        magicVariables = mutableMapOf(),
                        services = services,
                        allSteps = workflow.steps,
                        currentStepIndex = -1, // 初始索引
                        stepOutputs = mutableMapOf(),
                        loopStack = Stack(),
                        triggerData = triggerData,
                        namedVariables = ConcurrentHashMap(),
                        workflowStack = Stack<String>().apply { push(workflow.id) }, // 初始化调用栈
                        workDir = workDir
                    )

                    // 调用内部执行循环
                    executeWorkflowInternal(workflow, initialContext)

                    // 如果循环正常结束（没有break），则显示完成通知
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Completed("执行完毕"))

                } catch (e: CancellationException) {
                    DebugLogger.d("WorkflowExecutor", "主工作流 '${workflow.name}' 已被取消。")
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("已停止"))
                } catch (e: Exception) {
                    DebugLogger.e("WorkflowExecutor", "主工作流 '${workflow.name}' 执行时发生未捕获的异常。", e)
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("执行异常"))
                } finally {
                    // 捕获当前协程的取消状态，因为进入 withContext(NonCancellable) 后 isActive 将总是 true
                    val isCoroutineCancelled = !isActive

                    // 使用 NonCancellable 上下文，确保即使协程被取消（如手动停止），清理和广播逻辑也能完整执行
                    withContext(NonCancellable) {
                        // 执行结束后清理工作目录
                        try {
                            if (workDir.exists()) {
                                workDir.deleteRecursively()
                                DebugLogger.d("WorkflowExecutor", "已清理工作目录")
                            }
                        } catch (e: Exception) {
                            DebugLogger.w("WorkflowExecutor", "清理工作目录失败: ${e.message}")
                        }

                        // 广播最终状态
                        val wasStopped = stoppedWorkflows[workflow.id] == true
                        // 如果协程不再活跃且不是因为“停止工作流”模块导致的，则视为手动取消
                        val wasCancelled = isCoroutineCancelled && !wasStopped

                        val fullLog = executionLogs[workflow.id]?.toString() ?: ""

                        if (runningWorkflows.containsKey(workflow.id)) {
                            runningWorkflows.remove(workflow.id)
                            stoppedWorkflows.remove(workflow.id)
                            executionLogs.remove(workflow.id)

                            if (wasCancelled) {
                                ExecutionStateBus.postState(ExecutionState.Cancelled(workflow.id, fullLog))
                            } else {
                                // 正常结束或Stop信号都视为Finished
                                ExecutionStateBus.postState(ExecutionState.Finished(workflow.id, fullLog))
                            }
                            DebugLogger.d("WorkflowExecutor", "主工作流 '${workflow.name}' 执行完毕。")
                        }
                        // 延迟后取消通知，给用户时间查看最终状态
                        delay(3000)
                        ExecutionNotificationManager.cancelNotification()
                    }
                }
            }
        }
        // 将 Job 实例存入 map
        runningWorkflows[workflow.id] = job
    }

    /**
     * 执行一个子工作流并返回结果。
     * @param workflow 要执行的子工作流。
     * @param parentContext 父工作流的执行上下文。
     * @return 子工作流通过 "停止并返回" 模块返回的值，或 null。
     */
    suspend fun executeSubWorkflow(workflow: Workflow, parentContext: ExecutionContext): Any? {
        DebugLogger.d("WorkflowExecutor", "开始执行子工作流: ${workflow.name}")

        // 创建子工作流的上下文，继承大部分父上下文状态，但使用自己的步骤列表和调用栈
        val subWorkflowContext = parentContext.copy(
            allSteps = workflow.steps,
            workflowStack = Stack<String>().apply {
                addAll(parentContext.workflowStack)
                push(workflow.id)
            }
            // 子工作流共享父工作流的 workDir，这样文件可以互通
        )

        // 调用内部执行循环，并返回其结果
        return executeWorkflowInternal(workflow, subWorkflowContext)
    }

    /**
     * 核心的工作流执行循环。
     * @param workflow 要执行的工作流。
     * @param initialContext 初始执行上下文。
     * @return 子工作流的返回值，对于主工作流总是返回 null。
     */
    private suspend fun executeWorkflowInternal(workflow: Workflow, initialContext: ExecutionContext): Any? {
        val stepOutputs = initialContext.stepOutputs.toMutableMap()
        val namedVariables = initialContext.namedVariables
        val loopStack = initialContext.loopStack
        var pc = 0 // 程序计数器
        var returnValue: Any? = null // 用于存储子工作流的返回值

        while (pc < workflow.steps.size && coroutineContext.isActive) { // 检查 coroutine 是否仍然 active
            val step = workflow.steps[pc]
            val module = ModuleRegistry.getModule(step.moduleId)
            if (module == null) {
                DebugLogger.w("WorkflowExecutor", "模块未找到: ${step.moduleId}")
                pc++
                continue
            }

            // 如果在循环内，注入循环变量
            if (loopStack.isNotEmpty()) {
                val loopState = loopStack.peek()
                val loopStartPos = BlockNavigator.findCurrentLoopStartPosition(workflow.steps, pc)
                if (loopStartPos != -1) {
                    val loopStartStep = workflow.steps[loopStartPos]

                    val loopOutputs = when {
                        loopStartStep.moduleId == LOOP_START_ID && loopState is LoopState.CountLoopState -> {
                            mapOf(
                                "loop_index" to NumberVariable((loopState.currentIteration + 1).toDouble()),
                                "loop_total" to NumberVariable(loopState.totalIterations.toDouble())
                            )
                        }
                        loopStartStep.moduleId == FOREACH_START_ID && loopState is LoopState.ForEachLoopState -> {
                            mapOf(
                                "index" to NumberVariable((loopState.currentIndex + 1).toDouble()),
                                "item" to loopState.itemList.getOrNull(loopState.currentIndex)
                            )
                        }
                        else -> null
                    }

                    if (loopOutputs != null) {
                        stepOutputs[loopStartStep.id] = loopOutputs
                    }
                }
            }

            // 广播当前执行步骤的索引
            ExecutionStateBus.postState(ExecutionState.Running(workflow.id, pc))

            // 更新进度通知
            val progress = (pc * 100) / workflow.steps.size
            val progressMessage = "步骤 ${pc + 1}/${workflow.steps.size}: ${module.metadata.name}"
            ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(progress, progressMessage))

            // 为当前步骤创建执行上下文
            val executionContext = initialContext.copy(
                variables = step.parameters.toMutableMap(),
                magicVariables = mutableMapOf(),
                currentStepIndex = pc,
                stepOutputs = stepOutputs,
                loopStack = loopStack,
                namedVariables = namedVariables
            )

            // 统一解析所有变量引用
            step.parameters.forEach { (key, value) ->
                if (value is String) {
                    when {
                        // 1. 解析魔法变量 ({{...}})
                        value.isMagicVariable() -> {
                            val parts = value.removeSurrounding("{{", "}}").split('.')
                            val sourceStepId = parts.getOrNull(0)
                            val sourceOutputId = parts.getOrNull(1)
                            if (sourceStepId != null && sourceOutputId != null) {
                                stepOutputs[sourceStepId]?.get(sourceOutputId)?.let {
                                    executionContext.magicVariables[key] = it
                                }
                            }
                        }
                        // 2. 解析命名变量 ([[...]])
                        value.isNamedVariable() -> {
                            val varName = value.removeSurrounding("[[", "]]")
                            if (namedVariables.containsKey(varName)) {
                                executionContext.magicVariables[key] = namedVariables[varName]
                            }
                        }
                    }
                }
            }

            // 使用本地 DebugLogger，它会自动记录到 detailedLog
            DebugLogger.d("WorkflowExecutor", "[${workflow.name}][$pc] -> 执行: ${module.metadata.name}")

            // --- 错误处理与重试逻辑 ---
            val errorPolicy = step.parameters[ActionEditorSheet.KEY_ERROR_POLICY] as? String ?: ActionEditorSheet.POLICY_STOP
            val retryCount = (step.parameters[ActionEditorSheet.KEY_RETRY_COUNT] as? Number)?.toInt() ?: 3
            val retryInterval = (step.parameters[ActionEditorSheet.KEY_RETRY_INTERVAL] as? Number)?.toLong() ?: 1000L

            var attempt = 0
            var finalResult: ExecutionResult? = null

            while (attempt <= retryCount || errorPolicy != ActionEditorSheet.POLICY_RETRY) {
                if (attempt > 0) {
                    DebugLogger.w("WorkflowExecutor", "步骤执行失败，正在进行第 $attempt 次重试 (等待 ${retryInterval}ms)...")
                    // 在模块内部进度更新时，也刷新通知
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(progress, "重试 ($attempt/$retryCount): ${module.metadata.name}"))
                    delay(retryInterval)
                }

                finalResult = module.execute(executionContext) { progressUpdate ->
                    DebugLogger.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progressUpdate.message}")
                    ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Running(progress, progressUpdate.message))
                }

                if (finalResult is ExecutionResult.Success || finalResult is ExecutionResult.Signal) {
                    break // 成功，跳出重试循环
                }

                // 只有策略是 RETRY 且是 Failure 时才继续循环
                if (errorPolicy == ActionEditorSheet.POLICY_RETRY && finalResult is ExecutionResult.Failure) {
                    attempt++
                    if (attempt > retryCount) break
                } else {
                    break // 不是 RETRY 策略，直接处理结果
                }
            }

            // --- 结果处理 ---
            when (val result = finalResult) {
                is ExecutionResult.Success -> {
                    if (result.outputs.isNotEmpty()) {
                        stepOutputs[step.id] = result.outputs
                    }
                    pc++
                }
                is ExecutionResult.Failure -> {
                    DebugLogger.e("WorkflowExecutor", "模块执行失败: ${result.errorTitle} - ${result.errorMessage}")

                    if (errorPolicy == ActionEditorSheet.POLICY_SKIP) {
                        DebugLogger.w("WorkflowExecutor", "根据策略，跳过错误继续执行。")

                        // 生成该模块所有输出变量的默认空值
                        val defaultOutputs = generateDefaultOutputs(module, step)
                        // 添加错误元数据
                        val skipOutputs = defaultOutputs + mapOf(
                            "error" to TextVariable(result.errorMessage),
                            "success" to BooleanVariable(false)
                        )
                        stepOutputs[step.id] = skipOutputs

                        pc++ // 继续下一步
                    } else {
                        // POLICY_STOP (默认) 或 重试耗尽
                        ExecutionNotificationManager.updateState(workflow, ExecutionNotificationState.Cancelled("失败: ${result.errorMessage}"))

                        // 尝试获取 UI 服务并显示错误弹窗
                        // 仅当应用在前台或有悬浮窗权限时，弹窗才会显示（由 ExecutionUIService 处理）
                        try {
                            val uiService = initialContext.services.get(ExecutionUIService::class)
                            uiService?.showError(
                                workflowName = workflow.name,
                                moduleName = "#${pc} ${module.metadata.name}",
                                errorMessage = result.errorMessage
                            )
                        } catch (e: Exception) {
                            DebugLogger.e("WorkflowExecutor", "显示错误弹窗失败", e)
                        }

                        // 获取完整日志并广播失败状态
                        val fullLog = executionLogs[initialContext.workflowStack.first()]?.toString() ?: ""

                        runningWorkflows.remove(workflow.id)
                        executionLogs.remove(workflow.id)
                        stoppedWorkflows.remove(workflow.id)

                        // 广播失败状态和索引
                        ExecutionStateBus.postState(ExecutionState.Failure(workflow.id, pc, fullLog))
                        return null
                    }
                }
                is ExecutionResult.Signal -> {
                    DebugLogger.d("WorkflowExecutor", "信号: ${result.signal}")
                    when (val signal = result.signal) {
                        is ExecutionSignal.Jump -> {
                            pc = signal.pc
                        }
                        is ExecutionSignal.Loop -> {
                            when (signal.action) {
                                LoopAction.START -> pc++ // 循环开始，进入循环体第一个模块
                                LoopAction.END -> {
                                    val loopState = loopStack.peek() as? LoopState.CountLoopState
                                    if (loopState != null && loopState.currentIteration < loopState.totalIterations) {
                                        val loopStartPos = BlockNavigator.findBlockStartPosition(workflow.steps, pc, LOOP_START_ID)
                                        if (loopStartPos != -1) {
                                            pc = loopStartPos + 1
                                        } else {
                                            DebugLogger.w("WorkflowExecutor", "找不到循环起点，异常退出循环。")
                                            pc++
                                        }
                                    } else {
                                        loopStack.pop() // 循环结束
                                        pc++
                                    }
                                }
                            }
                        }
                        is ExecutionSignal.Break -> {
                            val currentLoopPairingId = BlockNavigator.findCurrentLoopPairingId(workflow.steps, pc)
                            val endBlockPosition = BlockNavigator.findEndBlockPosition(workflow.steps, pc, currentLoopPairingId)
                            if (endBlockPosition != -1) {
                                pc = endBlockPosition + 1
                                DebugLogger.d("WorkflowExecutor", "接收到Break信号，跳出循环 '$currentLoopPairingId' 到步骤 $pc")
                            } else {
                                DebugLogger.w("WorkflowExecutor", "接收到Break信号，但找不到匹配的结束循环块。")
                                pc++
                            }
                        }
                        is ExecutionSignal.Continue -> {
                            val loopStartPos = BlockNavigator.findCurrentLoopStartPosition(workflow.steps, pc)
                            if (loopStartPos != -1) {
                                val loopModule = ModuleRegistry.getModule(workflow.steps[loopStartPos].moduleId)
                                val endBlockPos = BlockNavigator.findEndBlockPosition(workflow.steps, loopStartPos, loopModule?.blockBehavior?.pairingId)
                                if (endBlockPos != -1) {
                                    pc = endBlockPos
                                } else {
                                    pc++
                                }
                                DebugLogger.d("WorkflowExecutor", "接收到Continue信号，跳转到步骤 $pc")
                            } else {
                                DebugLogger.w("WorkflowExecutor", "接收到Continue信号，但找不到循环起点。")
                                pc++
                            }
                        }
                        is ExecutionSignal.Stop -> {
                            DebugLogger.d("WorkflowExecutor", "接收到Stop信号，正常终止工作流。")
                            stoppedWorkflows[workflow.id] = true
                            pc = workflow.steps.size // 设置pc越界以跳出主循环
                        }
                        // 处理 Return 信号
                        is ExecutionSignal.Return -> {
                            DebugLogger.d("WorkflowExecutor", "接收到Return信号，返回值: ${signal.result}")
                            returnValue = signal.result
                            pc = workflow.steps.size // 立即结束当前工作流的执行
                        }
                    }
                }
                null -> {
                    // 理论不应发生
                    pc++
                }
            }
        }
        return returnValue // 返回子工作流的结果
    }

    /**
     * 为跳过的模块生成默认的空输出值。
     * 避免魔法变量在解析时因找不到值而回退到原始字符串。
     */
    private fun generateDefaultOutputs(module: ActionModule, step: ActionStep): Map<String, Any> {
        val outputs = mutableMapOf<String, Any>()
        try {
            // 获取模块定义的所有输出
            val outputDefs = module.getOutputs(step)
            for (def in outputDefs) {
                // 根据类型生成安全的空值
                val emptyValue = when (def.typeName) {
                    TextVariable.TYPE_NAME -> TextVariable("")
                    NumberVariable.TYPE_NAME -> NumberVariable(0.0)
                    BooleanVariable.TYPE_NAME -> BooleanVariable(false)
                    ListVariable.TYPE_NAME -> ListVariable(emptyList())
                    DictionaryVariable.TYPE_NAME -> DictionaryVariable(emptyMap())
                    ImageVariable.TYPE_NAME -> ImageVariable("") // 空 URI
                    else -> TextVariable("") // 默认为空文本
                }
                outputs[def.id] = emptyValue
            }
        } catch (e: Exception) {
            DebugLogger.w("WorkflowExecutor", "生成默认输出时出错: ${e.message}")
        }
        return outputs
    }
}