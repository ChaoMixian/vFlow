// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/IfModule.kt
// 描述: If/Else/EndIf 模块的实现。getSummary 已被简化，类型解析逻辑内聚。
package com.chaomixian.vflow.core.workflow.module.logic
import com.chaomixian.vflow.core.types.basic.VBoolean

import android.content.Context
import android.content.SharedPreferences
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
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
const val OP_EQUALS = "等于"              // 弱类型，自动类型转换
const val OP_STRICT_EQUALS = "严格等于"    // 强类型，类型和值都必须相同
const val OP_NOT_EQUALS = "不等于"
const val OP_CONTAINS = "包含"
const val OP_NOT_CONTAINS = "不包含"
const val OP_STARTS_WITH = "开头是"
const val OP_ENDS_WITH = "结尾是"
const val OP_MATCHES_REGEX = "匹配正则"
const val OP_NUM_GT = "大于"
const val OP_NUM_GTE = "大于等于"
const val OP_NUM_LT = "小于"
const val OP_NUM_LTE = "小于等于"
const val OP_NUM_BETWEEN = "介于"
const val OP_IS_TRUE = "为真"
const val OP_IS_FALSE = "为假"

val OPERATORS_REQUIRING_ONE_INPUT = setOf(
    OP_EQUALS, OP_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS,
    OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX,
    OP_STRICT_EQUALS, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE
)
val OPERATORS_REQUIRING_TWO_INPUTS = setOf(OP_NUM_BETWEEN)

val OPERATORS_FOR_ANY = listOf(OP_EXISTS, OP_NOT_EXISTS)
val OPERATORS_FOR_TEXT = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY, OP_EQUALS, OP_NOT_EQUALS, OP_CONTAINS, OP_NOT_CONTAINS, OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX)
val OPERATORS_FOR_NUMBER = listOf(OP_EQUALS, OP_STRICT_EQUALS, OP_NOT_EQUALS, OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE, OP_NUM_BETWEEN)
val OPERATORS_FOR_BOOLEAN = listOf(OP_IS_TRUE, OP_IS_FALSE)
val OPERATORS_FOR_COLLECTION = listOf(OP_IS_EMPTY, OP_IS_NOT_EMPTY)
val ALL_OPERATORS = (OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT + OPERATORS_FOR_NUMBER + OPERATORS_FOR_BOOLEAN + OPERATORS_FOR_COLLECTION).distinct()

/**
 * "如果" (If) 模块。
 */
