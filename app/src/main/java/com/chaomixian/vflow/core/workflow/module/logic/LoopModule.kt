package com.chaomixian.vflow.core.workflow.module.logic // Corrected package

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.* // Imports BaseModule, BaseBlockModule, etc.
import com.chaomixian.vflow.core.workflow.model.ActionStep
// Corrected import for NumberVariable
import com.chaomixian.vflow.core.workflow.module.data.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : BaseBlockModule() { // BaseBlockModule is in com.chaomixian.vflow.core.module
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.rounded_cached_24, "逻辑控制")
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5, // Consider Long for defaultValue if appropriate e.g. 5L
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME) // Uses imported NumberVariable
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countParam = step.parameters["count"]
        val isVariable = (countParam as? String)?.startsWith("{{") == true

        val countText = when {
            isVariable -> countParam.toString()
            countParam is Number -> {
                if (countParam.toDouble() == countParam.toLong().toDouble()) {
                    countParam.toLong().toString()
                } else {
                    countParam.toString()
                }
            }
            // Ensure defaultValue (e.g. 5) matches type here if countParam is null
            else -> countParam?.toString() ?: getInputs().firstOrNull { it.id == "count" }?.defaultValue?.toString() ?: "5"
        }

        return PillUtil.buildSpannable(
            context,
            "循环 ",
            PillUtil.Pill(countText, isVariable, parameterId = "count"),
            " 次"
        )
    }

    override fun validate(step: ActionStep): ValidationResult {
        val count = step.parameters["count"]
        if (count is String && !count.startsWith("{{")) {
            val countAsLong = count.toLongOrNull()
            if (countAsLong == null) return ValidationResult(false, "无效的数字格式")
            if (countAsLong <= 0) return ValidationResult(false, "循环次数必须大于0")
        } else if (count is Number) {
            if (count.toLong() <= 0) return ValidationResult(false, "循环次数必须大于0")
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val countVar = context.magicVariables["count"] ?: context.variables["count"]
        // Uses imported NumberVariable
        val actualCount = when (countVar) {
            is NumberVariable -> countVar.value.toLong()
            is Number -> countVar.toLong()
            is String -> countVar.toLongOrNull() ?: (getInputs().firstOrNull { it.id == "count" }?.defaultValue as? Number ?: 0).toLong()
            else -> (getInputs().firstOrNull { it.id == "count" }?.defaultValue as? Number ?: 0).toLong()
        }

        if (actualCount <= 0) {
            onProgress(ProgressUpdate("循环次数为 $actualCount，跳过循环块。"))
            // findNextBlockPosition is a top-level function in this package (defined in IfModule.kt)
            val endPc = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(LOOP_END_ID))
            return if (endPc != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(endPc))
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的结束循环块")
            }
        }

        onProgress(ProgressUpdate("循环开始，总次数: $actualCount"))
        context.loopStack.push(LoopState(actualCount))
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.START))
    }
}

class EndLoopModule : BaseModule() { // BaseModule is in com.chaomixian.vflow.core.module
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.rounded_cached_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束循环"

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val loopState = context.loopStack.peek() ?: return ExecutionResult.Success() // If no loop state, just succeed.
        onProgress(ProgressUpdate("循环迭代结束，当前是第 ${loopState.currentIteration + 1} 次")) // Iteration is 0-indexed

        loopState.currentIteration++
        // No need to check loopState.currentIteration against loopState.totalIterations here.
        // The LoopAction.END signal will be handled by the execution engine,
        // which will decide whether to jump back to the LoopModule or exit the loop.
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.END))
    }
}