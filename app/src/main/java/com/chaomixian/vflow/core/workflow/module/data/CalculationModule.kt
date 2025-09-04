package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BaseModule
// 修正导入：NumberVariable 来自 variable 包
import com.chaomixian.vflow.core.workflow.module.data.NumberVariable
// 添加 PillUtil 的导入
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class CalculationModule : BaseModule() {

    override val id: String = "data.calculation"

    override val metadata: ActionMetadata = ActionMetadata(
        name = "计算",
        description = "执行两个数字之间的数学运算。",
        iconRes = R.drawable.rounded_calculate_24, // 建议使用更相关的图标
        category = "数据" // "数据" 类别在 PillUtil.getCategoryColor 中没有特定颜色，会回退
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operand1",
            name = "数字1",
            staticType = ParameterType.NUMBER,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME),
            defaultValue = 0.0 // 提供一个数字类型的默认值
        ),
        InputDefinition(
            id = "operator",
            name = "符号",
            staticType = ParameterType.ENUM,
            options = listOf("+", "-", "*", "/"),
            defaultValue = "+",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "operand2",
            name = "数字2",
            staticType = ParameterType.NUMBER,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME),
            defaultValue = 0.0 // 提供一个数字类型的默认值
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            name = "结果",
            typeName = NumberVariable.TYPE_NAME
        )
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val operand1Value = context.magicVariables["operand1"] ?: context.variables["operand1"]
        val operator = context.variables["operator"] as? String ?: "+"
        val operand2Value = context.magicVariables["operand2"] ?: context.variables["operand2"]

        val num1 = convertToDouble(operand1Value)
        val num2 = convertToDouble(operand2Value)

        if (num1 == null) {
            return ExecutionResult.Failure("输入错误", "数字1无法解析: '${operand1Value?.toString() ?: "null"}'")
        }
        if (num2 == null) {
            return ExecutionResult.Failure("输入错误", "数字2无法解析: '${operand2Value?.toString() ?: "null"}'")
        }
        
        val resultValue: Double = when (operator) {
            "+" -> num1 + num2
            "-" -> num1 - num2
            "*" -> num1 * num2
            "/" -> {
                if (num2 == 0.0) {
                    return ExecutionResult.Failure("计算错误", "除数不能为零。")
                }
                num1 / num2
            }
            else -> return ExecutionResult.Failure("计算错误", "无效的运算符: '${operator}'.")
        }
        return ExecutionResult.Success(mapOf("result" to NumberVariable(resultValue)))
    }

    private fun convertToDouble(value: Any?): Double? {
        return when (value) {
            is NumberVariable -> value.value
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    override fun validate(step: ActionStep): ValidationResult {
        // 简单验证示例，可以根据需要扩展
        // (省略之前的验证逻辑，通常 BaseModule 的默认实现或更简单的实现就足够了，
        //  除非有特定于此模块的复杂验证规则)
        return ValidationResult(true)
    }
    
    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = this.id, parameters = emptyMap()))
    
    // --- 修改后的 getSummary 方法 ---
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val params = step.parameters
        val inputs = getInputs() // 获取输入定义以访问默认值

        // 获取 Operand1 的文本和是否为变量的标记
        val operand1RawValue = params["operand1"] // 首先获取原始值
        val operand1Text = operand1RawValue?.toString()
            ?: inputs.find { it.id == "operand1" }?.defaultValue?.toString() // 如果为 null, 尝试获取默认值
            ?: "..." // 最终回退
        val operand1IsVariable = operand1RawValue is String && operand1RawValue.startsWith("{{") && operand1RawValue.endsWith("}}")


        // 获取 Operator 的文本
        val operatorText = params["operator"]?.toString()
            ?: inputs.find { it.id == "operator" }?.defaultValue?.toString()
            ?: "+"

        // 获取 Operand2 的文本和是否为变量的标记
        val operand2RawValue = params["operand2"]
        val operand2Text = operand2RawValue?.toString()
            ?: inputs.find { it.id == "operand2" }?.defaultValue?.toString()
            ?: "..."
        val operand2IsVariable = operand2RawValue is String && operand2RawValue.startsWith("{{") && operand2RawValue.endsWith("}}")

        // 创建 Pills
        val pillOperand1 = PillUtil.Pill(
            text = if (operand1IsVariable) operand1Text else (operand1RawValue as? Number)?.let { formatNumberForPill(it) } ?: operand1Text,
            isVariable = operand1IsVariable,
            parameterId = "operand1"
        )
        val pillOperator = PillUtil.Pill(
            text = operatorText,
            isVariable = false,
            parameterId = "operator",
            isModuleOption = true
        )
        val pillOperand2 = PillUtil.Pill(
            text = if (operand2IsVariable) operand2Text else (operand2RawValue as? Number)?.let { formatNumberForPill(it) } ?: operand2Text,
            isVariable = operand2IsVariable,
            parameterId = "operand2"
        )
        
        return PillUtil.buildSpannable(context, "计算 ", pillOperand1, " ", pillOperator, " ", pillOperand2)
    }

    // 用于格式化数字显示的辅助函数 (如果需要，可以做得更复杂)
    private fun formatNumberForPill(number: Number): String {
        return if (number.toDouble() == number.toLong().toDouble()) {
            number.toLong().toString()
        } else {
            String.format("%.2f", number.toDouble()) // 例如，保留两位小数
        }
    }
}