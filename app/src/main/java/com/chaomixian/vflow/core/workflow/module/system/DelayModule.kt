package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

// 文件：DelayModule.kt
// 描述：定义了在工作流中暂停执行一段时间的延迟模块。

/**
 * 延迟模块。
 * 用于在工作流执行过程中暂停指定的毫秒数。
 */
class DelayModule : BaseModule() {
    // 模块的唯一ID
    override val id = "vflow.device.delay"

    // 模块的元数据
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_device_delay_name,
        descriptionStringRes = R.string.module_vflow_device_delay_desc,
        name = "延迟",                      // Fallback
        description = "暂停工作流一段时间", // Fallback
        iconRes = R.drawable.rounded_avg_time_24,
        category = "应用与系统"
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "延迟时间",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 1000L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_device_delay_duration_name
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_delay_success_name
        )
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如："延迟 [1000] 毫秒"
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val durationPill = PillUtil.createPillFromParam(
            step.parameters["duration"],
            inputs.find { it.id == "duration" }
        )

        val summaryPrefix = context.getString(R.string.summary_vflow_device_delay_prefix)
        val summarySuffix = context.getString(R.string.summary_vflow_device_delay_suffix)
        return PillUtil.buildSpannable(
            context,
            "$summaryPrefix ",
            durationPill,
            " $summarySuffix"
        )
    }

    /**
     * 验证模块参数的有效性。
     * 确保延迟时间不为负数。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val duration = step.parameters["duration"]
        // 检查参数是否为字符串（可能是魔法变量或直接输入的数字字符串）
        if (duration is String) {
            try {
                // 如果不是魔法变量，尝试转换为长整型并检查是否为负
                if (!duration.isMagicVariable()) {
                    if (duration.toLong() < 0) {
                        return ValidationResult(
                            false,
                            appContext.getString(R.string.error_vflow_device_delay_negative_validation)
                        )
                    }
                }
            } catch (e: Exception) {
                // 如果转换失败且不是魔法变量，则格式无效
                if (!duration.isMagicVariable()) {
                    return ValidationResult(
                        false,
                        appContext.getString(R.string.error_vflow_device_delay_invalid_format)
                    )
                }
            }
        } else if (duration is Number && duration.toLong() < 0) {
            return ValidationResult(
                false,
                appContext.getString(R.string.error_vflow_device_delay_negative_validation)
            )
        }
        return ValidationResult(true)
    }

    /**
     * 执行延迟操作的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取延迟时间
        val duration = context.getVariableAsLong("duration")

        if (duration == null) {
            val rawValue = context.getVariable("duration")
            val rawValueStr = when (rawValue) {
                is VString -> rawValue.raw
                is VNull -> "空值"
                is VNumber -> rawValue.raw.toString()
                else -> rawValue?.toString() ?: "未知"
            }
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                "无法将 '$rawValueStr' 解析为有效的延迟时间。"
            )
        }

        // 检查延迟时间是否为负
        if (duration < 0) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                appContext.getString(R.string.error_vflow_device_delay_negative)
            )
        }

        // 如果延迟时间大于0，则执行实际的协程延迟
        if (duration > 0) {
            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_device_delay_delaying), duration)))
            delay(duration)
        }
        // 返回成功结果
        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}