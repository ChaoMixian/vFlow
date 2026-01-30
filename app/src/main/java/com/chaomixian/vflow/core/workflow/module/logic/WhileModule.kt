// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/WhileModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val WHILE_PAIRING_ID = "while"
const val WHILE_START_ID = "vflow.logic.while.start"
const val WHILE_END_ID = "vflow.logic.while.end"

class WhileModule : BaseBlockModule() {
    override val id = WHILE_START_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_while_start_name,
        descriptionStringRes = R.string.module_vflow_logic_while_start_desc,
        name = "循环直到",
        description = "当条件为真时重复执行直到条件不满足",
        iconRes = R.drawable.rounded_repeat_24,
        category = "逻辑控制"
    )
    override val pairingId = WHILE_PAIRING_ID
    override val stepIdsInBlock = listOf(WHILE_START_ID, WHILE_END_ID)

    /** 获取动态输入参数，与 IfModule 类似。 */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()
        val input1Value = currentParameters["input1"] as? String

        if (input1Value == null) {
            return listOf(staticInputs.first { it.id == "input1" })
        }

        // **核心修改点**：调用新的 resolveVariableType 方法
        val input1TypeName = resolveVariableType(input1Value, allSteps, step)
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
                acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
            )
            dynamicInputs.add(newNumberValue1Def)
            dynamicInputs.add(staticInputs.first { it.id == "value2" })
        }
        return dynamicInputs
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

    /**
     * 统一处理魔法变量和命名变量的类型解析。
     */
    private fun resolveVariableType(variableReference: String?, allSteps: List<ActionStep>?, currentStep: ActionStep?): String? {
        if (variableReference == null || allSteps == null || currentStep == null) {
            return null
        }

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
            return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
        }

        return null
    }

    /**
     * 辅助函数，映射UI类型到内部类型。
     */
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


    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", nameStringRes = R.string.param_vflow_logic_while_start_input1_name, name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.BOOLEAN.id, VTypeRegistry.NUMBER.id, VTypeRegistry.STRING.id, VTypeRegistry.DICTIONARY.id, VTypeRegistry.LIST.id, VTypeRegistry.SCREEN_ELEMENT.id)),
        InputDefinition(id = "operator", nameStringRes = R.string.param_vflow_logic_while_start_operator_name, name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false),
        InputDefinition(id = "value1", nameStringRes = R.string.param_vflow_logic_while_start_value1_name, name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.NUMBER.id, VTypeRegistry.BOOLEAN.id)),
        InputDefinition(id = "value2", nameStringRes = R.string.param_vflow_logic_while_start_value2_name, name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id))
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", nameStringRes = R.string.output_vflow_logic_while_start_result_name, name = "条件结果", typeName = VTypeRegistry.BOOLEAN.id)
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

        val parts = mutableListOf<Any>(
            context.getString(R.string.summary_vflow_logic_while_prefix),
            input1Pill,
            " ",
            operatorPill,
            context.getString(R.string.summary_vflow_logic_while_suffix)
        )

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
            parts.add(context.getString(R.string.summary_vflow_logic_loop_between))
            parts.add(value2Pill)
            parts.add(context.getString(R.string.summary_vflow_logic_loop_between_suffix))
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
        // 优先从 magicVariables 中获取已解析的值
        val input1 = context.getVariable("input1")
        val operator = context.getVariableAsString("operator", OP_EXISTS)
        val value1 = context.getVariable("value1")
        val value2 = context.getVariable("value2")

        // 使用 ConditionEvaluator
        val result = ConditionEvaluator.evaluateCondition(input1, operator, value1, value2)
        onProgress(ProgressUpdate("条件判断: $result (操作: $operator)"))

        if (result) {
            onProgress(ProgressUpdate("条件为真，进入循环体。"))
            return ExecutionResult.Success(mapOf("result" to VBoolean(true)))
        } else {
            onProgress(ProgressUpdate("条件为假，跳出循环。"))
            // 使用 BlockNavigator
            val jumpTo = BlockNavigator.findNextBlockPosition(
                context.allSteps,
                context.currentStepIndex,
                setOf(WHILE_END_ID)
            )
            if (jumpTo != -1) {
                // 跳转到结束循环模块的下一个位置，以跳出整个循环
                return ExecutionResult.Signal(ExecutionSignal.Jump(jumpTo + 1))
            }
        }
        return ExecutionResult.Failure("执行错误", "找不到配对的结束循环块")
    }
}

/**
 * "结束当条件为真时循环" (EndWhile) 模块，While 逻辑块的结束点。
 */
class EndWhileModule : BaseModule() {
    override val id = WHILE_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_while_end_name,
        descriptionStringRes = R.string.module_vflow_logic_while_end_desc,
        name = "结束循环",
        description = "",
        iconRes = R.drawable.ic_control_flow,
        category = "逻辑控制"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, WHILE_PAIRING_ID) // 标记为块结束

    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_end_loop)

    /**
     * 执行 EndWhile 模块的核心逻辑。
     * 负责跳回到对应的 While 模块，继续进行下一次条件判断。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("循环体执行完毕，返回到循环起点。"))

        // 使用 BlockNavigator
        val whilePc = BlockNavigator.findBlockStartPosition(context.allSteps, context.currentStepIndex, WHILE_START_ID)

        return if (whilePc != -1) {
            // 发出信号，跳转到 While 模块以重新评估条件
            ExecutionResult.Signal(ExecutionSignal.Jump(whilePc))
        } else {
            // 理论上不应发生，但作为保护
            ExecutionResult.Failure("执行错误", "找不到配对的 '循环直到' 模块")
        }
    }
}