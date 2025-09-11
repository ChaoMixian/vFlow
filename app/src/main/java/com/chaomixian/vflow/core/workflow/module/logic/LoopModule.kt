package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

// 文件：LoopModule.kt
// 描述：定义循环 (Loop/EndLoop) 模块，实现重复执行逻辑。

// --- 循环模块常量定义 ---
const val LOOP_PAIRING_ID = "loop" // Loop块的配对ID
const val LOOP_START_ID = "vflow.logic.loop.start" // Loop模块ID
const val LOOP_END_ID = "vflow.logic.loop.end"    // EndLoop模块ID

/**
 * "循环" (Loop) 模块，逻辑块的起点。
 * 指定次数重复执行块内操作。
 */
class LoopModule : BaseBlockModule() {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.rounded_cached_24, "逻辑控制")
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID) // 定义Loop块包含的模块ID

    /** 获取输入参数定义：重复次数。 */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5L, // 默认5次
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
        )
    )

    /** 生成模块摘要。 */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countPill = PillUtil.createPillFromParam(
            step.parameters["count"],
            getInputs().find { it.id == "count" }
        )
        return PillUtil.buildSpannable(context, "循环 ", countPill, " 次")
    }

    /** 验证参数：循环次数必须为正数。 */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val count = step.parameters["count"]
        if (count is String && !count.isMagicVariable()) { // 非魔法变量字符串
            val countAsLong = count.toLongOrNull()
            if (countAsLong == null) return ValidationResult(false, "无效的数字格式")
            if (countAsLong <= 0) return ValidationResult(false, "循环次数必须大于0")
        } else if (count is Number) { // 数字类型
            if (count.toLong() <= 0) return ValidationResult(false, "循环次数必须大于0")
        }
        return ValidationResult(true)
    }

    /** 执行循环模块：初始化循环状态并发出 LoopAction.START 信号。 */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val countVar = context.magicVariables["count"] ?: context.variables["count"]
        val actualCount = when (countVar) {
            is NumberVariable -> countVar.value.toLong()
            is Number -> countVar.toLong()
            is String -> countVar.toLongOrNull() ?: (getInputs().firstOrNull { it.id == "count" }?.defaultValue as? Number ?: 0L).toLong()
            else -> (getInputs().firstOrNull { it.id == "count" }?.defaultValue as? Number ?: 0L).toLong()
        }

        if (actualCount <= 0) { // 次数为0或负数，则跳过整个循环块
            onProgress(ProgressUpdate("循环次数为 $actualCount，跳过循环块。"))
            val endPc = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(LOOP_END_ID))
            return if (endPc != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(endPc))
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的结束循环块")
            }
        }

        onProgress(ProgressUpdate("循环开始，总次数: $actualCount"))
        context.loopStack.push(LoopState(actualCount)) // 推入新的循环状态
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.START)) // 发出循环开始信号
    }
}

/**
 * "结束循环" (EndLoop) 模块，Loop 逻辑块的结束点。
 */
class EndLoopModule : BaseModule() {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.rounded_cached_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID) // 标记为块结束

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束循环"

    /** 执行结束循环模块：更新循环状态并发出 LoopAction.END 信号。 */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val loopState = context.loopStack.peek() ?: return ExecutionResult.Success() // 栈顶无循环状态则直接成功
        onProgress(ProgressUpdate("循环迭代结束，当前是第 ${loopState.currentIteration + 1} 次"))

        loopState.currentIteration++ // 迭代次数增加
        // LoopAction.END 信号将由执行引擎处理，判断是继续循环还是跳出
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.END)) // 发出循环结束信号
    }
}