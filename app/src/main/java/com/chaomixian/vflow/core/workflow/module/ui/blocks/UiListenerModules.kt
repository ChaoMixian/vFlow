// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/blocks/UiListenerModules.kt
package com.chaomixian.vflow.core.workflow.module.ui.blocks

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.UiLoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.logic.BlockNavigator
import com.chaomixian.vflow.core.workflow.module.ui.UiCommand
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 当事件发生时 (Start)
 * 逻辑：
 * 1. 从 Context 获取当前发生的事件。
 * 2. 检查事件源 ID 是否匹配 `target_component`。
 * 3. 匹配则执行内部，不匹配则跳过。
 */
class OnUiEventModule : BaseBlockModule() {
    override val id = ON_EVENT_START_ID
    override val metadata = ActionMetadata("当组件被操作", "监听指定组件的点击或修改事件。", R.drawable.rounded_earbuds_24, "UI 组件")
    override val stepIdsInBlock = listOf(ON_EVENT_START_ID, ON_EVENT_END_ID)
    override val pairingId = ON_EVENT_PAIRING

    override fun getInputs() = listOf(
        // 这里接受组件的 ID (魔法变量)
        InputDefinition("target_id", "目标组件", ParameterType.STRING, "", acceptsMagicVariable = true)
    )

    override fun getOutputs(step: ActionStep?) = listOf(
        // 输出事件携带的值 (例如开关的 true/false，输入框的文本)
        OutputDefinition("value", "事件值", "vflow.type.any")
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "当 ", PillUtil.createPillFromParam(step.parameters["target_id"], getInputs()[0]), " 被操作时")

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        // 获取当前事件
        val event = context.namedVariables[KEY_CURRENT_EVENT] as? UiEvent
            ?: return ExecutionResult.Failure("环境错误", "此模块必须放在“显示界面”块内部。")

        // 获取目标 ID (需要从 TextVariable 对象中提取 value)
        val targetId = when (val obj = context.magicVariables["target_id"]) {
            is com.chaomixian.vflow.core.module.TextVariable -> obj.value
            else -> context.variables["target_id"]?.toString() ?: ""
        }

        // 判断事件是否匹配目标组件
        if (event.elementId == targetId) {
            // 匹配成功，注入事件值
            val outputs = mapOf("value" to (event.value ?: ""))
            return ExecutionResult.Success(outputs)
        } else {
            // 不匹配，跳过此块
            val endPos = BlockNavigator.findEndBlockPosition(context.allSteps, context.currentStepIndex, ON_EVENT_PAIRING)
            return ExecutionResult.Signal(ExecutionSignal.Jump(endPos))
        }
    }
}

class EndOnUiEventModule : BaseModule() {
    override val id = ON_EVENT_END_ID
    override val metadata = ActionMetadata("结束监听", "", R.drawable.rounded_earbuds_24, "UI 组件")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, ON_EVENT_PAIRING)
    override fun getSummary(context: Context, step: ActionStep) = "结束监听"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success()
}

/**
 * 更新组件模块
 *
 * 用于动态修改已显示组件的属性。
 *
 * 支持两种调用方式：
 * 1. 传递组件 ID 字符串（旧方式）
 *    - target_id: "btn1"
 * 2. 传递 VUiComponent 对象（推荐）
 *    - target_id: {{buttonComponent}}
 *    - 可以通过 .value、.label 等访问组件属性
 *
 * 支持的更新属性：
 * - text: 新文本内容（支持魔法变量和组件属性）
 * - visible: 可见性（"显示"、"隐藏"、"保持不变"）
 * - enabled: 是否启用
 * - checked: 选中状态（仅开关组件）
 * - padding: 内边距
 * - margin: 外边距
 * - textSize: 文本大小
 * - background: 背景色
 *
 * 使用示例：
 * - 更新文本: target_id={{input1.id}}, text="新内容"
 * - 使用组件属性: target_id={{button}}, text={{button.label}} + " (已点击)"
 * - 更新布局: target_id={{text1}}, textSize=18, padding=16
 *
 * 注意：此模块必须在"显示界面"块内部使用。
 */
