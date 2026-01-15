// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/blocks/CreateActivityModule.kt
package com.chaomixian.vflow.core.workflow.module.ui.blocks

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.UiLoopState
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.logic.BlockNavigator
import com.chaomixian.vflow.core.workflow.module.ui.UiCommand
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.ui.common.DynamicUiActivity
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.UUID

/**
 * 动态 UI 界面模块
 *
 * 本文件包含三个相互配合的模块，用于创建和管理动态用户界面：
 *
 * 1. CreateActivityModule (Start) - 初始化界面环境
 *    - 创建组件列表和会话 ID
 *    - 为后续的 UI 组件模块提供存储空间
 *
 * 2. ShowActivityModule (Middle) - 显示界面并管理事件循环
 *    - 启动 DynamicUiActivity 显示界面
 *    - 等待并处理 UI 事件
 *    - 自动收集所有组件的当前值
 *    - 管理事件循环，直到界面关闭
 *
 * 3. EndActivityModule (End) - 清理资源
 *    - 判断是否继续循环
 *    - 如果循环结束，关闭界面并清理资源
 *
 * 工作流程：
 * CreateActivity -> [UI 组件定义] -> ShowActivity -> [事件监听模块] -> EndActivity
 *                                                        ↑_______________|
 *                                                              (循环)
 *
 * 注意事项：
 * - UI 组件必须定义在 CreateActivity 和 ShowActivity 之间
 * - 事件监听模块必须定义在 ShowActivity 和 EndActivity 之间
 * - 使用 namedVariables 存储跨步骤的数据（session、组件列表、事件、组件值）
 */
class CreateActivityModule : BaseBlockModule() {
    override val id = ACTIVITY_START_ID
    override val metadata = ActionMetadata("创建界面", "开始定义界面布局。", R.drawable.rounded_activity_zone_24, "UI 组件")
    override val stepIdsInBlock = listOf(ACTIVITY_START_ID, ACTIVITY_SHOW_ID, ACTIVITY_END_ID) // 定义三段结构
    override val pairingId = ACTIVITY_PAIRING

    override fun getInputs() = listOf(
        InputDefinition("title", "标题", ParameterType.STRING, "用户界面", acceptsMagicVariable = true),
        InputDefinition("destroy_on_exit", "退出随即销毁页面", ParameterType.BOOLEAN, true)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "创建界面: ", PillUtil.createPillFromParam(step.parameters["title"], getInputs()[0]))

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        // 初始化组件列表
        context.namedVariables[KEY_UI_ELEMENTS_LIST] = mutableListOf<UiElement>()
        // 生成 Session ID
        context.namedVariables[KEY_UI_SESSION_ID] = UUID.randomUUID().toString()
        // 保存退出及销毁页面参数
        val currentStep = context.allSteps[context.currentStepIndex]
        val destroyOnExit = currentStep.parameters["destroy_on_exit"] as? Boolean ?: true
        context.namedVariables[KEY_UI_DESTROY_ON_EXIT] = destroyOnExit

        onProgress(ProgressUpdate("初始化界面定义..."))
        return ExecutionResult.Success()
    }
}

/**
 * 第二段：显示界面 & 开始循环 (Middle)
 * 作用：
 * 1. 界面定义结束，启动 Activity。
 * 2. 充当 `While(true)` 循环头，挂起等待事件。
 */
