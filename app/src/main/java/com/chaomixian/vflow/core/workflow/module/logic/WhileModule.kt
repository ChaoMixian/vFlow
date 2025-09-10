package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import java.util.regex.Pattern
import kotlin.math.*

// 文件: WhileModule.kt
// 描述: 定义条件循环 (While/EndWhile) 模块，实现根据条件重复执行逻辑。

// --- 循环模块常量定义 ---
const val WHILE_PAIRING_ID = "while" // While块的配对ID
const val WHILE_START_ID = "vflow.logic.while.start" // While模块ID
const val WHILE_END_ID = "vflow.logic.while.end"    // EndWhile模块ID

/**
 * "当条件为真时循环" (While) 模块，逻辑块的起点。
 * 根据条件判断结果，决定是否继续执行块内操作。
 */
class WhileModule : BaseBlockModule() {
    override val id = WHILE_START_ID
    override val metadata = ActionMetadata("循环直到", "当条件为真时重复执行直到条件不满足", R.drawable.rounded_repeat_24, "逻辑控制")
    override val pairingId = WHILE_PAIRING_ID
    override val stepIdsInBlock = listOf(WHILE_START_ID, WHILE_END_ID) // 定义While块包含的模块ID

    /** 获取动态输入参数，与 IfModule 类似。 */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()
        val input1Value = currentParameters["input1"] as? String // 主输入值

        if (input1Value == null) {
            return listOf(staticInputs.first { it.id == "input1" })
        }

        val input1TypeName = resolveMagicVariableType(input1Value, allSteps)
        val availableOperators = getOperatorsForVariableType(input1TypeName)

        val dynamicInputs = mutableListOf<InputDefinition>()
        dynamicInputs.add(staticInputs.first { it.id == "input1" })
        dynamicInputs.add(staticInputs.first { it.id == "operator" }.copy(options = availableOperators))

        val selectedOperator = currentParameters["operator"] as? String

