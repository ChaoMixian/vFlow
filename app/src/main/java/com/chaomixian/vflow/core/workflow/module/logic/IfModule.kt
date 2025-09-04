// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/IfModule.kt

package com.chaomixian.vflow.modules.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.*
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

// --- 常量定义 ---
const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

// --- 条件操作符定义 ---
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

// --- 操作符所需输入数量的定义 ---
val OPERATORS_REQUIRING_ONE_INPUT = setOf(
    OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS,
    OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX,
    OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE
)
val OPERATORS_REQUIRING_TWO_INPUTS = setOf(OP_NUM_BETWEEN)

// --- 按变量类型预定义操作符列表 ---
val OPERATORS_FOR_ANY = listOf(OP_EXISTS, OP_NOT_EXISTS)
val OPERATORS_FOR_TEXT = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY, OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS, OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX)
val OPERATORS_FOR_NUMBER = listOf(OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE, OP_NUM_BETWEEN)
val OPERATORS_FOR_BOOLEAN = listOf(OP_IS_TRUE, OP_IS_FALSE)
val OPERATORS_FOR_COLLECTION = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY)

// 所有操作符的完整列表，供静态 `getInputs` 方法使用
val ALL_OPERATORS = (OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT + OPERATORS_FOR_NUMBER + OPERATORS_FOR_BOOLEAN + OPERATORS_FOR_COLLECTION).distinct()