class ShowActivityModule : BaseModule() {
    override val id = ACTIVITY_SHOW_ID
    override val metadata = ActionMetadata("显示界面 (事件循环)", "界面定义结束，启动界面并开始监听事件。", R.drawable.rounded_play_arrow_24, "UI 组件")
    // 标记为 Middle 块，且不可独立删除
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, ACTIVITY_PAIRING, isIndividuallyDeletable = false)

    override fun getSummary(context: Context, step: ActionStep) = "显示界面并开始监听事件"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val sessionId = context.namedVariables[KEY_UI_SESSION_ID] as? String ?: return ExecutionResult.Failure("错误", "Session丢失")

        // 获取 destroy_on_exit 参数
        val destroyOnExit = context.namedVariables[KEY_UI_DESTROY_ON_EXIT] as? Boolean ?: true

        // --- 检查是否已经在循环中 ---
        val loopState = if (context.loopStack.isNotEmpty()) context.loopStack.peek() else null

        if (loopState is UiLoopState && loopState.sessionId == sessionId) {
            // === 循环回跳逻辑 (Loop) ===

            // 1. 检查 Session 是否已关闭
            if (UiSessionBus.isSessionClosed(sessionId)) {
                context.loopStack.pop() // 退出循环
                // 跳转到 End 模块
                val endPos = BlockNavigator.findEndBlockPosition(context.allSteps, context.currentStepIndex, ACTIVITY_PAIRING)
                return ExecutionResult.Signal(ExecutionSignal.Jump(endPos))
            }

            // 2. 挂起等待下一个事件
            onProgress(ProgressUpdate("等待用户操作..."))
            val event = UiSessionBus.waitForEvent(sessionId)

            // 3. 处理系统事件（关闭、返回键）
            if (event.type == "closed" || (destroyOnExit && event.type == "back_pressed")) {
                context.loopStack.pop()
                val endPos = BlockNavigator.findEndBlockPosition(context.allSteps, context.currentStepIndex, ACTIVITY_PAIRING)
                return ExecutionResult.Signal(ExecutionSignal.Jump(endPos))
            }

            // 4. 将事件存入上下文，供内部的监听模块使用
            context.namedVariables[KEY_CURRENT_EVENT] = event

            // 5. 将所有组件的值存入 namedVariables，方便其他模块使用
            event.allComponentValues.forEach { (componentId, value) ->
                context.namedVariables["component_value.$componentId"] = value
            }

            onProgress(ProgressUpdate("收到事件: ${event.elementId} - ${event.type}"))
            return ExecutionResult.Success() // 继续执行内部的监听积木

        } else {
            // === 首次启动逻辑 (Start) ===

            @Suppress("UNCHECKED_CAST")
            val elements = context.namedVariables[KEY_UI_ELEMENTS_LIST] as? List<UiElement>
                ?: return ExecutionResult.Failure("配置错误", "组件列表为空")

            val title = VariableResolver.resolve(context.variables["title"]?.toString() ?: "界面", context)

            onProgress(ProgressUpdate("正在启动界面..."))

            // 启动 Activity
            val intent = Intent(context.applicationContext, DynamicUiActivity::class.java).apply {
                putExtra(DynamicUiActivity.EXTRA_TITLE, title)
                putParcelableArrayListExtra(DynamicUiActivity.EXTRA_ELEMENTS, ArrayList(elements))
                putExtra("session_id", sessionId)
                putExtra(DynamicUiActivity.EXTRA_IS_INTERACTIVE, true) // 开启交互模式
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.applicationContext.startActivity(intent)

            // 注册 Session 并进入循环栈
            UiSessionBus.registerSession(sessionId)
            context.loopStack.push(UiLoopState(sessionId))

            // 为了复用代码，这里直接进入等待逻辑：
            onProgress(ProgressUpdate("界面已启动，等待操作..."))
            val event = UiSessionBus.waitForEvent(sessionId)

            // 处理系统事件（关闭、返回键）
            if (event.type == "closed" || (destroyOnExit && event.type == "back_pressed")) {
                context.loopStack.pop()
                val endPos = BlockNavigator.findEndBlockPosition(context.allSteps, context.currentStepIndex, ACTIVITY_PAIRING)
                return ExecutionResult.Signal(ExecutionSignal.Jump(endPos))
            }

            context.namedVariables[KEY_CURRENT_EVENT] = event

            // 将所有组件的值存入 namedVariables
            event.allComponentValues.forEach { (componentId, value) ->
                context.namedVariables["component_value.$componentId"] = value
            }

            return ExecutionResult.Success()
        }
    }
}

/**
 * 第三段：结束界面 (End)
 * 作用：循环的终点，负责跳回 Middle 继续监听，或者在退出后清理资源。
 */
class EndActivityModule : BaseModule() {
    override val id = ACTIVITY_END_ID
    override val metadata = ActionMetadata("结束界面", "界面生命周期结束。", R.drawable.rounded_stop_circle_24, "UI 组件")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, ACTIVITY_PAIRING)

    override fun getSummary(context: Context, step: ActionStep) = "结束界面"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val sessionId = context.namedVariables[KEY_UI_SESSION_ID] as? String

        // 如果还在循环栈中，说明是正常的一轮逻辑执行完毕，需要跳回 Middle 继续等待
        if (!context.loopStack.isEmpty()) {
            val loopState = context.loopStack.peek()
            if (loopState is UiLoopState && loopState.sessionId == sessionId) {
                // 查找配对的 Middle 模块 (ShowActivityModule)
                val middlePos = findMiddleBlockPosition(context.allSteps, context.currentStepIndex, ACTIVITY_SHOW_ID)
                if (middlePos != -1) {
                    return ExecutionResult.Signal(ExecutionSignal.Jump(middlePos))
                }
            }
        }

        // 循环已退出（从 Middle 跳转过来），清理资源
        if (sessionId != null) {
            UiSessionBus.sendCommand(sessionId, UiCommand("close", null, emptyMap()))
            UiSessionBus.unregisterSession(sessionId)
        }

        return ExecutionResult.Success()
    }

    private fun findMiddleBlockPosition(steps: List<ActionStep>, endPos: Int, targetId: String): Int {
        for (i in endPos - 1 downTo 0) {
            if (steps[i].moduleId == targetId) return i
        }
        return -1
    }
}