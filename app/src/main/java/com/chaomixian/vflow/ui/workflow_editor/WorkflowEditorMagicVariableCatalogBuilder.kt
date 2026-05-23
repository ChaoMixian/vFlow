package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.VariableType
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.parser.VariablePathParser
import com.chaomixian.vflow.core.workflow.GlobalVariableStore
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.core.workflow.module.logic.ForEachModule
import com.chaomixian.vflow.core.workflow.module.logic.LoopModule

internal data class MagicVariablePickerModel(
    val stepVariables: Map<String, List<MagicVariableItem>>,
    val namedVariables: Map<String, List<MagicVariableItem>>,
    val acceptsMagicVariable: Boolean,
    val acceptsNamedVariable: Boolean,
    val acceptedMagicVariableTypes: Set<String>,
    val enableTypeFilter: Boolean
)

internal class WorkflowEditorMagicVariableCatalogBuilder(
    private val context: Context,
    private val workflowManager: WorkflowManager
) {
    fun buildNamedVariables(
        actionSteps: List<ActionStep>,
        upToPosition: Int
    ): Map<String, List<MagicVariableItem>> {
        val availableNamedVariables = linkedMapOf<String, MagicVariableItem>()

        actionSteps.subList(0, upToPosition)
            .filter { it.moduleId == CreateVariableModule().id }
            .forEach { step ->
                val varName = step.parameters["variableName"] as? String
                val varType = step.parameters["type"] as? String ?: context.getString(R.string.variable_type_text)
                if (!varName.isNullOrBlank()) {
                    availableNamedVariables[varName] = namedVariableItem(varName, varType)
                }
            }

        actionSteps.subList(0, upToPosition)
            .filter { it.moduleId == LOAD_VARIABLES_MODULE_ID }
            .forEach { step ->
                val workflowId = step.parameters["workflow_id"] as? String
                val variableNames = (step.parameters["variable_names"] as? String)
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (workflowId.isNullOrBlank() || variableNames.isEmpty()) return@forEach
                val sourceWorkflow = workflowManager.getWorkflow(workflowId) ?: return@forEach
                val sourceVariables = sourceWorkflow.steps
                    .filter { it.moduleId == CreateVariableModule().id }
                    .associate { sourceStep ->
                        val name = sourceStep.parameters["variableName"] as? String ?: ""
                        val type = sourceStep.parameters["type"] as? String ?: context.getString(R.string.variable_type_text)
                        name to type
                    }

                variableNames.forEach { varName ->
                    if (availableNamedVariables.containsKey(varName)) return@forEach
                    val varType = sourceVariables[varName] ?: context.getString(R.string.variable_type_text)
                    availableNamedVariables[varName] = namedVariableItem(varName, varType)
                }
            }

        val globalVariables = GlobalVariableStore.getAll(context)
        if (globalVariables.isNotEmpty()) {
            val globalItems = globalVariables.entries
                .sortedBy { it.key }
                .map { (name, value) ->
                    MagicVariableItem(
                        variableReference = VariablePathParser.buildGlobalVariableReference(name),
                        variableName = name,
                        originDescription = typeDescription(value.type.id),
                        typeId = value.type.id
                    )
                }
            return buildMap {
                if (availableNamedVariables.isNotEmpty()) {
                    put(
                        context.getString(R.string.editor_group_named_variables),
                        availableNamedVariables.values.toList()
                    )
                }
                put(context.getString(R.string.module_config_section_global_variables), globalItems)
            }
        }

        return if (availableNamedVariables.isNotEmpty()) {
            mapOf(context.getString(R.string.editor_group_named_variables) to availableNamedVariables.values.toList())
        } else {
            emptyMap()
        }
    }

    fun buildPickerModel(
        editingStepPosition: Int,
        targetInputId: String,
        editingModule: ActionModule,
        currentParams: Map<String, Any?>?,
        allSteps: List<ActionStep>,
        triggerStepCount: Int,
        actionSteps: List<ActionStep>,
        enableTypeFilter: Boolean,
        findEnclosingLoopStartStep: (position: Int, pairingId: String) -> ActionStep?,
        loopPairingId: String,
        forEachPairingId: String,
        loopStartId: String,
        forEachStartId: String
    ): MagicVariablePickerModel? {
        val targetInputDef = findTargetInputDefinition(targetInputId, editingModule, currentParams, allSteps) ?: return null
        val editingActionIndex = (editingStepPosition - triggerStepCount).coerceAtLeast(0)
        val groupedStepOutputs = linkedMapOf<String, MutableList<MagicVariableItem>>()

        for (i in (editingStepPosition - 1) downTo 0) {
            val step = allSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId) ?: continue
            if (module.id == loopStartId || module.id == forEachStartId) continue

            val outputs = module.getDynamicOutputs(step, allSteps).filter { outputDef ->
                !enableTypeFilter || VTypeRegistry.isTypeOrAnyPropertyAccepted(
                    outputDef.typeName,
                    targetInputDef.acceptedMagicVariableTypes
                )
            }

            if (outputs.isEmpty()) continue
            val groupName = buildGroupTitle(i, triggerStepCount, module.metadata.getLocalizedName(context))
            val items = outputs.map { outputDef ->
                MagicVariableItem(
                    variableReference = "{{${step.id}.${outputDef.id}}}",
                    variableName = outputDef.getLocalizedName(context),
                    originDescription = outputTypeDescription(outputDef),
                    typeId = pickerTypeId(outputDef)
                )
            }
            groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
        }

        appendLoopVariables(
            groupedStepOutputs = groupedStepOutputs,
            allSteps = allSteps,
            actionSteps = actionSteps,
            triggerStepCount = triggerStepCount,
            editingActionIndex = editingActionIndex,
            findEnclosingLoopStartStep = findEnclosingLoopStartStep,
            pairingId = loopPairingId,
            moduleClass = LoopModule::class.java
        )

        appendForEachVariables(
            groupedStepOutputs = groupedStepOutputs,
            allSteps = allSteps,
            actionSteps = actionSteps,
            triggerStepCount = triggerStepCount,
            editingActionIndex = editingActionIndex,
            findEnclosingLoopStartStep = findEnclosingLoopStartStep,
            pairingId = forEachPairingId
        )

        return MagicVariablePickerModel(
            stepVariables = groupedStepOutputs,
            namedVariables = buildNamedVariables(actionSteps, editingActionIndex),
            acceptsMagicVariable = targetInputDef.acceptsMagicVariable,
            acceptsNamedVariable = targetInputDef.acceptsNamedVariable,
            acceptedMagicVariableTypes = targetInputDef.acceptedMagicVariableTypes,
            enableTypeFilter = enableTypeFilter
        )
    }

    private fun findTargetInputDefinition(
        targetInputId: String,
        editingModule: ActionModule,
        currentParams: Map<String, Any?>?,
        allSteps: List<ActionStep>
    ): InputDefinition? {
        val rootInputId = ParamPath.parse(targetInputId).rootId
        val stepForPicker = ActionStep(editingModule.id, currentParams ?: emptyMap())
        return editingModule.getDynamicInputs(stepForPicker, allSteps).find { it.id == rootInputId }
    }

    private fun appendLoopVariables(
        groupedStepOutputs: LinkedHashMap<String, MutableList<MagicVariableItem>>,
        allSteps: List<ActionStep>,
        actionSteps: List<ActionStep>,
        triggerStepCount: Int,
        editingActionIndex: Int,
        findEnclosingLoopStartStep: (position: Int, pairingId: String) -> ActionStep?,
        pairingId: String,
        moduleClass: Class<LoopModule>
    ) {
        val loopStep = findEnclosingLoopStartStep(editingActionIndex, pairingId) ?: return
        val loopModule = moduleClass.cast(ModuleRegistry.getModule(loopStep.moduleId)) ?: return
        val loopIndex = actionSteps.indexOf(loopStep)
        if (loopIndex < 0) return
        val groupName = buildGroupTitle(triggerStepCount + loopIndex, triggerStepCount, loopModule.metadata.getLocalizedName(context))
        val items = loopModule.getDynamicOutputs(loopStep, allSteps).map { outputDef ->
            MagicVariableItem(
                variableReference = "{{${loopStep.id}.${outputDef.id}}}",
                variableName = outputDef.getLocalizedName(context),
                originDescription = outputTypeDescription(outputDef),
                typeId = pickerTypeId(outputDef)
            )
        }
        groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
    }

    private fun appendForEachVariables(
        groupedStepOutputs: LinkedHashMap<String, MutableList<MagicVariableItem>>,
        allSteps: List<ActionStep>,
        actionSteps: List<ActionStep>,
        triggerStepCount: Int,
        editingActionIndex: Int,
        findEnclosingLoopStartStep: (position: Int, pairingId: String) -> ActionStep?,
        pairingId: String
    ) {
        val forEachStep = findEnclosingLoopStartStep(editingActionIndex, pairingId) ?: return
        val forEachModule = ModuleRegistry.getModule(forEachStep.moduleId) as? ForEachModule ?: return
        val forEachIndex = actionSteps.indexOf(forEachStep)
        if (forEachIndex < 0) return
        val groupName = buildGroupTitle(triggerStepCount + forEachIndex, triggerStepCount, forEachModule.metadata.getLocalizedName(context))
        val items = forEachModule.getDynamicOutputs(forEachStep, allSteps).map { outputDef ->
            MagicVariableItem(
                variableReference = "{{${forEachStep.id}.${outputDef.id}}}",
                variableName = outputDef.getLocalizedName(context),
                originDescription = outputTypeDescription(outputDef),
                typeId = pickerTypeId(outputDef)
            )
        }
        groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
    }

    private fun namedVariableItem(varName: String, varType: String): MagicVariableItem {
        val typeEnum = VariableType.fromStoredValue(varType)
        val typeId = typeEnum?.typeId ?: VTypeRegistry.ANY.id
        return MagicVariableItem(
            variableReference = VariablePathParser.buildNamedVariableReference(varName),
            variableName = varName,
            originDescription = context.getString(R.string.error_named_variable, varType),
            typeId = typeId
        )
    }

    private fun buildGroupTitle(stepPositionInAllSteps: Int, triggerStepCount: Int, moduleName: String): String {
        val displayIndex = if (stepPositionInAllSteps < triggerStepCount) {
            0
        } else {
            stepPositionInAllSteps - triggerStepCount + 1
        }
        return "#$displayIndex $moduleName"
    }

    private fun typeDescription(typeId: String): String {
        return "(${VTypeRegistry.getType(typeId).getLocalizedName(context)})"
    }

    private fun outputTypeDescription(outputDef: OutputDefinition): String {
        if (outputDef.typeName == VTypeRegistry.ANY.id) return ""
        if (outputDef.typeName != VTypeRegistry.LIST.id) {
            return typeDescription(outputDef.typeName)
        }

        val listLabel = VTypeRegistry.getType(VTypeRegistry.LIST.id).getLocalizedName(context)
        val elementLabel = outputDef.listElementType
            ?.let { VTypeRegistry.getType(it).getLocalizedName(context) }
        return if (elementLabel != null) {
            "($listLabel<$elementLabel>)"
        } else {
            "($listLabel)"
        }
    }

    private fun pickerTypeId(outputDef: OutputDefinition): String {
        return if (outputDef.typeName == VTypeRegistry.LIST.id) {
            VTypeRegistry.LIST.id
        } else {
            outputDef.typeName
        }
    }

    private companion object {
        const val LOAD_VARIABLES_MODULE_ID = "vflow.variable.load"
    }
}
