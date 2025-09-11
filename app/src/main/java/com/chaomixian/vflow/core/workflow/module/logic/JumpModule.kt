// 文件: JumpModule.kt
// 描述: 定义了跳转到工作流中指定步骤的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "跳转到步骤" 模块。
 * 允许用户通过指定一个步骤的索引号，无条件跳转到该步骤继续执行。
 */
class JumpModule : BaseModule() {
    override val id = "vflow.logic.jump"
    override val metadata = ActionMetadata(
        name = "跳转步骤",
        description = "跳转到工作流中指定的步骤继续执行。",
        iconRes = R.drawable.rounded_turn_slight_right_24,
        category = "逻辑控制"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target_step_index",
            name = "目标步骤编号",
            staticType = ParameterType.NUMBER,
            defaultValue = 0L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetStepPill = PillUtil.createPillFromParam(
            step.parameters["target_step_index"],
            getInputs().find { it.id == "target_step_index" }
        )
        return PillUtil.buildSpannable(context, "跳转到步骤 ", targetStepPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val targetIndex = step.parameters["target_step_index"]
        val index = (targetIndex as? Number)?.toInt()

        if (index == null || index < 0) {
            return ValidationResult(false, "步骤编号必须是一个非负整数。")
        }

        // 注意：无法在这里验证索引是否超出范围，因为工作流的步骤列表是动态的
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val indexValue = context.magicVariables["target_step_index"] ?: context.variables["target_step_index"]
        val targetIndex = (indexValue as? Number)?.toInt() ?: 0

        if (targetIndex < 0 || targetIndex >= context.allSteps.size) {
            return ExecutionResult.Failure("无效的步骤编号", "目标步骤编号 $targetIndex 无效或超出范围。")
        }

        onProgress(ProgressUpdate("跳转到步骤: #$targetIndex"))
        return ExecutionResult.Signal(ExecutionSignal.Jump(targetIndex))
    }
}