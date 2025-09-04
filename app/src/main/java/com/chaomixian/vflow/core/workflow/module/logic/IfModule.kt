package com.chaomixian.vflow.core.workflow.module.logic // Corrected package

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.* // Imports BaseModule, BaseBlockModule, ModuleRegistry etc.
import com.chaomixian.vflow.core.workflow.model.ActionStep
// Corrected import for ScreenElement
import com.chaomixian.vflow.core.workflow.module.device.ScreenElement
// Corrected specific imports for Variable types
import com.chaomixian.vflow.core.workflow.module.data.TextVariable
import com.chaomixian.vflow.core.workflow.module.data.NumberVariable
import com.chaomixian.vflow.core.workflow.module.data.BooleanVariable
import com.chaomixian.vflow.core.workflow.module.data.ListVariable
import com.chaomixian.vflow.core.workflow.module.data.DictionaryVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

// --- Constants remain the same ---
const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

const val OP_EXISTS = "存在"
const val OP_NOT_EXISTS = "不存在"
const val OP_IS_EMPTY = "为空"
const val OP_IS_NOT_EMPTY = "不为空"
const val OP_TEXT_EQUALS = "等于(文本)"
const val OP_TEXT_NOT_EQUALS = "不等于(文本)"
const val OP_CONTAINS = "包含"
const val OP_NOT_CONTAINS = "不包含"
const val OP_STARTS_WITH = "开头是"
const val OP_ENDS_WITH = "结尾是"
const val OP_MATCHES_REGEX = "匹配正则"
const val OP_NUM_EQ = "等于"
const val OP_NUM_NEQ = "不等于"
const val OP_NUM_GT = "大于"
const val OP_NUM_GTE = "大于等于"
const val OP_NUM_LT = "小于"
const val OP_NUM_LTE = "小于等于"
const val OP_NUM_BETWEEN = "介于"
const val OP_IS_TRUE = "为真"
const val OP_IS_FALSE = "为假"

val OPERATORS_REQUIRING_ONE_INPUT = setOf(
    OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS,
    OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX,
    OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE
)
val OPERATORS_REQUIRING_TWO_INPUTS = setOf(OP_NUM_BETWEEN)

val OPERATORS_FOR_ANY = listOf(OP_EXISTS, OP_NOT_EXISTS)
val OPERATORS_FOR_TEXT = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY, OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS, OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX)
val OPERATORS_FOR_NUMBER = listOf(OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE, OP_NUM_BETWEEN)
val OPERATORS_FOR_BOOLEAN = listOf(OP_IS_TRUE, OP_IS_FALSE)
val OPERATORS_FOR_COLLECTION = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY)

val ALL_OPERATORS = (OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT + OPERATORS_FOR_NUMBER + OPERATORS_FOR_BOOLEAN + OPERATORS_FOR_COLLECTION).distinct()


