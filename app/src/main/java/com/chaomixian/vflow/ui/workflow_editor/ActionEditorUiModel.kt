package com.chaomixian.vflow.ui.workflow_editor

import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep

internal data class EditorFieldModel(
    val inputDefinition: InputDefinition,
    val currentValue: Any?
)

internal data class EditorSectionModel(
    val fields: List<EditorFieldModel> = emptyList(),
    val actions: List<EditorAction> = emptyList(),
    val isVisible: Boolean = false
)

internal data class EditorUiModel(
    val uiProvider: ModuleUIProvider?,
    val effectiveParameters: Map<String, Any?>,
    val showCustomUi: Boolean,
    val genericInputsSection: EditorSectionModel,
    val advancedInputsSection: EditorSectionModel,
    val editorActionsSection: EditorSectionModel
) {
    val showsAnyGenericInputs: Boolean
        get() = genericInputsSection.isVisible || advancedInputsSection.isVisible

    val shouldShowAdvancedDivider: Boolean
        get() = genericInputsSection.isVisible && advancedInputsSection.isVisible
}

internal object ActionEditorUiModelBuilder {
    fun build(
        module: ActionModule,
        sessionState: ActionEditorSessionState,
        allSteps: List<ActionStep>?
    ): EditorUiModel {
        val baseStep = sessionState.toActionStep(module.id)
        val baseInputs = module.getDynamicInputs(baseStep, allSteps)
        val correctedParameterValues = computeEnumCorrections(baseInputs, sessionState.asMap())
        val effectiveParameters = sessionState.snapshot().apply {
            putAll(correctedParameterValues)
        }
        val effectiveStep = ActionStep(module.id, effectiveParameters)
        val inputsToShow = module.getDynamicInputs(effectiveStep, allSteps)
        val uiProvider = module.uiProvider
        val handledInputIds = uiProvider?.getHandledInputIds() ?: emptySet()
        val visibleFields = inputsToShow
            .asSequence()
            .filterNot { handledInputIds.contains(it.id) }
            .filter { it.isVisibleForEditor(effectiveParameters) }
            .map { inputDef ->
                EditorFieldModel(
                    inputDefinition = inputDef,
                    currentValue = effectiveParameters[inputDef.id]
                )
            }
            .toList()
        val genericFields = visibleFields.filterNot { it.inputDefinition.isFolded }
        val advancedFields = visibleFields.filter { it.inputDefinition.isFolded }
        val editorActions = module.getEditorActions(effectiveStep, allSteps)

        return EditorUiModel(
            uiProvider = uiProvider,
            effectiveParameters = effectiveParameters,
            showCustomUi = uiProvider?.hasCustomEditor() == true,
            genericInputsSection = EditorSectionModel(
                fields = genericFields,
                isVisible = genericFields.isNotEmpty()
            ),
            advancedInputsSection = EditorSectionModel(
                fields = advancedFields,
                isVisible = advancedFields.isNotEmpty()
            ),
            editorActionsSection = EditorSectionModel(
                actions = editorActions,
                isVisible = editorActions.isNotEmpty()
            )
        )
    }

    private fun computeEnumCorrections(
        inputsToShow: List<InputDefinition>,
        currentParameters: Map<String, Any?>
    ): Map<String, Any?> {
        val correctedValues = linkedMapOf<String, Any?>()

        inputsToShow.forEach { inputDef ->
            if (inputDef.staticType != ParameterType.ENUM) return@forEach

            val currentValue = currentParameters[inputDef.id] as? String ?: return@forEach
            if (inputDef.options.contains(currentValue)) return@forEach

            val correctedValue = inputDef.normalizeEnumValueOrNull(currentValue)
                ?.takeIf { inputDef.options.contains(it) }
                ?: inputDef.defaultValue
            correctedValues[inputDef.id] = correctedValue
        }

        return correctedValues
    }
}

private fun InputDefinition.isVisibleForEditor(currentParameters: Map<String, Any?>): Boolean {
    visibility?.let { return it.isVisible(currentParameters) }
    return !isHidden
}
