// main/java/com/chaomixian/vflow/core/workflow/module/logic/IfModule.kt

package com.chaomixian.vflow.modules.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.*
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
// 移除了未使用的 WorkflowEditorActivity 导入

// ... (常量定义保持不变) ...
const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"


class IfModule : BaseBlockModule() {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "condition",
            name = "条件",
            staticType = ParameterType.BOOLEAN,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(
                BooleanVariable.TYPE_NAME,
                NumberVariable.TYPE_NAME,
                TextVariable.TYPE_NAME,
                DictionaryVariable.TYPE_NAME,
                ListVariable.TYPE_NAME,
                ScreenElement.TYPE_NAME
            )
        ),
        // --- 核心修改：将此内部参数标记为隐藏 ---
        InputDefinition(
            id = "checkMode",
            name = "检查模式",
            staticType = ParameterType.STRING,
            defaultValue = null,
            acceptsMagicVariable = false,
            isHidden = true // 设置为 true
        )
    )

    // ... (getOutputs, getSummary, execute, evaluateCondition 等方法保持不变) ...
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val conditionValue = step.parameters["condition"]?.toString()
        val isVariable = conditionValue?.startsWith("{{") == true

        if (!isVariable) {
            return PillUtil.buildSpannable(
                context,
                "如果 ",
                PillUtil.Pill("条件", true, parameterId = "condition")
            )
        }

        val checkMode = step.parameters["checkMode"] as? String
        val conditionalPillOptions = findConditionalOptions(step) // 辅助函数找到选项

        return PillUtil.buildSpannable(
            context,
            "如果 ",
            PillUtil.Pill("变量", true, parameterId = "condition"),
            " ",
            PillUtil.Pill(checkMode ?: "...", true, parameterId = "checkMode", options = conditionalPillOptions)
        )
    }

    // 辅助函数，用于在生成摘要时找到上游模块定义的条件选项
    private fun findConditionalOptions(step: ActionStep): List<ConditionalOption>? {
        // (这个逻辑也可以放在 Adapter 中，但放在这里更符合模块化)
        // 此处为简化逻辑，实际应从工作流中查找上游步骤并获取其输出定义
        // 在当前的实现中，这个查找逻辑已经在 WorkflowEditorActivity 和 ActionStepAdapter 中处理
        return null // Adapter 会处理实际的查找
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val condition = context.magicVariables["condition"]
        val checkMode = context.variables["checkMode"] as? String
        val result = evaluateCondition(condition, checkMode)
        onProgress(ProgressUpdate("条件判断结果: $result (模式: ${checkMode ?: "默认"})"))

        if (!result) {
            val jumpTo = findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(ELSE_ID, IF_END_ID)
            )
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        return ExecutionResult.Success(mapOf("result" to BooleanVariable(result)))
    }

    private fun evaluateCondition(condition: Any?, checkMode: String?): Boolean {
        if (checkMode == "存在") return condition != null
        if (checkMode == "不存在") return condition == null
        if (checkMode == "成功") return (condition as? BooleanVariable)?.value == true
        if (checkMode == "不成功") return (condition as? BooleanVariable)?.value != true

        return when (condition) {
            is Boolean -> condition
            is BooleanVariable -> condition.value
            is Number -> condition.toDouble() != 0.0
            is NumberVariable -> condition.value != 0.0
            is String -> condition.isNotEmpty()
            is TextVariable -> condition.value.isNotEmpty()
            is Collection<*> -> condition.isNotEmpty()
            is ListVariable -> condition.value.isNotEmpty()
            is Map<*,*> -> condition.isNotEmpty()
            is DictionaryVariable -> condition.value.isNotEmpty()
            is ScreenElement -> true
            else -> condition != null
        }
    }
}
// ... (ElseModule, EndIfModule, findNextBlockPosition 保持不变) ...
class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "否则"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val jumpTo = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
        if (jumpTo != -1) return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
        return ExecutionResult.Success()
    }
}

class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束如果"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success()
}

fun findNextBlockPosition(steps: List<ActionStep>, startPosition: Int, targetIds: Set<String>): Int {
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