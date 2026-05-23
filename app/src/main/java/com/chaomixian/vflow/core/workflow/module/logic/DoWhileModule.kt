package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableType
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.parser.VariablePathParser
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

const val DO_WHILE_PAIRING_ID = "do_while"
const val DO_WHILE_START_ID = "vflow.logic.do_while.start"
const val DO_WHILE_END_ID = "vflow.logic.do_while.end"

class DoWhileModule : BaseBlockModule() {
    override val id = DO_WHILE_START_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_do_while_start_name,
        descriptionStringRes = R.string.module_vflow_logic_do_while_start_desc,
        name = "循环直到",
        description = "先执行循环体，再判断条件是否继续",
        iconRes = R.drawable.rounded_repeat_24,
        category = "逻辑控制",
        categoryId = "logic"
    )
    override val pairingId = DO_WHILE_PAIRING_ID
    override val stepIdsInBlock = listOf(DO_WHILE_START_ID, DO_WHILE_END_ID)
    override val editorTargetStepIndex = 1

    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_logic_do_while_start)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("进入循环体。"))
        return ExecutionResult.Success(emptyMap())
    }
}

class EndDoWhileModule : BaseModule() {
    override val id = DO_WHILE_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_do_while_end_name,
        descriptionStringRes = R.string.module_vflow_logic_do_while_end_desc,
        name = "结束循环",
        description = "循环直到块的结束点，判断条件是否继续循环",
        iconRes = R.drawable.ic_control_flow,
        category = "逻辑控制",
        categoryId = "logic"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, DO_WHILE_PAIRING_ID)

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        val currentParameters = step?.parameters ?: emptyMap()

        val enableTypeFilter = isTypeFilterEnabled()

        val dynamicInputs = mutableListOf<InputDefinition>()
        dynamicInputs.add(staticInputs.first { it.id == "input1" })

        val input1Value = currentParameters["input1"] as? String
        val availableOperators = if (!enableTypeFilter || input1Value == null) {
            ALL_OPERATORS
        } else {
            val input1TypeName = resolveVariableType(input1Value, allSteps, step)
            getOperatorsForVariableType(input1TypeName)
        }

        val operatorInput = staticInputs.first { it.id == "operator" }
        dynamicInputs.add(
            operatorInput.copy(
                options = availableOperators,
                optionsStringRes = availableOperators.mapNotNull(CONDITION_OPERATOR_OPTION_RES_ID_MAP::get),
                legacyValueMap = CONDITION_OPERATOR_LEGACY_MAP
            )
        )

        val rawOperator = currentParameters["operator"] as? String ?: OP_EXISTS
        val selectedOperator = operatorInput.normalizeEnumValue(rawOperator) ?: rawOperator

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
        val allowedOperators = when (variableTypeName) {
            VTypeRegistry.STRING.id, VTypeRegistry.SCREEN_ELEMENT.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_TEXT
            VTypeRegistry.NUMBER.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_NUMBER
            VTypeRegistry.BOOLEAN.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_BOOLEAN
            VTypeRegistry.LIST.id, VTypeRegistry.DICTIONARY.id -> OPERATORS_FOR_ANY + OPERATORS_FOR_COLLECTION
            else -> OPERATORS_FOR_ANY
        }.toSet()
        return ALL_OPERATORS.filter(allowedOperators::contains)
    }

    private fun resolveVariableType(variableReference: String?, allSteps: List<ActionStep>?, currentStep: ActionStep?): String? {
        if (variableReference == null || allSteps == null || currentStep == null) return null

        if (variableReference.isNamedVariable()) {
            val varName = VariablePathParser.parseNamedVariablePath(variableReference)?.firstOrNull() ?: return null
            val currentIndex = allSteps.indexOf(currentStep)
            val stepsToCheck = if (currentIndex != -1) allSteps.subList(0, currentIndex) else allSteps
            val creationStep = stepsToCheck.findLast {
                it.moduleId == CreateVariableModule().id && it.parameters["variableName"] == varName
            }
            val userType = creationStep?.parameters?.get("type") as? String
            return VariableType.fromStoredValue(userType)?.typeId
        }

        if (variableReference.isMagicVariable()) {
            val parts = VariablePathParser.parseVariableReference(variableReference)
            val sourceStepId = parts.getOrNull(0) ?: return null
            val sourceOutputId = parts.getOrNull(1) ?: return null
            val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null
            return sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId }?.typeName
        }

        return null
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "input1", nameStringRes = R.string.param_vflow_logic_do_while_end_input1_name, name = "输入", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.BOOLEAN.id, VTypeRegistry.NUMBER.id, VTypeRegistry.STRING.id, VTypeRegistry.DICTIONARY.id, VTypeRegistry.LIST.id, VTypeRegistry.SCREEN_ELEMENT.id)),
        InputDefinition(id = "operator", nameStringRes = R.string.param_vflow_logic_do_while_end_operator_name, name = "条件", staticType = ParameterType.ENUM, defaultValue = OP_EXISTS, options = ALL_OPERATORS, acceptsMagicVariable = false, optionsStringRes = CONDITION_OPERATOR_OPTION_RES_IDS),
        InputDefinition(id = "value1", nameStringRes = R.string.param_vflow_logic_do_while_end_value1_name, name = "比较值 1", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.NUMBER.id, VTypeRegistry.BOOLEAN.id)),
        InputDefinition(id = "value2", nameStringRes = R.string.param_vflow_logic_do_while_end_value2_name, name = "比较值 2", staticType = ParameterType.NUMBER, acceptsMagicVariable = true, acceptsNamedVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id))
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", nameStringRes = R.string.output_vflow_logic_do_while_end_result_name, name = "条件结果", typeName = VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val allInputs = getInputs()
        val inputsForStep = getDynamicInputs(step, null)

        val input1Pill = PillUtil.createPillFromParam(
            step.parameters["input1"],
            allInputs.find { it.id == "input1" }
        )
        val operatorPill = PillUtil.createPillFromParam(
            step.parameters["operator"] ?: OP_EXISTS,
            allInputs.find { it.id == "operator" },
            isModuleOption = true
        )

        val parts = mutableListOf<Any>(
            context.getString(R.string.summary_vflow_logic_do_while_prefix),
            input1Pill,
            " ",
            operatorPill
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

        parts.add(" ")
        parts.add(context.getString(R.string.summary_vflow_logic_do_while_suffix))

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val input1 = context.getVariable("input1")
        val rawOperator = context.getVariableAsString("operator", OP_EXISTS)
        val operator = getInputs().first { it.id == "operator" }.normalizeEnumValue(rawOperator) ?: rawOperator
        val value1 = context.getVariable("value1")
        val value2 = context.getVariable("value2")

        val result = ConditionEvaluator.evaluateCondition(input1, operator, value1, value2)
        onProgress(ProgressUpdate("条件判断: $result (操作: $operator)"))

        if (result) {
            onProgress(ProgressUpdate("条件仍然成立，继续循环。"))
            val startPc = BlockNavigator.findBlockStartPosition(context.allSteps, context.currentStepIndex, DO_WHILE_START_ID)
            return if (startPc != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(startPc + 1))
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的 '循环直到' 模块")
            }
        } else {
            onProgress(ProgressUpdate("条件不满足，跳出循环。"))
            return ExecutionResult.Success(mapOf("result" to VBoolean(false)))
        }
    }

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
