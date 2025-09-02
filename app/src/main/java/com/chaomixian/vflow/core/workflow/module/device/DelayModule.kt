package com.chaomixian.vflow.modules.device

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.NumberVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

class DelayModule : BaseModule() {
    override val id = "vflow.device.delay"
    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.ic_workflows,
        category = "设备"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "延迟时间 (毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 1000L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val durationValue = step.parameters["duration"]?.toString() ?: "1000"
        val isVariable = durationValue.startsWith("{{")
        val pillText = if (isVariable) "变量" else durationValue

        return PillUtil.buildSpannable(
            context,
            "延迟 ",
            PillUtil.Pill(pillText, isVariable, parameterId = "duration"),
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
        val durationValue = context.magicVariables["duration"] ?: context.variables["duration"]

        val duration = when(durationValue) {
            is NumberVariable -> durationValue.value.toLong()
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

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}