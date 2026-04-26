// 文件: JumpModule.kt
// 描述: 定义了跳转到工作流中指定步骤的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "跳转到步骤" 模块。
 * 允许用户通过指定一个步骤的索引号，无条件跳转到该步骤继续执行。
 */
class JumpModule : BaseModule() {
    override val id = "vflow.logic.jump"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_jump_name,
        descriptionStringRes = R.string.module_vflow_logic_jump_desc,
        name = "跳转步骤",
        description = "跳转到工作流中指定的步骤继续执行。",
        iconRes = R.drawable.rounded_turn_slight_right_24,
        category = "逻辑控制",
        categoryId = "logic"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target_step_index",
            nameStringRes = R.string.param_vflow_logic_jump_target_step_index_name,
            name = "目标步骤编号",
            staticType = ParameterType.NUMBER,
            defaultValue = 1L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", nameStringRes = R.string.output_vflow_logic_jump_success_name, name = "是否成功", typeName = VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetStepPill = PillUtil.createPillFromParam(
            step.parameters["target_step_index"],
            getInputs().find { it.id == "target_step_index" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_jump_to_step_prefix), targetStepPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val targetIndex = step.parameters["target_step_index"]

        // 如果是魔法变量引用，跳过类型检查，执行时再验证
        if (targetIndex is String && targetIndex.isMagicVariable()) {
            return ValidationResult(true)
        }

        // 编辑器显示的步骤编号从 1 开始，这里保持一致。
        val rawIndex = targetIndex as? Number
        val index = rawIndex?.toDouble()
        if (index == null || index.isNaN() || index % 1.0 != 0.0) {
            return ValidationResult(false, "步骤编号必须是一个整数。")
        }

        if (index.toInt() < 1) {
            return ValidationResult(false, "步骤编号必须大于等于 1。")
        }

        // 注意：无法在这里验证索引是否超出范围，因为工作流的步骤列表是动态的
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取原始值用于错误消息
        val rawValue = context.getVariable("target_step_index")
        val rawValueStr = when (rawValue) {
            is VString -> rawValue.raw
            is VNull -> "空值"
            is VNumber -> rawValue.raw.toString()
            else -> rawValue.toString()
        }

        // 使用 getVariableAsNumber 获取可空值
        val targetIndexDouble = context.getVariableAsNumber("target_step_index")

        if (targetIndexDouble == null || targetIndexDouble.isNaN()) {
            return ExecutionResult.Failure(
                "类型错误",
                "无法将 '$rawValueStr' 解析为有效的步骤编号。"
            )
        }

        if (targetIndexDouble % 1.0 != 0.0) {
            return ExecutionResult.Failure(
                "无效的步骤编号",
                "目标步骤编号 '$rawValueStr' 不是有效的整数。"
            )
        }

        val displayStepNumber = targetIndexDouble.toInt()
        val targetIndex = displayStepNumber - 1

        if (targetIndex !in context.allSteps.indices) {
            return ExecutionResult.Failure(
                "无效的步骤编号",
                "目标步骤编号 $displayStepNumber 无效或超出范围。"
            )
        }

        onProgress(ProgressUpdate("跳转到步骤: #$displayStepNumber"))
        return ExecutionResult.Signal(ExecutionSignal.Jump(targetIndex))
    }
}
