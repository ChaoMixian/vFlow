package com.chaomixian.vflow.core.workflow.module.device // Corrected package

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
// Corrected imports for Variable types
import com.chaomixian.vflow.core.workflow.module.data.BooleanVariable
import com.chaomixian.vflow.core.workflow.module.data.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

class DelayModule : BaseModule() {
    override val id = "vflow.device.delay"
    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.rounded_avg_time_24,
        category = "设备"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "延迟时间",
            staticType = ParameterType.NUMBER,
            defaultValue = 1000L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME) // Uses TYPE_NAME from the imported NumberVariable
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME) // Uses TYPE_NAME from the imported BooleanVariable
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val durationParam = step.parameters["duration"]
        val isVariable = (durationParam as? String)?.startsWith("{{") == true

        val durationText = when {
            isVariable -> durationParam.toString()
            durationParam is Number -> {
                if (durationParam.toDouble() == durationParam.toLong().toDouble()) {
                    durationParam.toLong().toString()
                } else {
                    durationParam.toString()
                }
            }
            else -> durationParam?.toString() ?: "1000"
        }

        return PillUtil.buildSpannable(
            context,
            "延迟",
            PillUtil.Pill(durationText, isVariable, parameterId = "duration"),
            " 毫秒"
        )
    }

    override fun validate(step: ActionStep): ValidationResult {
        val duration = step.parameters["duration"]
        if (duration is String) {
            try {
                if (duration.toLong() < 0) {
                    return ValidationResult(false, "延迟时间不能为负数")
                }
            } catch (e: Exception) {
                if (!duration.startsWith("{{")) {
                    return ValidationResult(false, "无效的数字格式")
                }
            }
        } else if (duration is Number && duration.toLong() < 0) {
            return ValidationResult(false, "延迟时间不能为负数")
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // Ensure NumberVariable here refers to the correctly imported type
        val durationValue = context.magicVariables["duration"] ?: context.variables["duration"]

        val duration = when(durationValue) {
            is NumberVariable -> durationValue.value.toLong() // Uses the imported NumberVariable
            is Number -> durationValue.toLong()
            is String -> durationValue.toLongOrNull() ?: 1000L
            else -> 1000L
        }

        if (duration < 0) {
            return ExecutionResult.Failure("参数错误", "延迟时间不能为负数: $duration ms")
        }

        if (duration > 0) {
            onProgress(ProgressUpdate("正在延迟 ${duration}ms..."))
            delay(duration)
        }
        // Ensure BooleanVariable here refers to the correctly imported type
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}