class IfModule : BaseBlockModule() {
    override val id = IF_START_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_if_start_name,
        descriptionStringRes = R.string.module_vflow_logic_if_start_desc,
        name = "如果",
        description = "根据条件执行不同的操作",
        iconRes = R.drawable.rounded_alt_route_24,
        category = "逻辑控制"
    )
    override val pairingId = IF_PAIRING_ID
    override val stepIdsInBlock = listOf(IF_START_ID, ELSE_ID, IF_END_ID)

    /** 获取静态输入参数定义。 */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", nameStringRes = R.string.param_vflow_logic_if_start_input1_name, name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.BOOLEAN.id, VTypeRegistry.NUMBER.id, VTypeRegistry.STRING.id, VTypeRegistry.DICTIONARY.id, VTypeRegistry.LIST.id, VTypeRegistry.SCREEN_ELEMENT.id)),
        InputDefinition(id = "operator", nameStringRes = R.string.param_vflow_logic_if_start_operator_name, name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", nameStringRes = R.string.param_vflow_logic_if_start_value1_name, name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.NUMBER.id, VTypeRegistry.BOOLEAN.id)),
        InputDefinition(id = "value2", nameStringRes = R.string.param_vflow_logic_if_start_value2_name, name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id))
    )

    /**
     * 获取动态输入参数，根据主输入类型和所选操作符调整后续输入项。
     * 条件输入（operator、value1、value2）始终显示，不依赖 input1 是否已连接。
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()

        // 获取是否启用类型限制的设置（默认关闭，快捷指令风格）
        val enableTypeFilter = isTypeFilterEnabled()

        val dynamicInputs = mutableListOf<InputDefinition>()
        dynamicInputs.add(staticInputs.first { it.id == "input1" })

        // 根据 input1 是否已连接来决定可用的操作符
        val input1Value = currentParameters["input1"] as? String
        val availableOperators = if (!enableTypeFilter || input1Value == null) {
            ALL_OPERATORS  // 未启用类型限制或 input1 未连接时，使用所有操作符
        } else {
            val input1TypeName = resolveVariableType(input1Value, allSteps, step)
            getOperatorsForVariableType(input1TypeName)
        }

        dynamicInputs.add(staticInputs.first { it.id == "operator" }.copy(options = availableOperators))

        val selectedOperator = currentParameters["operator"] as? String ?: OP_EXISTS

        if (OPERATORS_REQUIRING_ONE_INPUT.contains(selectedOperator)) {
            dynamicInputs.add(staticInputs.first { it.id == "value1" })
        } else if (OPERATORS_REQUIRING_TWO_INPUTS.contains(selectedOperator)) {
            val originalValue1Def = staticInputs.first { it.id == "value1" }
            val newNumberValue1Def = originalValue1Def.copy(
                staticType = ParameterType.NUMBER,
                acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
            )
            dynamicInputs.add(newNumberValue1Def)
            dynamicInputs.add(staticInputs.first { it.id == "value2" })
        }
        return dynamicInputs
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", nameStringRes = R.string.output_vflow_logic_if_start_result_name, name = "条件结果", typeName = VTypeRegistry.BOOLEAN.id)
    )

    /**
     * [已简化] 生成模块摘要。现在只负责构建结构，不关心渲染。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val allInputs = getInputs()
        val inputsForStep = getDynamicInputs(step, allSteps) // `allSteps` 在Adapter中可用

        val input1Pill = PillUtil.createPillFromParam(step.parameters["input1"], allInputs.find { it.id == "input1" })
        val operatorPill = PillUtil.createPillFromParam(step.parameters["operator"], allInputs.find { it.id == "operator" }, isModuleOption = true)

        val parts = mutableListOf<Any>(context.getString(R.string.summary_vflow_logic_if_prefix), input1Pill, " ", operatorPill)

        if (inputsForStep.any { it.id == "value1" }) {
            val value1Pill = PillUtil.createPillFromParam(step.parameters["value1"], allInputs.find { it.id == "value1" })
            parts.add(" ")
            parts.add(value1Pill)
        }
        if (inputsForStep.any { it.id == "value2" }) {
            val value2Pill = PillUtil.createPillFromParam(step.parameters["value2"], allInputs.find { it.id == "value2" })
            parts.add(context.getString(R.string.summary_vflow_logic_and))
            parts.add(value2Pill)
            parts.add(context.getString(R.string.summary_vflow_logic_between))
        }

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    /**
     * [新增] 这是一个在 `ActionStepAdapter` 中访问 `allSteps` 的 hacky 方式。
     * 更好的长期解决方案是重构 `getSummary` 以接收 `allSteps`。
     * 但为了最小化改动，暂时使用此方法。
     */
    private val allSteps: List<ActionStep>
        get() {
            // 在实际应用中，您需要一种方法来从 `getSummary` 的调用者（Adapter）
            // 获取 `allSteps` 列表。由于接口限制，这里返回一个空列表作为占位符。
            // 正确的实现将在 `ActionStepAdapter` 中完成。
            return emptyList()
        }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val input1 = context.getVariable("input1")
        val operator = context.getVariableAsString("operator", OP_EXISTS)
        val value1 = context.getVariable("value1")
        val value2 = context.getVariable("value2")

        val result = ConditionEvaluator.evaluateCondition(input1, operator, value1, value2)
        onProgress(ProgressUpdate("条件判断: $result (操作: $operator)"))

        // 无论条件结果如何，都需要更新 stepOutputs，以便 ElseModule 能正确读取
        context.stepOutputs[context.allSteps[context.currentStepIndex].id] = mapOf("result" to VBoolean(result))

        if (!result) {
            val jumpTo = BlockNavigator.findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(ELSE_ID, IF_END_ID))
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        return ExecutionResult.Success()
    }

    private fun getOperatorsForVariableType(variableTypeName: String?): List<String> {
        return when (variableTypeName) {
            VTypeRegistry.STRING.id, VTypeRegistry.SCREEN_ELEMENT.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT
            VTypeRegistry.NUMBER.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_NUMBER
            VTypeRegistry.BOOLEAN.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_BOOLEAN
            VTypeRegistry.LIST.id, VTypeRegistry.DICTIONARY.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_COLLECTION
            VTypeRegistry.COORDINATE.id, VTypeRegistry.IMAGE.id, VTypeRegistry.TIME.id, VTypeRegistry.DATE.id, VTypeRegistry.NOTIFICATION.id -> OPERATORS_FOR_ANY
            null -> OPERATORS_FOR_ANY
            else -> OPERATORS_FOR_ANY
        }.distinct()
    }

    private fun resolveVariableType(variableReference: String?, allSteps: List<ActionStep>?, currentStep: ActionStep?): String? {
        if (variableReference == null || allSteps == null || currentStep == null) return null

        if (variableReference.isNamedVariable()) {
            val varName = variableReference.removeSurrounding("[[", "]]")
            val currentIndex = allSteps.indexOf(currentStep)
            val stepsToCheck = if (currentIndex != -1) allSteps.subList(0, currentIndex) else allSteps
            val creationStep = stepsToCheck.findLast {
                it.moduleId == CreateVariableModule().id && it.parameters["variableName"] == varName
            }
            val userType = creationStep?.parameters?.get("type") as? String
            return userTypeToInternalName(userType)
        }

        if (variableReference.isMagicVariable()) {
            val parts = variableReference.removeSurrounding("{{", "}}").split('.')
            val sourceStepId = parts.getOrNull(0) ?: return null
            val sourceOutputId = parts.getOrNull(1) ?: return null
            val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

            // 获取输出的类型
            val outputDef = sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }
            var currentTypeId = outputDef?.typeName

            // 如果有属性访问路径（如 .length、.uppercase），则解析属性的类型
            if (parts.size > 2) {
                currentTypeId = resolvePropertyPath(currentTypeId, parts.drop(2))
            }

            return currentTypeId
        }
        return null
    }

    /**
     * 解析属性路径，返回最终属性的类型ID
     * @param baseTypeId 基础类型ID（如 VTypeRegistry.STRING.id）
     * @param propertyPath 属性路径（如 ["length"] 或 ["uppercase", "length"]）
     * @return 最终属性的类型ID
     */
    private fun resolvePropertyPath(baseTypeId: String?, propertyPath: List<String>): String? {
        var currentTypeId = baseTypeId

        for (propertyName in propertyPath) {
            val propertyType = VTypeRegistry.getPropertyType(currentTypeId, propertyName)
            if (propertyType == null) return currentTypeId  // 如果找不到属性，返回当前类型
            currentTypeId = propertyType.id
        }

        return currentTypeId
    }

    private fun userTypeToInternalName(userType: String?): String? {
        return when (userType) {
            "文本" -> VTypeRegistry.STRING.id
            "数字" -> VTypeRegistry.NUMBER.id
            "布尔" -> VTypeRegistry.BOOLEAN.id
            "字典" -> VTypeRegistry.DICTIONARY.id
            "列表" -> VTypeRegistry.LIST.id
            "图像" -> VTypeRegistry.IMAGE.id
            "坐标" -> VTypeRegistry.COORDINATE.id
            else -> null
        }
    }

    /**
     * 检查是否启用了变量类型限制。
     * 默认关闭（快捷指令风格），通过 SharedPreferences 获取用户设置。
     */
    private fun isTypeFilterEnabled(): Boolean {
        return try {
            val prefs = com.chaomixian.vflow.core.logging.LogManager.applicationContext
                .getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
            prefs.getBoolean("enableTypeFilter", false)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * "否则" (Else) 模块。
 */
class ElseModule : BaseModule() {
    override val id = ELSE_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_if_middle_name,
        descriptionStringRes = R.string.module_vflow_logic_if_middle_desc,
        name = "否则",
        description = "如果条件不满足，则执行这里的操作",
        iconRes = R.drawable.rounded_alt_route_24,
        category = "逻辑控制"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_else)

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val ifStepId = findPreviousStepInSameBlock(context.allSteps, context.currentStepIndex, IF_START_ID)
        // stepOutputs 现在包含 VObject (VBoolean)，使用 asBoolean() 方法
        val ifOutput = ifStepId?.let {
            val vObj = context.stepOutputs[it]?.get("result")
            vObj?.asBoolean()
        }

        if (ifOutput == true) {
            onProgress(ProgressUpdate("如果条件为真，跳过否则块。"))
            val jumpTo = BlockNavigator.findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(IF_END_ID))
            if (jumpTo != -1) {
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo))
            }
        }
        onProgress(ProgressUpdate("进入否则块"))
        return ExecutionResult.Success()
    }

    private fun findPreviousStepInSameBlock(steps: List<ActionStep>, startPosition: Int, targetId: String): String? {
        val pairingId = steps.getOrNull(startPosition)?.moduleId?.let { ModuleRegistry.getModule(it)?.blockBehavior?.pairingId } ?: return null

        var nestingLevel = 0  // 嵌套层级计数器，用于处理 IF 内部的循环等嵌套结构

        for (i in (startPosition - 1) downTo 0) {
            val currentStep = steps[i]
            val currentModule = ModuleRegistry.getModule(currentStep.moduleId) ?: continue
            val behavior = currentModule.blockBehavior

            if (behavior.pairingId == pairingId) {
                // 处理同一配对ID的模块（IF/ELSE/ENDIF）
                if (currentModule.id == targetId) {
                    // 只有在当前层级（nestingLevel == 0）时才认为是找到目标
                    if (nestingLevel == 0) return currentStep.id
                }

                // 更新嵌套层级
                when (behavior.type) {
                    BlockType.BLOCK_END -> nestingLevel++
                    BlockType.BLOCK_START -> nestingLevel--
                    else -> {}  // BLOCK_MIDDLE (ELSE) 不影响层级
                }
            } else if (behavior.type == BlockType.BLOCK_START) {
                // 遇到其他类型的开始块（如 LOOP_START, WHILE_START 等），增加嵌套层级
                nestingLevel++
            } else if (behavior.type == BlockType.BLOCK_END) {
                // 遇到其他类型的结束块（如 LOOP_END 等），减少嵌套层级
                nestingLevel--
            }
        }
        return null
    }
}

/**
 * "结束如果" (EndIf) 模块。
 */
class EndIfModule : BaseModule() {
    override val id = IF_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_if_end_name,
        descriptionStringRes = R.string.module_vflow_logic_if_end_desc,
        name = "结束如果",
        description = "",
        iconRes = R.drawable.rounded_alt_route_24,
        category = "逻辑控制"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_end_if)
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit) = ExecutionResult.Success()
}