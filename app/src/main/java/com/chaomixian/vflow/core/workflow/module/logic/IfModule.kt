package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement // This seems to be a local type, not from VariableTypes
import com.chaomixian.vflow.core.module.TextVariable // 更新导入
import com.chaomixian.vflow.core.module.NumberVariable // 更新导入
import com.chaomixian.vflow.core.module.BooleanVariable // 更新导入
import com.chaomixian.vflow.core.module.ListVariable // 更新导入
import com.chaomixian.vflow.core.module.DictionaryVariable // 更新导入
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

// 文件：IfModule.kt
// 描述：定义条件判断 (If/Else/EndIf) 模块，实现逻辑分支控制。

// --- 条件模块常量定义 ---
const val IF_PAIRING_ID = "if" // If块的配对ID
const val IF_START_ID = "vflow.logic.if.start" // If模块ID
const val ELSE_ID = "vflow.logic.if.middle"  // Else模块ID
const val IF_END_ID = "vflow.logic.if.end"    // EndIf模块ID

// --- 操作符常量定义 ---
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

// --- 操作符集合 ---
// 需要一个额外输入值的操作符
val OPERATORS_REQUIRING_ONE_INPUT = setOf(
    OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS,
    OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX,
    OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE
)
// 需要两个额外输入值的操作符 (例如 \"介于\")
val OPERATORS_REQUIRING_TWO_INPUTS = setOf(OP_NUM_BETWEEN)

// 不同数据类型支持的操作符列表
val OPERATORS_FOR_ANY = listOf(OP_EXISTS, OP_NOT_EXISTS)
val OPERATORS_FOR_TEXT = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY, OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS, OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX)
val OPERATORS_FOR_NUMBER = listOf(OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE, OP_NUM_BETWEEN)
val OPERATORS_FOR_BOOLEAN = listOf(OP_IS_TRUE, OP_IS_FALSE)
val OPERATORS_FOR_COLLECTION = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY)
// 所有可用操作符的集合
val ALL_OPERATORS = (OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT + OPERATORS_FOR_NUMBER + OPERATORS_FOR_BOOLEAN + OPERATORS_FOR_COLLECTION).distinct()

/**
 * \"如果\" (If) 模块，逻辑块的起点。
 * 根据条件判断结果，决定执行流程。
 */
