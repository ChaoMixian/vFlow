package com.chaomixian.vflow.ui.workflow_editor

import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.workflow.model.ActionStep

internal class ActionEditorSessionState {
    private val parameters = mutableMapOf<String, Any?>()
    private val parameterTreeEditor = ParameterTreeEditor(parameters)

    operator fun get(key: String): Any? = parameters[key]

    operator fun set(key: String, value: Any?) {
        parameters[key] = value
    }

    fun remove(key: String) {
        parameters.remove(key)
    }

    fun clear() {
        parameters.clear()
    }

    fun putAll(values: Map<String, Any?>) {
        parameters.putAll(values)
    }

    fun replaceAll(values: Map<String, Any?>) {
        parameters.clear()
        parameters.putAll(values)
    }

    fun initializeDefaults(inputs: List<InputDefinition>) {
        inputs.forEach { def ->
            def.defaultValue?.let { parameters[def.id] = it }
        }
    }

    fun setPath(path: ParamPath, value: Any?) {
        parameterTreeEditor.set(path, value)
    }

    fun clearPath(path: ParamPath, topLevelDefaultValue: Any?) {
        parameterTreeEditor.clear(path, topLevelDefaultValue)
    }

    fun asMap(): Map<String, Any?> = parameters

    fun snapshot(): MutableMap<String, Any?> = parameters.toMutableMap()

    fun toMutableMap(): MutableMap<String, Any?> = parameters.toMutableMap()

    fun toActionStep(moduleId: String, stepId: String = ""): ActionStep {
        return ActionStep(moduleId = moduleId, parameters = snapshot(), id = stepId)
    }
}
