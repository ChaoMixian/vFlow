// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/LoopModule.kt

package com.chaomixian.vflow.modules.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : BaseBlockModule() {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.rounded_cached_24, "逻辑控制")
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countValue = step.parameters["count"]?.toString() ?: "5"
        val isVariable = countValue.startsWith("{{")

        return PillUtil.buildSpannable(
            context,
            "循环 ",
            PillUtil.Pill(countValue, isVariable, parameterId = "count"),
            " 次"
        )
    }

    override fun validate(step: ActionStep): ValidationResult {
        val count = step.parameters["count"]
        // 在验证时，我们只关心静态值。魔法变量在运行时才解析。
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
        val actualCount = when (countVar) {
            is NumberVariable -> countVar.value.toLong()
            is Number -> countVar.toLong()
            else -> countVar.toString().toLongOrNull() ?: 0
        }

        if (actualCount <= 0) {
            onProgress(ProgressUpdate("循环次数为0，跳过循环块。"))
            val endPc = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(LOOP_END_ID))
            if (endPc != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(endPc))
            } else {
                return ExecutionResult.Failure("执行错误", "找不到配对的结束循环块")
            }
        }

        onProgress(ProgressUpdate("循环开始，总次数: $actualCount"))
        context.loopStack.push(LoopState(actualCount))
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.START))
    }
}

class EndLoopModule : BaseModule() {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.rounded_cached_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束循环"

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val loopState = context.loopStack.peek() ?: return ExecutionResult.Success()
        onProgress(ProgressUpdate("循环迭代结束，当前是第 ${loopState.currentIteration + 1} 次"))

        loopState.currentIteration++
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.END))
    }
}