package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 计算模块，用于执行两个数字之间的基本数学运算。
 */
class CalculationModule : BaseModule() {

    // 模块的唯一标识符
    override val id: String = "data.calculation"

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
            staticType = ParameterType.NUMBER, 
            acceptsMagicVariable = true,       
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME), 
            defaultValue = 0.0                 
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
            defaultValue = 0.0
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            name = "结果",
            typeName = NumberVariable.TYPE_NAME 
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

    /**
     * 将任意类型的值转换为 Double?。
     */
    private fun convertToDouble(value: Any?): Double? {
        return when (value) {
            is NumberVariable -> value.value 
            is Number -> value.toDouble()    
            is String -> value.toDoubleOrNull() 
            else -> null                     
        }
    }

    /**
     * 验证模块参数的有效性。
     */
    override fun validate(step: ActionStep): ValidationResult {
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
        val params = step.parameters
        val inputs = getInputs() 

        val operand1RawValue = params["operand1"] 
        val operand1Text = operand1RawValue?.toString()
            ?: inputs.find { it.id == "operand1" }?.defaultValue?.toString() 
            ?: "..." 
        val operand1IsVariable = operand1RawValue is String && operand1RawValue.startsWith("{{") && operand1RawValue.endsWith("}}")

        val operatorText = params["operator"]?.toString()
            ?: inputs.find { it.id == "operator" }?.defaultValue?.toString()
            ?: "+"

        val operand2RawValue = params["operand2"]
        val operand2Text = operand2RawValue?.toString()
            ?: inputs.find { it.id == "operand2" }?.defaultValue?.toString()
            ?: "..."
        val operand2IsVariable = operand2RawValue is String && operand2RawValue.startsWith("{{") && operand2RawValue.endsWith("}}")

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

    /**
     * 格式化数字以便在 "药丸" 中显示。
     */
    private fun formatNumberForPill(number: Number): String {
        return if (number.toDouble() == number.toLong().toDouble()) {
            number.toLong().toString() 
        } else {
            String.format("%.2f", number.toDouble()) 
        }
    }
}