class IfModule : BaseBlockModule() {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID)

    /**
     * 【核心优化】
     * 重写此方法以提供动态的输入项定义。
     * 这是实现“智能条件”功能且不破坏编辑器耦合性的关键。
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        // --- 核心修复：将 super.getInputs() 修改为 getInputs() ---
        val staticInputs = getInputs() // 获取静态的、完整的输入定义作为蓝本
        val currentParameters = step?.parameters ?: emptyMap()

        val input1Value = currentParameters["input1"] as? String

        // 如果 "输入" (input1) 还没有连接任何魔法变量，则编辑器中只应显示 "输入" 这一个选项
        if (input1Value == null) {
            return listOf(staticInputs.first { it.id == "input1" })
        }

        // 解析 "输入" (input1) 连接的魔法变量的类型
        val input1TypeName = resolveMagicVariableType(input1Value, allSteps)

        // 根据解析出的变量类型，获取专门为其筛选的、可用的条件操作符列表
        val availableOperators = getOperatorsForVariableType(input1TypeName)

        // 开始构建将要动态显示在编辑器中的输入项列表
        val dynamicInputs = mutableListOf<InputDefinition>()
        dynamicInputs.add(staticInputs.first { it.id == "input1" }) // 总是添加 "输入"

        // 添加 "条件" (operator) 输入项，但将其选项列表 `options` 替换为我们筛选后的 `availableOperators`
        dynamicInputs.add(staticInputs.first { it.id == "operator" }.copy(options = availableOperators))

        // 检查当前步骤中已保存的操作符
        val selectedOperator = currentParameters["operator"] as? String

        // 根据当前选择的操作符，决定是否需要显示 "比较值1" 和 "比较值2"
        if (OPERATORS_REQUIRING_ONE_INPUT.contains(selectedOperator)) {
            // 如果需要一个比较值，直接使用静态定义
            dynamicInputs.add(staticInputs.first { it.id == "value1" })
        } else if (OPERATORS_REQUIRING_TWO_INPUTS.contains(selectedOperator)) {
            // 针对“介于”操作符，动态修改 value1 的类型
            val originalValue1Def = staticInputs.first { it.id == "value1" }
            val newNumberValue1Def = originalValue1Def.copy(
                staticType = ParameterType.NUMBER,
                acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
            )
            dynamicInputs.add(newNumberValue1Def)
            dynamicInputs.add(staticInputs.first { it.id == "value2" })
        }

        return dynamicInputs // 返回最终构建好的、动态的输入项列表
    }

    /**
     * 根据输入的魔法变量类型，返回一个只包含适用条件操作符的列表。
     * @param variableTypeName 变量类型的唯一名称，例如 "vflow.type.text"。
     * @return 筛选后的操作符列表。
     */
    private fun getOperatorsForVariableType(variableTypeName: String?): List<String> {
        return when (variableTypeName) {
            TextVariable.TYPE_NAME, ScreenElement.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT
            NumberVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_NUMBER
            BooleanVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_BOOLEAN
            ListVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_COLLECTION
            null -> OPERATORS_FOR_ANY // 如果没有输入，只提供最基础的判断
            else -> OPERATORS_FOR_ANY // 对于未知类型，也只提供基础判断
        }.distinct()
    }

    /**
     * 解析一个魔法变量引用字符串，并从工作流上下文中找出其原始的类型名称。
     */
    private fun resolveMagicVariableType(variableReference: String?, allSteps: List<ActionStep>?): String? {
        if (variableReference == null || !variableReference.startsWith("{{") || allSteps == null) {
            return null
        }
        val parts = variableReference.removeSurrounding("{{", "}}").split('.')
        val sourceStepId = parts.getOrNull(0) ?: return null
        val sourceOutputId = parts.getOrNull(1) ?: return null

        val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
        val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

        return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
    }

    /**
     * 提供一个静态的、包含所有可能输入项的完整列表。
     * 这是 `getDynamicInputs` 方法获取蓝本的来源。
     */
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
        val input1Value = step.parameters["input1"]?.toString()
        val operator = step.parameters["operator"] as? String ?: OP_EXISTS
        val value1Param = step.parameters["value1"]
        val value2Param = step.parameters["value2"]

        val parts = mutableListOf<Any>()
        parts.add("如果 ")
        parts.add(PillUtil.Pill(input1Value ?: "...", input1Value != null, parameterId = "input1"))
        parts.add(" ")

        // --- ✨ 核心修改 START ✨ ---
        // 辅助函数，用于格式化数字或返回原始字符串
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
        // --- ✨ 核心修改 END ✨ ---

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val input1 = context.magicVariables["input1"]
        val operator = context.variables["operator"] as? String ?: OP_EXISTS
        val value1 = context.magicVariables["value1"] ?: context.variables["value1"]
        val value2 = context.magicVariables["value2"] ?: context.variables["value2"] // <-- 获取第二个值

        val result = evaluateCondition(input1, operator, value1, value2) // <-- 传递第二个值
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
        return ExecutionResult.Success(mapOf("result" to BooleanVariable(result)))
    }

    private fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        // --- 通用条件 ---
        when (operator) {
            OP_EXISTS -> return input1 != null
            OP_NOT_EXISTS -> return input1 == null
        }

        if (input1 == null) return false

        // --- 类型特定的条件 ---
        return when (input1) {
            is TextVariable, is String -> evaluateTextCondition(input1.toStringValue(), operator, value1)
//            is NumberVariable, is Number -> evaluateNumberCondition(input1.toDoubleValue(), operator, value1, value2) // <-- 传递第二个值
            is BooleanVariable, is Boolean -> evaluateBooleanCondition(input1.toBooleanValue(), operator)
            is ListVariable, is Collection<*> -> evaluateCollectionCondition(input1, operator)
            is DictionaryVariable, is Map<*, *> -> evaluateMapCondition(input1, operator)
//            is ScreenElement -> evaluateTextCondition(input1.text, operator, value1)
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
            if (num2 == null || num3 == null) return false // “介于”必须有两个有效的数字
            val minVal = min(num2, num3)
            val maxVal = max(num2, num3)
            return num1 >= minVal && num1 <= maxVal
        }

        if (num2 == null) return false // 对于其他数字操作，第一个比较值必须有效
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

    // --- 类型转换辅助函数 ---
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

class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "否则"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        // 【核心修改】
        // 1. 找到上一个 If 模块的执行结果
        val ifStepId = findPreviousStepInSameBlock(context.allSteps, context.currentStepIndex, IF_START_ID)
        val ifOutput = ifStepId?.let { context.stepOutputs[it]?.get("result") as? BooleanVariable }?.value

        // 2. 如果 If 条件为真，则需要跳过整个 Else 块
        //    (这说明流程是自然执行到这里的，而不是被 If 模块跳转过来的)
        if (ifOutput == true) {
            onProgress(ProgressUpdate("如果条件为真，跳过否则块。"))
            val jumpTo = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }

        // 3. 如果 If 条件为假，或者没有找到 If 的结果，则流程应正常执行 Else 块
        onProgress(ProgressUpdate("进入否则块"))
        return ExecutionResult.Success()
    }

    // 辅助函数：向前查找同一块中的特定步骤
    private fun findPreviousStepInSameBlock(steps: List<ActionStep>, startPosition: Int, targetId: String): String? {
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