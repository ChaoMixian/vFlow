package com.chaomixian.vflow.modules.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.*
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

class IfModule : BaseBlockModule() {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.ic_control_flow, "逻辑控制")

    // --- 来自 BaseBlockModule 的配置 ---
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID)
    // --- 配置结束 ---

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "condition",
            name = "条件",
            staticType = ParameterType.BOOLEAN,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(
                BooleanVariable::class.java,
                NumberVariable::class.java,
                TextVariable::class.java,
                DictionaryVariable::class.java,
                ListVariable::class.java,
                ScreenElement::class.java
            )
        )
    )
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable::class.java)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val conditionValue = step.parameters["condition"]?.toString()
        val hasCondition = !conditionValue.isNullOrBlank()
        val isVariable = conditionValue?.startsWith("{{") == true
        val pillText = if (isVariable) "变量" else (if(hasCondition) "..." else "条件")

        return PillUtil.buildSpannable(
            context,
            "如果 ",
            PillUtil.Pill(pillText, isVariable || !hasCondition, parameterId = "condition")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val condition = context.magicVariables["condition"] ?: context.variables["condition"]
        val result = evaluateCondition(condition)
        onProgress(ProgressUpdate("条件判断结果: $result"))

        // 核心改动：如果条件不满足，则计算跳转位置并发起信号
        if (!result) {
            val jumpTo = findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(ELSE_ID, IF_END_ID)
            )
            if (jumpTo != -1) {
                // 返回一个信号，告诉执行器跳转
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }

        // 条件满足，正常返回 Success
        return ExecutionResult.Success(mapOf("result" to BooleanVariable(result)))
    }

    private fun evaluateCondition(condition: Any?): Boolean {
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

class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "否则"

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 核心改动： "Else" 模块需要无条件跳转到 "EndIf"
        val jumpTo = findNextBlockPosition(
            context.allSteps,
            context.currentStepIndex,
            setOf(IF_END_ID)
        )
        if (jumpTo != -1) {
            return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
        }
        return ExecutionResult.Success()
    }
}

class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束如果"

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ) = ExecutionResult.Success()
}

/**
 * 辅助函数，用于模块内部计算配对模块的位置
 */
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