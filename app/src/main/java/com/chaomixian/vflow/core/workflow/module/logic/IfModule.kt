// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/IfModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.module.NumberVariable
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.ListVariable
import com.chaomixian.vflow.core.module.DictionaryVariable
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

// --- 常量定义保持不变 ---
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

// --- 操作符集合 ---
// 需要一个额外输入值的操作符
val OPERATORS_REQUIRING_ONE_INPUT = setOf(
    OP_TEXT_EQUALS, OP_TEXT_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS,
    OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX,
    OP_NUM_EQ, OP_NUM_NEQ, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE
)
// 需要两个额外输入值的操作符 (例如 "介于")
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
 * "如果" (If) 模块，逻辑块的起点。
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

        // 调用新的 resolveVariableType 方法
        val input1TypeName = resolveVariableType(input1Value, allSteps, step)
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
            // "介于" 操作需要两个数字输入，调整第一个比较值的类型
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

    /**
     * 将 resolveMagicVariableType 重构为此方法，以同时处理两种变量类型。
     * @param variableReference 变量引用字符串, 如 `{{...}}` 或 `[[...]]`。
     * @param allSteps 工作流中的所有步骤。
     * @param currentStep 当前步骤，用于确定查找命名变量时的范围。
     * @return 变量的内部类型名称，如 "vflow.type.text"。
     */
    private fun resolveVariableType(variableReference: String?, allSteps: List<ActionStep>?, currentStep: ActionStep?): String? {
        if (variableReference == null || allSteps == null || currentStep == null) {
            return null
        }

        // 1. 检查是否为命名变量
        if (variableReference.isNamedVariable()) {
            val varName = variableReference.removeSurrounding("[[", "]]")
            // 从当前步骤向上查找定义
            val currentIndex = allSteps.indexOf(currentStep)
            val stepsToCheck = if (currentIndex != -1) allSteps.subList(0, currentIndex) else allSteps

            // 反向查找，找到最近的定义
            val creationStep = stepsToCheck.findLast {
                it.moduleId == CreateVariableModule().id && it.parameters["variableName"] == varName
            }

            // 从定义步骤中获取类型
            val userType = creationStep?.parameters?.get("type") as? String
            return userTypeToInternalName(userType)
        }

        // 2. 如果不是命名变量，执行原有的魔法变量检查
        if (variableReference.isMagicVariable()) {
            val parts = variableReference.removeSurrounding("{{", "}}").split('.')
            val sourceStepId = parts.getOrNull(0) ?: return null
            val sourceOutputId = parts.getOrNull(1) ?: return null

            val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null
            return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
        }

        return null
    }

    /**
     * 辅助函数，将用户在UI上选择的类型字符串映射到内部类型名称。
     */
    private fun userTypeToInternalName(userType: String?): String? {
        return when (userType) {
            "文本" -> TextVariable.TYPE_NAME
            "数字" -> NumberVariable.TYPE_NAME
            "布尔" -> BooleanVariable.TYPE_NAME
            "字典" -> DictionaryVariable.TYPE_NAME
            "图像" -> ImageVariable.TYPE_NAME
            // 如果未来有更多类型，在这里添加映射
            else -> null
        }
    }


    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(BooleanVariable.TYPE_NAME, NumberVariable.TYPE_NAME, TextVariable.TYPE_NAME, DictionaryVariable.TYPE_NAME, ListVariable.TYPE_NAME, ScreenElement.TYPE_NAME)),
        InputDefinition(id = "operator", name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, NumberVariable.TYPE_NAME, BooleanVariable.TYPE_NAME)),
        InputDefinition(id = "value2", name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    /** 获取输出参数定义。 */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable.TYPE_NAME)
    )

    /** 生成模块摘要。 */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val allInputs = getInputs()
        // 传入 allSteps 以便正确解析命名变量
        val inputsForStep = getDynamicInputs(step, actionSteps) // 假设 actionSteps 可访问

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
        // 优先从 magicVariables 中获取已解析的值
        val input1 = context.magicVariables["input1"] ?: context.variables["input1"]
        val operator = context.variables["operator"] as? String ?: OP_EXISTS
        val value1 = context.magicVariables["value1"] ?: context.variables["value1"]
        val value2 = context.magicVariables["value2"] ?: context.variables["value2"]

        // [修改] 使用 ConditionEvaluator
        val result = ConditionEvaluator.evaluateCondition(input1, operator, value1, value2)
        onProgress(ProgressUpdate("条件判断: $result (操作: $operator)"))

        // 如果条件不为真，则跳转到 Else 或 EndIf
        if (!result) {
            // 使用 BlockNavigator
            val jumpTo = BlockNavigator.findNextBlockPosition(
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

    private val BaseBlockModule.actionSteps: List<ActionStep>
        get() = emptyList()
}

/**
 * "否则" (Else) 模块，If 逻辑块的中间部分。
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
            // 使用 BlockNavigator
            val jumpTo = BlockNavigator.findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
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
 * "结束如果" (EndIf) 模块，If 逻辑块的结束点。
 */
class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.rounded_alt_route_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束如果"
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success() // EndIf 模块本身不执行特殊逻辑
}