package com.chaomixian.vflow.core.workflow.module.device

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
// 确保从正确的包导入变量类型
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.NumberVariable
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
    // 模块的元数据，用于在UI中展示
    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.rounded_avg_time_24, // 使用一个与时间相关的图标
        category = "设备" // 模块分类
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "延迟时间", // 用户需要设置的延迟毫秒数
            staticType = ParameterType.NUMBER, // 参数类型为数字
            defaultValue = 1000L, // 默认延迟1000毫秒（1秒）
            acceptsMagicVariable = true, // 允许使用魔法变量指定延迟时间
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME) // 接受数字类型的魔法变量
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME) // 输出一个布尔值表示延迟是否成功完成
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如：“延迟 [1000] 毫秒”
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val durationParam = step.parameters["duration"]
        // 判断参数值是否为魔法变量引用
        val isVariable = (durationParam as? String)?.startsWith("{{") == true && (durationParam as? String)?.endsWith("}}") == true

        // 格式化延迟时间的显示文本
        val durationText = when {
            isVariable -> durationParam.toString() // 如果是魔法变量，直接显示其引用字符串
            durationParam is Number -> { // 如果是数字，整数不显示小数点
                if (durationParam.toDouble() == durationParam.toLong().toDouble()) {
                    durationParam.toLong().toString()
                } else {
                    durationParam.toString() // 小数则按原样显示，或可进一步格式化
                }
            }
            else -> durationParam?.toString() ?: "1000" // 其他情况或默认值
        }

        // 使用 PillUtil 构建带样式的摘要
        return PillUtil.buildSpannable(
            context,
            "延迟 ",
            PillUtil.Pill(durationText, isVariable, parameterId = "duration"), // 延迟时间药丸
            " 毫秒"
        )
    }

    /**
     * 验证模块参数的有效性。
     * 确保延迟时间不为负数。
     */
    override fun validate(step: ActionStep): ValidationResult {
        val duration = step.parameters["duration"]
        // 检查参数是否为字符串（可能是魔法变量或直接输入的数字字符串）
        if (duration is String) {
            try {
                // 如果不是魔法变量，尝试转换为长整型并检查是否为负
                if (!duration.startsWith("{{")) { 
                    if (duration.toLong() < 0) {
                        return ValidationResult(false, "延迟时间不能为负数")
                    }
                }
            } catch (e: Exception) {
                // 如果转换失败且不是魔法变量，则格式无效
                if (!duration.startsWith("{{")) {
                    return ValidationResult(false, "无效的数字格式")
                }
            }
        } else if (duration is Number && duration.toLong() < 0) { // 如果参数直接是数字类型，检查是否为负
            return ValidationResult(false, "延迟时间不能为负数")
        }
        return ValidationResult(true) // 默认有效
    }

    /**
     * 执行延迟操作的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取延迟时间，优先从魔法变量，其次从静态变量
        val durationValue = context.magicVariables["duration"] ?: context.variables["duration"]

        // 将获取到的值转换为长整型毫秒数
        val duration = when(durationValue) {
            is NumberVariable -> durationValue.value.toLong() // 如果是 NumberVariable
            is Number -> durationValue.toLong() // 如果是普通 Number 类型
            is String -> durationValue.toLongOrNull() ?: 1000L // 如果是字符串，尝试解析，失败则用默认值
            else -> 1000L // 其他未知类型，使用默认值
        }

        // 检查延迟时间是否为负
        if (duration < 0) {
            return ExecutionResult.Failure("参数错误", "延迟时间不能为负数: $duration ms")
        }

        // 如果延迟时间大于0，则执行实际的协程延迟
        if (duration > 0) {
            onProgress(ProgressUpdate("正在延迟 ${duration}ms..."))
            delay(duration)
        }
        // 返回成功结果
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}