        if (OPERATORS_REQUIRING_ONE_INPUT.contains(selectedOperator)) {
            dynamicInputs.add(staticInputs.first { it.id == "value1" })
        } else if (OPERATORS_REQUIRING_TWO_INPUTS.contains(selectedOperator)) {
            val originalValue1Def = staticInputs.first { it.id == "value1" }
            val newNumberValue1Def = originalValue1Def.copy(
                staticType = ParameterType.NUMBER,
                acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
            )
            dynamicInputs.add(newNumberValue1Def)
            dynamicInputs.add(staticInputs.first { it.id == "value2" })
        }
        return dynamicInputs
    }

    private fun getOperatorsForVariableType(variableTypeName: String?): List<String> {
        return when (variableTypeName) {
            TextVariable.TYPE_NAME, ScreenElement.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT
            NumberVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_NUMBER
            BooleanVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_BOOLEAN
            ListVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_COLLECTION
            null -> OPERATORS_FOR_ANY
            else -> OPERATORS_FOR_ANY
        }.distinct()
    }

    private fun resolveMagicVariableType(variableReference: String?, allSteps: List<ActionStep>?): String? {
        if (variableReference == null || !variableReference.isMagicVariable() || allSteps == null) {
            return null
        }
        val parts = variableReference.removeSurrounding("{{", "}}").split('.')
        val sourceStepId = parts.getOrNull(0) ?: return null
        val sourceOutputId = parts.getOrNull(1) ?: return null

        val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
        val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

        return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(BooleanVariable.TYPE_NAME, NumberVariable.TYPE_NAME, TextVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME, ListVariable.TYPE_NAME, ScreenElement.TYPE_NAME)),
        InputDefinition(id = "operator", name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, NumberVariable.TYPE_NAME, BooleanVariable.TYPE_NAME)),
        InputDefinition(id = "value2", name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val allInputs = getInputs()
        val inputsForStep = getDynamicInputs(step, null)

        val input1Pill = PillUtil.createPillFromParam(
            step.parameters["input1"],
            allInputs.find { it.id == "input1" }
        )
        val operatorPill = PillUtil.createPillFromParam(
            step.parameters["operator"],
            allInputs.find { it.id == "operator" },
            isModuleOption = true
        )

        val parts = mutableListOf<Any>("循环直到 ", input1Pill, " ", operatorPill)

        if (inputsForStep.any { it.id == "value1" }) {
            val value1Pill = PillUtil.createPillFromParam(
                step.parameters["value1"],
                allInputs.find { it.id == "value1" }
            )
            parts.add(" ")
            parts.add(value1Pill)
        }
        if (inputsForStep.any { it.id == "value2" }) {
            val value2Pill = PillUtil.createPillFromParam(
                step.parameters["value2"],
                allInputs.find { it.id == "value2" }
            )
            parts.add(" 和 ")
            parts.add(value2Pill)
            parts.add(" 之间")
        }

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    /**
     * 执行 While 模块的核心逻辑。
     * 评估条件。如果为真，则继续执行下一个步骤。如果为假，则跳到 EndWhile。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val input1 = context.magicVariables["input1"]
        val operator = context.variables["operator"] as? String ?: OP_EXISTS
        val value1 = context.magicVariables["value1"] ?: context.variables["value1"]
        val value2 = context.magicVariables["value2"] ?: context.variables["value2"]

        val result = evaluateCondition(input1, operator, value1, value2)
        onProgress(ProgressUpdate("条件判断: $result (操作: $operator)"))

        if (result) {
            onProgress(ProgressUpdate("条件为真，进入循环体。"))
            return ExecutionResult.Success(mapOf("result" to BooleanVariable(true)))
        } else {
            onProgress(ProgressUpdate("条件为假，跳出循环。"))
            val jumpTo = findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(WHILE_END_ID)
            )
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        return ExecutionResult.Failure("执行错误", "找不到配对的结束循环块")
    }

    private fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        when (operator) {
            OP_EXISTS -> return input1 != null
            OP_NOT_EXISTS -> return input1 == null
        }
        if (input1 == null) return false

        return when (input1) {
            is TextVariable, is String -> evaluateTextCondition(input1.toStringValue(), operator, value1)
            is BooleanVariable, is Boolean -> evaluateBooleanCondition(input1.toBooleanValue(), operator)
            is ListVariable, is Collection<*> -> evaluateCollectionCondition(input1, operator)
            is DictionaryVariable, is Map<*, *> -> evaluateMapCondition(input1, operator)
            is NumberVariable, is Number -> {
                val value = input1.toDoubleValue() ?: return false
                evaluateNumberCondition(value, operator, value1, value2)
            }
            is ScreenElement -> {
                val text = input1.text ?: return false
                evaluateTextCondition(text, operator, value1)
            }
            else -> false
        }
    }

    private fun evaluateTextCondition(text1: String, operator: String, value1: Any?): Boolean {
        val text2 = value1.toStringValue()
        return when (operator) {
            OP_IS_EMPTY -> text1.isEmpty()
            OP_IS_NOT_EMPTY -> text1.isNotEmpty()
            OP_TEXT_EQUALS -> text1.equals(text2, ignoreCase = true)
            OP_TEXT_NOT_EQUALS -> !text1.equals(text2, ignoreCase = true)
            OP_CONTAINS -> text1.contains(text2, ignoreCase = true)
            OP_NOT_CONTAINS -> !text1.contains(text2, ignoreCase = true)
            OP_STARTS_WITH -> text1.startsWith(text2, ignoreCase = true)
            OP_ENDS_WITH -> text1.endsWith(text2, ignoreCase = true)
            OP_MATCHES_REGEX -> try { Pattern.compile(text2).matcher(text1).find() } catch (e: Exception) { false }
            else -> false
        }
    }

    private fun evaluateNumberCondition(num1: Double, operator: String, value1: Any?, value2: Any?): Boolean {
        val num2 = value1.toDoubleValue()
        if (operator == OP_NUM_BETWEEN) {
            val num3 = value2.toDoubleValue()
            if (num2 == null || num3 == null) return false
            val minVal = min(num2, num3)
            val maxVal = max(num2, num3)
            return num1 >= minVal && num1 <= maxVal
        }
        if (num2 == null) return false
        return when (operator) {
            OP_NUM_EQ -> num1 == num2
            OP_NUM_NEQ -> num1 != num2
            OP_NUM_GT -> num1 > num2
            OP_NUM_GTE -> num1 >= num2
            OP_NUM_LT -> num1 < num2
            OP_NUM_LTE -> num1 <= num2
            else -> false
        }
    }

    private fun evaluateBooleanCondition(bool1: Boolean, operator: String): Boolean {
        return when (operator) {
            OP_IS_TRUE -> bool1
            OP_IS_FALSE -> !bool1
            else -> false
        }
    }

    private fun evaluateCollectionCondition(col1: Any, operator: String): Boolean {
        val size = when(col1) {
            is ListVariable -> col1.value.size
            is Collection<*> -> col1.size
            else -> -1
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    private fun evaluateMapCondition(map1: Any, operator: String): Boolean {
        val size = when(map1) {
            is DictionaryVariable -> map1.value.size
            is Map<*,*> -> map1.size
            else -> -1
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    private fun Any?.toStringValue(): String {
        return when(this) {
            is TextVariable -> this.value
            else -> this?.toString() ?: ""
        }
    }

    private fun Any?.toDoubleValue(): Double? {
        return when(this) {
            is NumberVariable -> this.value
            is Number -> this.toDouble()
            is String -> this.toDoubleOrNull()
            else -> null
        }
    }

    private fun Any?.toBooleanValue(): Boolean {
        return when(this) {
            is BooleanVariable -> this.value
            is Boolean -> this
            else -> false
        }
    }
}

/**
 * "结束当条件为真时循环" (EndWhile) 模块，While 逻辑块的结束点。
 */
class EndWhileModule : BaseModule() {
    override val id = WHILE_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, WHILE_PAIRING_ID) // 标记为块结束

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束循环"

    /**
     * 执行 EndWhile 模块的核心逻辑。
     * 负责跳回到对应的 While 模块，继续进行下一次条件判断。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("循环体执行完毕，返回到循环起点。"))
        // 查找与当前 EndWhile 模块配对的 While 模块的位置
        val jumpTo = findBlockStartPosition(context.allSteps, context.currentStepIndex, WHILE_START_ID)
        // 如果找到了，则发出跳转信号回到 While 模块，否则继续执行下一个步骤（即结束循环）
        return if (jumpTo != -1) {
            ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
        } else {
            ExecutionResult.Success()
        }
    }

    /**
     * 在同一个积木块中向前查找特定ID的步骤。
     */
    private fun findBlockStartPosition(steps: List<ActionStep>, startPosition: Int, targetId: String): Int {
        val pairingId = steps.getOrNull(startPosition)?.moduleId?.let { it ->
            com.chaomixian.vflow.core.module.ModuleRegistry.getModule(it)?.blockBehavior?.pairingId
        } ?: return -1

        for (i in (startPosition - 1) downTo 0) {
            val currentStep = steps[i]
            val currentModule = com.chaomixian.vflow.core.module.ModuleRegistry.getModule(currentStep.moduleId) ?: continue
            if (currentModule.blockBehavior.pairingId == pairingId && currentModule.id == targetId) {
                return i
            }
            if (currentModule.blockBehavior.type == BlockType.BLOCK_END && currentModule.blockBehavior.pairingId != pairingId) break
        }
        return -1
    }
}