class IfModule : BaseBlockModule() { // BaseBlockModule is in com.chaomixian.vflow.core.module
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID)

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()
        val input1Value = currentParameters["input1"] as? String

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
            // Uses imported NumberVariable
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
        // Uses imported Variable types
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
        if (variableReference == null || !variableReference.startsWith("{{") || allSteps == null) {
            return null
        }
        val parts = variableReference.removeSurrounding("{{", "}}").split('.')
        val sourceStepId = parts.getOrNull(0) ?: return null
        val sourceOutputId = parts.getOrNull(1) ?: return null

        val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
        // ModuleRegistry is in com.chaomixian.vflow.core.module
        val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

        return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // Uses imported Variable types and ScreenElement
        InputDefinition(id = "input1", name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(BooleanVariable.TYPE_NAME, NumberVariable.TYPE_NAME, TextVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME, ListVariable.TYPE_NAME, ScreenElement.TYPE_NAME)),
        InputDefinition(id = "operator", name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, NumberVariable.TYPE_NAME, BooleanVariable.TYPE_NAME)),
        InputDefinition(id = "value2", name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        // Uses imported BooleanVariable
        OutputDefinition("result", "条件结果", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val input1Value = step.parameters["input1"]?.toString()
        val operator = step.parameters["operator"] as? String ?: OP_EXISTS
        val value1Param = step.parameters["value1"]
        val value2Param = step.parameters["value2"]

        val parts = mutableListOf<Any>()
        parts.add("如果 ")
        parts.add(PillUtil.Pill(input1Value ?: "...", input1Value != null, parameterId = "input1"))
        parts.add(" ")

        fun formatNumberForPill(param: Any?): String {
            return when {
                (param as? String)?.startsWith("{{") == true -> param.toString()
                param is Number -> {
                    if (param.toDouble() == param.toLong().toDouble()) {
                        param.toLong().toString()
                    } else {
                        param.toString()
                    }
                }
                else -> param?.toString() ?: "..."
            }
        }

        if (operator == OP_NUM_BETWEEN) {
            parts.add(PillUtil.Pill(OP_NUM_BETWEEN, false, parameterId = "operator", isModuleOption = true))
            parts.add(" ")
            val value1Text = formatNumberForPill(value1Param)
            val isValue1Var = (value1Param as? String)?.startsWith("{{") == true
            parts.add(PillUtil.Pill(value1Text, isValue1Var, parameterId = "value1"))
            parts.add(" 和 ")
            val value2Text = formatNumberForPill(value2Param)
            val isValue2Var = (value2Param as? String)?.startsWith("{{") == true
            parts.add(PillUtil.Pill(value2Text, isValue2Var, parameterId = "value2"))
            parts.add(" 之间")
        } else {
            parts.add(PillUtil.Pill(operator, false, parameterId = "operator", isModuleOption = true))
            if (OPERATORS_REQUIRING_ONE_INPUT.contains(operator)) {
                parts.add(" ")
                val value1Text = formatNumberForPill(value1Param)
                val isValue1Var = (value1Param as? String)?.startsWith("{{") == true
                parts.add(PillUtil.Pill(value1Text, isValue1Var, parameterId = "value1"))
            }
        }
        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

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

        if (!result) {
            val jumpTo = findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(ELSE_ID, IF_END_ID)
            )
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        // Uses imported BooleanVariable
        return ExecutionResult.Success(mapOf("result" to BooleanVariable(result)))
    }

    private fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        when (operator) {
            OP_EXISTS -> return input1 != null
            OP_NOT_EXISTS -> return input1 == null
        }
        if (input1 == null) return false

        // Uses imported Variable types and ScreenElement
        return when (input1) {
            is TextVariable, is String -> evaluateTextCondition(input1.toStringValue(), operator, value1)
            is BooleanVariable, is Boolean -> evaluateBooleanCondition(input1.toBooleanValue(), operator)
            is ListVariable, is Collection<*> -> evaluateCollectionCondition(input1, operator)
            is DictionaryVariable, is Map<*, *> -> evaluateMapCondition(input1, operator)
            is NumberVariable, is Number -> {
                val value = input1.toDoubleValue() ?: return false
                evaluateNumberCondition(value, operator, value1, value2)
            }
            is ScreenElement -> { // Uses imported ScreenElement
                val text = input1.text ?: return false // ScreenElement.text is nullable
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
        // Uses imported ListVariable
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
        // Uses imported DictionaryVariable
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
        // Uses imported TextVariable
        return when(this) {
            is TextVariable -> this.value
            else -> this?.toString() ?: ""
        }
    }

    private fun Any?.toDoubleValue(): Double? {
        // Uses imported NumberVariable
        return when(this) {
            is NumberVariable -> this.value
            is Number -> this.toDouble()
            is String -> this.toDoubleOrNull()
            else -> null
        }
    }

    private fun Any?.toBooleanValue(): Boolean {
        // Uses imported BooleanVariable
        return when(this) {
            is BooleanVariable -> this.value
            is Boolean -> this
            else -> false
        }
    }
}

// ElseModule and EndIfModule extend BaseModule, which is in com.chaomixian.vflow.core.module
class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "否则"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val ifStepId = findPreviousStepInSameBlock(context.allSteps, context.currentStepIndex, IF_START_ID)
        // Uses imported BooleanVariable
        val ifOutput = ifStepId?.let { context.stepOutputs[it]?.get("result") as? BooleanVariable }?.value

        if (ifOutput == true) {
            onProgress(ProgressUpdate("如果条件为真，跳过否则块。"))
            val jumpTo = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        onProgress(ProgressUpdate("进入否则块"))
        return ExecutionResult.Success()
    }

    private fun findPreviousStepInSameBlock(steps: List<ActionStep>, startPosition: Int, targetId: String): String? {
        // ModuleRegistry is in com.chaomixian.vflow.core.module
        val pairingId = steps[startPosition].moduleId.let { ModuleRegistry.getModule(it)?.blockBehavior?.pairingId } ?: return null
        for (i in (startPosition - 1) downTo 0) {
            val currentStep = steps[i]
            val currentModule = ModuleRegistry.getModule(currentStep.moduleId) ?: continue
            if (currentModule.blockBehavior.pairingId == pairingId && currentModule.id == targetId) {
                return currentStep.id
            }
        }
        return null
    }
}

class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束如果"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success()
}

// Helper function findNextBlockPosition remains the same
fun findNextBlockPosition(steps: List<ActionStep>, startPosition: Int, targetIds: Set<String>): Int {
    val startModule = ModuleRegistry.getModule(steps[startPosition].moduleId)
    val pairingId = startModule?.blockBehavior?.pairingId ?: return -1
    var openBlocks = 1
    for (i in (startPosition + 1) until steps.size) {
        val currentModule = ModuleRegistry.getModule(steps[i].moduleId)
        if (currentModule?.blockBehavior?.pairingId == pairingId) {
            when (currentModule.blockBehavior.type) {
                BlockType.BLOCK_START -> openBlocks++
                BlockType.BLOCK_END -> {
                    openBlocks--
                    if (openBlocks == 0 && targetIds.contains(currentModule.id)) return i
                }
                BlockType.BLOCK_MIDDLE -> {
                    if (openBlocks == 1 && targetIds.contains(currentModule.id)) return i
                }
                else -> {}
            }
        }
    }
    return -1
}