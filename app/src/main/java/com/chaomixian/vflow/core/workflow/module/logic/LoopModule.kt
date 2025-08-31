package com.chaomixian.vflow.modules.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : BaseBlockModule() {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.ic_control_flow, "逻辑控制")

    // --- 来自 BaseBlockModule 的配置 ---
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID)
    // --- 配置结束 ---

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable::class.java)
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countValue = step.parameters["count"]?.toString() ?: "5"
        val isVariable = countValue.startsWith("{{")
        val pillText = if (isVariable) "变量" else countValue

        return PillUtil.buildSpannable(
            context,
            "循环 ",
            PillUtil.Pill(pillText, isVariable, parameterId = "count"),
            " 次"
        )
    }

    override fun validate(step: ActionStep): ValidationResult {
        val count = step.parameters["count"]
        val countAsLong = when (count) {
            is String -> count.toLongOrNull()
            is Number -> count.toLong()
            else -> null
        }

        if (countAsLong == null && (count as? String)?.startsWith("{{") != true) {
            return ValidationResult(false, "无效的数字格式")
        }

        if (countAsLong != null && countAsLong <= 0) {
            return ValidationResult(false, "循环次数必须大于0")
        }

        return ValidationResult(true)
    }

    // 在新架构下，LoopModule的execute仅用于日志记录
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val countValue = context.magicVariables["count"] ?: context.variables["count"]
        val actualCount = when (countValue) {
            is NumberVariable -> countValue.value
            is Number -> countValue
            else -> countValue?.toString()
        }
        onProgress(ProgressUpdate("循环开始，总次数: $actualCount"))
        return ExecutionResult.Success()
    }
}

class EndLoopModule : BaseModule() {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束循环"

    // EndLoopModule的execute也仅用于日志记录
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("循环迭代结束"))
        return ExecutionResult.Success()
    }
}