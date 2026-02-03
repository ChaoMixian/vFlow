// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/logic/LoopModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
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
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_loop_start_name,
        descriptionStringRes = R.string.module_vflow_logic_loop_start_desc,
        name = "循环",
        description = "重复执行一组操作固定的次数",
        iconRes = R.drawable.rounded_cached_24,
        category = "逻辑控制"
    )
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID) // 定义Loop块包含的模块ID

    /** 获取输入参数定义：重复次数。 */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            nameStringRes = R.string.param_vflow_logic_loop_start_count_name,
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5L, // 默认5次
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    /**
     * `loop_index`：当前循环的次数（从1开始）。
     * `loop_total`：循环的总次数。
     * 这两个值由 WorkflowExecutor 在每次循环时动态注入，
     * 使得循环体内的模块可以通过魔法变量引用它们。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("loop_index", nameStringRes = R.string.output_vflow_logic_loop_start_loop_index_name, name = "循环索引", typeName = VTypeRegistry.NUMBER.id),
        OutputDefinition("loop_total", nameStringRes = R.string.output_vflow_logic_loop_start_loop_total_name, name = "循环总数", typeName = VTypeRegistry.NUMBER.id)
    )


    /** 生成模块摘要。 */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countPill = PillUtil.createPillFromParam(
            step.parameters["count"],
            getInputs().find { it.id == "count" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_loop_prefix), countPill, context.getString(R.string.summary_vflow_logic_loop_suffix))
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
        val countVar = context.getVariable("count")
        val actualCount: Long? = when (countVar) {
            is VNumber -> countVar.raw.toLong()
            is VString -> countVar.raw.toLongOrNull()
            is VNull -> null
            else -> null
        }

        // 无法转换为有效数字时报错
        if (actualCount == null || actualCount <= 0) {
            val errorMsg = when (countVar) {
                is VString -> "无法将 '${countVar.raw}' 解析为有效的循环次数"
                is VNull -> "循环次数不能为空"
                else -> "循环次数必须是有效的数字"
            }
            return ExecutionResult.Failure("参数错误", errorMsg)
        }

        onProgress(ProgressUpdate("循环开始，总次数: $actualCount"))
        // 使用新的 CountLoopState 来创建循环状态
        context.loopStack.push(LoopState.CountLoopState(actualCount))
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.START))
    }
}

/**
 * "结束循环" (EndLoop) 模块，Loop 逻辑块的结束点。
 */
class EndLoopModule : BaseModule() {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_loop_end_name,
        descriptionStringRes = R.string.module_vflow_logic_loop_end_desc,
        name = "结束循环",
        description = "",
        iconRes = R.drawable.rounded_cached_24,
        category = "逻辑控制"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID) // 标记为块结束

    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_end_loop)

    /** 执行结束循环模块：更新循环状态并发出 LoopAction.END 信号。 */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 检查循环状态是否为正确的 CountLoopState 类型
        val loopState = context.loopStack.peek()
        if (loopState !is LoopState.CountLoopState) {
            // 如果栈顶不是固定次数循环状态（可能是空的，或是一个ForEach循环），则直接成功返回或报错
            return if (loopState == null) ExecutionResult.Success() else ExecutionResult.Failure("执行错误", "结束循环模块不在一个'循环'块内。")
        }

        onProgress(ProgressUpdate("循环迭代结束，当前是第 ${loopState.currentIteration + 1} 次"))

        loopState.currentIteration++ // 迭代次数增加
        // LoopAction.END 信号将由执行引擎处理，判断是继续循环还是跳出
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.END))
    }
}