class IfModule : BaseBlockModule() {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID) // 定义If块包含的模块ID

    /** 获取动态输入参数，根据主输入类型和所选操作符调整后续输入项。 */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()
        val input1Value = currentParameters["input1"] as? String // 主输入值

        // 如果主输入为空，则只显示主输入框
        if (input1Value == null) {
            return listOf(staticInputs.first { it.id == "input1" })
        }

        // 解析主输入的魔法变量类型，以确定可用的操作符
        val input1TypeName = resolveMagicVariableType(input1Value, allSteps)
        val availableOperators = getOperatorsForVariableType(input1TypeName)

        val dynamicInputs = mutableListOf<InputDefinition>()
        dynamicInputs.add(staticInputs.first { it.id == "input1" })
        dynamicInputs.add(staticInputs.first { it.id == "operator" }.copy(options = availableOperators)) // 操作符选项根据主输入类型动态调整

        val selectedOperator = currentParameters["operator"] as? String

        // 根据操作符类型，添加相应数量的比较值输入框
        if (OPERATORS_REQUIRING_ONE_INPUT.contains(selectedOperator)) {
            dynamicInputs.add(staticInputs.first { it.id == "value1" })
        } else if (OPERATORS_REQUIRING_TWO_INPUTS.contains(selectedOperator)) {
            val originalValue1Def = staticInputs.first { it.id == "value1" }
            // \"介于\" 操作需要两个数字输入，调整第一个比较值的类型
            val newNumberValue1Def = originalValue1Def.copy(
                staticType = ParameterType.NUMBER,
                acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
            )
            dynamicInputs.add(newNumberValue1Def)
            dynamicInputs.add(staticInputs.first { it.id == "value2" })
        }
        return dynamicInputs
    }

    /** 根据变量类型名称获取适用的操作符列表。 */
    private fun getOperatorsForVariableType(variableTypeName: String?): List<String> {
        return when (variableTypeName) {
            TextVariable.TYPE_NAME, ScreenElement.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT
            NumberVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_NUMBER
            BooleanVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_BOOLEAN
            ListVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME -> OPERATORS_FOR_ANY + OPERATORS_FOR_COLLECTION
            null -> OPERATORS_FOR_ANY // 未指定类型或无法解析时，提供通用操作符
            else -> OPERATORS_FOR_ANY
        }.distinct()
    }

    /** 解析魔法变量引用的类型。 */
    private fun resolveMagicVariableType(variableReference: String?, allSteps: List<ActionStep>?): String? {
        if (variableReference == null || !variableReference.isMagicVariable() || allSteps == null) {
            return null
        }
        val parts = variableReference.removeSurrounding("{{", "}}").split('.')
        val sourceStepId = parts.getOrNull(0) ?: return null
        val sourceOutputId = parts.getOrNull(1) ?: return null

        val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
        val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

        // 从源模块的输出定义中查找类型
        return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
    }

    /** 获取静态输入参数定义。 */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(BooleanVariable.TYPE_NAME, NumberVariable.TYPE_NAME, TextVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME, ListVariable.TYPE_NAME, ScreenElement.TYPE_NAME)),
        InputDefinition(id = "operator", name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, NumberVariable.TYPE_NAME, BooleanVariable.TYPE_NAME)),
        InputDefinition(id = "value2", name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    /** 获取输出参数定义。 */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable.TYPE_NAME) // 输出布尔类型的条件判断结果
    )

    /** 生成模块摘要。 */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val allInputs = getInputs() // 获取静态输入定义作为参考
        val inputsForStep = getDynamicInputs(step, null) // 获取当前步骤的动态输入

        val input1Pill = PillUtil.createPillFromParam(
            step.parameters["input1"],
            allInputs.find { it.id == "input1" }
        )
        val operatorPill = PillUtil.createPillFromParam(
            step.parameters["operator"],
            allInputs.find { it.id == "operator" },
            isModuleOption = true
        )

        val parts = mutableListOf<Any>("如果 ", input1Pill, " ", operatorPill)

        // 仅当比较值输入框实际存在时才添加它们的Pill
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

    /** 执行条件判断。 */
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

        // 如果条件不为真，则跳转到 Else 或 EndIf
        if (!result) {
            val jumpTo = findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(ELSE_ID, IF_END_ID) // 可能的跳转目标
            )
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        return ExecutionResult.Success(mapOf("result" to BooleanVariable(result)))
    }

    /** 根据输入、操作符和比较值评估条件。 */
    private fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        when (operator) {
            OP_EXISTS -> return input1 != null
            OP_NOT_EXISTS -> return input1 == null
        }
        if (input1 == null) return false // 对于其他操作符，主输入为null则条件不成立

        // 根据主输入的数据类型，调用相应的评估函数
        return when (input1) {
            is TextVariable, is String -> evaluateTextCondition(input1.toStringValue(), operator, value1)
            is BooleanVariable, is Boolean -> evaluateBooleanCondition(input1.toBooleanValue(), operator)
            is ListVariable, is Collection<*> -> evaluateCollectionCondition(input1, operator)
            is DictionaryVariable, is Map<*, *> -> evaluateMapCondition(input1, operator)
            is NumberVariable, is Number -> {
                val value = input1.toDoubleValue() ?: return false // 数字转换失败则条件不成立
                evaluateNumberCondition(value, operator, value1, value2)
            }
            is ScreenElement -> {
                val text = input1.text ?: return false // 屏幕元素无文本则条件不成立 (除非是EXISTS/NOT_EXISTS)
                evaluateTextCondition(text, operator, value1)
            }
            else -> false
        }
    }

    /** 评估文本相关条件。 */
    private fun evaluateTextCondition(text1: String, operator: String, value1: Any?): Boolean {
        val text2 = value1.toStringValue() // 获取比较值文本
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

    /** 评估数字相关条件。 */
    private fun evaluateNumberCondition(num1: Double, operator: String, value1: Any?, value2: Any?): Boolean {
        val num2 = value1.toDoubleValue() // 获取第一个比较值
        if (operator == OP_NUM_BETWEEN) { // \"介于\" 操作
            val num3 = value2.toDoubleValue() // 获取第二个比较值
            if (num2 == null || num3 == null) return false // 比较值无效则条件不成立
            val minVal = min(num2, num3)
            val maxVal = max(num2, num3)
            return num1 >= minVal && num1 <= maxVal
        }
        if (num2 == null) return false // 其他数字操作，第一个比较值无效则不成立
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

    /** 评估布尔相关条件。 */
    private fun evaluateBooleanCondition(bool1: Boolean, operator: String): Boolean {
        return when (operator) {
            OP_IS_TRUE -> bool1
            OP_IS_FALSE -> !bool1
            else -> false
        }
    }

    /** 评估集合（列表、字典）相关条件。 */
    private fun evaluateCollectionCondition(col1: Any, operator: String): Boolean {
        val size = when(col1) {
            is ListVariable -> col1.value.size
            is Collection<*> -> col1.size
            else -> -1 //无法获取大小
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    /** 评估字典（Map）相关条件。 */
    private fun evaluateMapCondition(map1: Any, operator: String): Boolean {
        val size = when(map1) {
            is DictionaryVariable -> map1.value.size
            is Map<*,*> -> map1.size
            else -> -1 //无法获取大小
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    // --- 类型转换辅助函数 ---
    /** 将任意类型安全转换为字符串。 */
    private fun Any?.toStringValue(): String {
        return when(this) {
            is TextVariable -> this.value
            else -> this?.toString() ?: ""
        }
    }
    /** 将任意类型安全转换为 Double?。 */
    private fun Any?.toDoubleValue(): Double? {
        return when(this) {
            is NumberVariable -> this.value
            is Number -> this.toDouble()
            is String -> this.toDoubleOrNull()
            else -> null
        }
    }
    /** 将任意类型安全转换为布尔值。 */
    private fun Any?.toBooleanValue(): Boolean {
        return when(this) {
            is BooleanVariable -> this.value
            is Boolean -> this
            else -> false // 默认转换为 false
        }
    }
}

/**
 * \"否则\" (Else) 模块，If 逻辑块的中间部分。
 */
class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true) // 可单独删除
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "否则"

    /** 执行 Else 逻辑：如果前置 If 条件为真，则跳过 Else 块。 */
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        // 查找上一个If模块的输出
        val ifStepId = findPreviousStepInSameBlock(context.allSteps, context.currentStepIndex, IF_START_ID)
        val ifOutput = ifStepId?.let { context.stepOutputs[it]?.get("result") as? BooleanVariable }?.value

        if (ifOutput == true) { // 如果 If 条件为真，跳过 Else 块
            onProgress(ProgressUpdate("如果条件为真，跳过否则块。"))
            val jumpTo = findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        onProgress(ProgressUpdate("进入否则块"))
        return ExecutionResult.Success()
    }

    /** 在同一个积木块中向前查找特定ID的步骤。 */
    private fun findPreviousStepInSameBlock(steps: List<ActionStep>, startPosition: Int, targetId: String): String? {
        val pairingId = steps.getOrNull(startPosition)?.moduleId?.let { ModuleRegistry.getModule(it)?.blockBehavior?.pairingId } ?: return null
        for (i in (startPosition - 1) downTo 0) {
            val currentStep = steps[i]
            val currentModule = ModuleRegistry.getModule(currentStep.moduleId) ?: continue
            // 检查配对ID和模块ID是否都匹配
            if (currentModule.blockBehavior.pairingId == pairingId && currentModule.id == targetId) {
                return currentStep.id
            }
            // 如果遇到不同配对ID的块起始，说明已超出当前块范围
            if (currentModule.blockBehavior.type == BlockType.BLOCK_START && currentModule.blockBehavior.pairingId != pairingId) break
        }
        return null
    }
}

/**
 * \"结束如果\" (EndIf) 模块，If 逻辑块的结束点。
 */
class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束如果"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success() // EndIf 模块本身不执行特殊逻辑
}

/**
 * 辅助函数：查找下一个积木块中特定类型的步骤位置（例如 Else 或 EndIf）。
 * @param steps 步骤列表。
 * @param startPosition 当前步骤的索引。
 * @param targetIds 目标模块ID的集合。
 * @return 找到的步骤索引，未找到则返回 -1。
 */
fun findNextBlockPosition(steps: List<ActionStep>, startPosition: Int, targetIds: Set<String>): Int {
    val startModule = ModuleRegistry.getModule(steps.getOrNull(startPosition)?.moduleId ?: return -1)
    val pairingId = startModule?.blockBehavior?.pairingId ?: return -1 // 获取当前块的配对ID
    var openBlocks = 1 // 嵌套块计数器，从当前块开始

    for (i in (startPosition + 1) until steps.size) {
        val currentModule = ModuleRegistry.getModule(steps[i].moduleId)
        if (currentModule?.blockBehavior?.pairingId == pairingId) { // 只关心同一配对ID的模块
            when (currentModule.blockBehavior.type) {
                BlockType.BLOCK_START -> openBlocks++ // 遇到嵌套的开始块，增加计数
                BlockType.BLOCK_END -> {
                    openBlocks-- // 遇到结束块，减少计数
                    // 如果计数归零且是目标ID之一，则找到
                    if (openBlocks == 0 && targetIds.contains(currentModule.id)) return i 
                }
                BlockType.BLOCK_MIDDLE -> {
                    // 如果是中间块（如Else），且当前嵌套层级为1（即当前块的直接子块），且是目标ID之一，则找到
                    if (openBlocks == 1 && targetIds.contains(currentModule.id)) return i
                }
                else -> {} // 其他类型（NONE）不影响计数
            }
        }
    }
    return -1 // 未找到目标
}