class UpdateUiComponentModule : BaseModule() {
    override val id = "vflow.ui.interaction.update"
    override val metadata = ActionMetadata("更新组件", "修改组件属性（如文本、可见性、布局）。", R.drawable.rounded_dashboard_2_edit_24, "UI 组件")

    override fun getInputs() = listOf(
        InputDefinition("target_id", "目标组件", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("text", "新文本", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("visible", "可见性", ParameterType.ENUM, "保持不变", options = listOf("保持不变", "显示", "隐藏")),
        InputDefinition("padding", "内边距", ParameterType.NUMBER, 0),
        InputDefinition("margin", "外边距", ParameterType.NUMBER, 0),
        InputDefinition("textSize", "文本大小(sp)", ParameterType.NUMBER, 0),
        InputDefinition("background", "背景色", ParameterType.STRING, "", acceptsMagicVariable = true)
    )

    override fun getOutputs(step: ActionStep?) = listOf(
        OutputDefinition("success", "是否成功", com.chaomixian.vflow.core.module.BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "更新组件 ", PillUtil.createPillFromParam(step.parameters["target_id"], getInputs()[0]))

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val sessionId = context.namedVariables[KEY_UI_SESSION_ID] as? String
            ?: return ExecutionResult.Failure("错误", "Session丢失")

        // 提取目标组件 ID（支持 VUiComponent 对象或字符串）
        val targetId = when (val obj = context.magicVariables["target_id"]) {
            is com.chaomixian.vflow.core.module.TextVariable -> obj.value
            is com.chaomixian.vflow.core.types.complex.VUiComponent -> obj.getId()
            else -> context.variables["target_id"]?.toString() ?: ""
        }

        val payload = mutableMapOf<String, Any?>()

        // 解析 text 参数（支持魔法变量、命名变量和组件属性）
        val rawText = context.variables["text"]?.toString()
        if (!rawText.isNullOrEmpty()) {
            val resolvedText = com.chaomixian.vflow.core.execution.VariableResolver.resolve(rawText, context)
            payload["text"] = resolvedText
        }

        // 解析可见性参数
        val visible = context.variables["visible"] as? String
        if (visible == "显示") payload["visible"] = true
        else if (visible == "隐藏") payload["visible"] = false

        // 解析布局属性
        val padding = context.variables["padding"] as? Number
        if (padding != null && padding.toInt() > 0) {
            payload["padding"] = padding.toInt()
        }

        val margin = context.variables["margin"] as? Number
        if (margin != null && margin.toInt() > 0) {
            payload["margin"] = margin.toInt()
        }

        val textSize = context.variables["textSize"] as? Number
        if (textSize != null && textSize.toFloat() > 0) {
            payload["textSize"] = textSize.toFloat()
        }

        val background = context.variables["background"]?.toString()
        if (!background.isNullOrEmpty()) {
            payload["background"] = background
        }

        // 发送更新命令到 UI
        UiSessionBus.sendCommand(sessionId, UiCommand("update", targetId, payload))
        return ExecutionResult.Success(mapOf("success" to com.chaomixian.vflow.core.module.BooleanVariable(true)))
    }
}

/**
 * 获取组件模块
 *
 * 用于获取 UI 组件的完整对象，支持访问组件的所有属性。
 *
 * 输出：
 * - component: VUiComponent 对象（包含组件的所有信息）
 * - value: 组件的当前值（简单类型）
 *
 * VUiComponent 支持的属性访问：
 * - {{component.id}}: 组件 ID
 * - {{component.type}}: 组件类型（text/button/input/switch）
 * - {{component.label}}: 标签文本
 * - {{component.value}}: 当前值
 * - {{component.placeholder}}: 占位符文本
 * - {{component.required}}: 是否必填
 * - {{component.triggerEvent}}: 是否触发事件
 *
 * 使用示例：
 * - 获取按钮对象: target_id={{btn.id}}
 * - 访问属性: text={{getBtn.component.label}}
 * - 更新时使用: target_id={{getBtn.component}}, text="已点击"
 *
 * 注意：组件信息需要在"创建界面"块中定义。
 */
class GetComponentValueModule : BaseModule() {
    override val id = "vflow.ui.interaction.get_value"
    override val metadata = ActionMetadata("获取组件", "获取UI组件对象或其值。", R.drawable.rounded_earbuds_24, "UI 组件")

    override fun getInputs() = listOf(
        InputDefinition("component_id", "组件 ID", ParameterType.STRING, "", acceptsMagicVariable = true)
    )

    override fun getOutputs(step: ActionStep?) = listOf(
        OutputDefinition("component", "组件对象", "vflow.type.uicomponent"),
        OutputDefinition("value", "组件值", "vflow.type.any")
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "获取组件 ", PillUtil.createPillFromParam(step.parameters["component_id"], getInputs()[0]))

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        // 从 TextVariable 对象或 VUiComponent 对象中提取组件 ID
        val componentId = when (val obj = context.magicVariables["component_id"]) {
            is com.chaomixian.vflow.core.module.TextVariable -> obj.value
            is com.chaomixian.vflow.core.types.complex.VUiComponent -> obj.getId()
            else -> context.variables["component_id"]?.toString() ?: ""
        }

        // 从 namedVariables 中获取组件值
        val valueKey = "component_value.$componentId"
        val value = context.namedVariables[valueKey]

        return if (value != null) {
            // 创建 VUiComponent 对象
            @Suppress("UNCHECKED_CAST")
            val elementsList = context.namedVariables[KEY_UI_ELEMENTS_LIST] as? List<com.chaomixian.vflow.core.workflow.module.ui.model.UiElement>
            val element = elementsList?.find { it.id == componentId }

            if (element != null) {
                val vComponent = com.chaomixian.vflow.core.types.complex.VUiComponent(element, value)
                ExecutionResult.Success(mapOf(
                    "component" to vComponent,
                    "value" to value
                ))
            } else {
                ExecutionResult.Failure("组件未找到", "组件 '$componentId' 不存在于当前界面")
            }
        } else {
            ExecutionResult.Failure("组件未找到", "组件 '$componentId' 没有值或不存在")
        }
    }
}

/**
 * 退出界面模块
 *
 * 用于主动退出并销毁当前界面。
 *
 * 使用场景：
 * - 条件满足时关闭界面（例如：输入特定内容、点击取消按钮）
 * - 完成任务后自动关闭界面
 *
 * 行为：
 * - 发送关闭命令到界面
 * - 退出事件循环
 * - 跳转到"结束界面"模块清理资源
 *
 * 注意：此模块必须放在"显示界面"块内部。
 */
class ExitActivityModule : BaseModule() {
    override val id = "vflow.ui.interaction.exit"
    override val metadata = ActionMetadata("退出界面", "主动退出并销毁当前界面。", R.drawable.rounded_close_small_24, "UI 组件")

    override fun getSummary(context: Context, step: ActionStep) = "退出界面"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val sessionId = context.namedVariables[KEY_UI_SESSION_ID] as? String
            ?: return ExecutionResult.Failure("错误", "Session丢失")

        // 检查是否在循环栈中
        val loopState = context.loopStack.peek() as? UiLoopState
        if (loopState?.sessionId != sessionId) {
            return ExecutionResult.Failure("环境错误", "此模块必须放在\"显示界面\"块内部。")
        }

        // 退出循环栈
        context.loopStack.pop()

        // 发送关闭命令到 UI
        UiSessionBus.sendCommand(sessionId, UiCommand("close", null, emptyMap()))

        // 跳转到 End 模块
        val endPos = BlockNavigator.findEndBlockPosition(context.allSteps, context.currentStepIndex, ACTIVITY_PAIRING)
        return ExecutionResult.Signal(ExecutionSignal.Jump(endPos))
    }
}