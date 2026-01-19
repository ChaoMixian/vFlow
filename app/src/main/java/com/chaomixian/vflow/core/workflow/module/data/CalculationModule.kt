// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CalculationModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 计算模块，用于执行两个数字之间的基本数学运算。
 */
class CalculationModule : BaseModule() {

    // 模块的唯一标识符
    override val id = "vflow.data.calculation"

    // 模块的元数据，定义其在编辑器中的显示名称、描述、图标和分类
    override val metadata: ActionMetadata = ActionMetadata(
        name = "计算",
        description = "执行两个数字之间的数学运算。",
        iconRes = R.drawable.rounded_calculate_24,
        category = "数据"
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operand1",
            name = "数字1",
            // 将类型从 NUMBER 改为 STRING，以同时接受数字字面量和变量引用字符串
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            defaultValue = "0" // 默认值也改为字符串
        ),
        InputDefinition(
            id = "operator",
            name = "符号",
            staticType = ParameterType.ENUM,
            options = listOf("+", "-", "*", "/", "%"),
            defaultValue = "+",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "operand2",
            name = "数字2",
            // 为了支持命名变量，将类型从 NUMBER 改为 STRING
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            defaultValue = "0" // 默认值也改为字符串
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            name = "结果",
            typeName = VTypeRegistry.NUMBER.id
        )
    )

    /**
     * 执行模块的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val operand1Value = context.magicVariables["operand1"] ?: context.variables["operand1"]
        val operator = context.variables["operator"] as? String ?: "+"
        val operand2Value = context.magicVariables["operand2"] ?: context.variables["operand2"]

        val num1 = convertToBigDecimal(operand1Value)
        val num2 = convertToBigDecimal(operand2Value)

        if (num1 == null) {
            return ExecutionResult.Failure("输入错误", "数字1无法解析: '${operand1Value?.toString() ?: "null"}'")
        }
        if (num2 == null) {
            return ExecutionResult.Failure("输入错误", "数字2无法解析: '${operand2Value?.toString() ?: "null"}'")
        }

        val resultValue: BigDecimal = when (operator) {
            "+" -> num1.add(num2)
            "-" -> num1.subtract(num2)
            "*" -> num1.multiply(num2)
            "/" -> {
                if (num2.compareTo(BigDecimal.ZERO) == 0) {
                    return ExecutionResult.Failure("计算错误", "除数不能为零。")
                }
                try {
                    num1.divide(num2, 2, RoundingMode.HALF_UP)
                } catch (e: ArithmeticException) {
                    return ExecutionResult.Failure("计算错误", "除法计算时发生错误: ${'$'}{e.message}")
                }
            }
            "%" -> {
                if (num2.compareTo(BigDecimal.ZERO) == 0) {
                    return ExecutionResult.Failure("计算错误", "除数不能为零。")
                }
                num1.remainder(num2)
            }
            else -> return ExecutionResult.Failure("计算错误", "无效的运算符: '${'$'}{operator}'.")
        }
        return ExecutionResult.Success(mapOf("result" to VNumber(resultValue.toDouble())))
    }

    /**
     * 将任意类型的值转换为 BigDecimal?。
     */
    private fun convertToBigDecimal(value: Any?): BigDecimal? {
        return when (value) {
            is VNumber -> value.raw.toBigDecimal()
            is Number -> value.toString().toBigDecimalOrNull()
            is String -> value.toBigDecimalOrNull()
            else -> null
        }
    }

    /**
     * 验证模块参数的有效性。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    /**
     * 创建此模块对应的默认动作步骤列表。
     */
    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = this.id, parameters = emptyMap()))

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()

        val pillOperand1 = PillUtil.createPillFromParam(
            step.parameters["operand1"],
            inputs.find { it.id == "operand1" }
        )
        val pillOperator = PillUtil.createPillFromParam(
            step.parameters["operator"],
            inputs.find { it.id == "operator" },
            isModuleOption = true
        )
        val pillOperand2 = PillUtil.createPillFromParam(
            step.parameters["operand2"],
            inputs.find { it.id == "operand2" }
        )

        return PillUtil.buildSpannable(context, "计算 ", pillOperand1, " ", pillOperator, " ", pillOperand2)
    }
}
