package com.chaomixian.vflow.ui.workflow_editor

import com.chaomixian.vflow.core.workflow.model.ActionStep
import java.util.UUID

object WorkflowClipboardStore {
    private var copiedSteps: List<StepSnapshot> = emptyList()

    fun storeSteps(steps: List<ActionStep>) {
        copiedSteps = steps.map {
            StepSnapshot(
                oldId = it.id,
                moduleId = it.moduleId,
                parameters = deepCopyParameters(it.parameters),
                isDisabled = it.isDisabled,
                indentationLevel = it.indentationLevel
            )
        }
    }

    fun hasSteps(): Boolean = copiedSteps.isNotEmpty()

    fun getStepsForInsertion(): List<ActionStep> {
        if (copiedSteps.isEmpty()) return emptyList()

        val idMap = copiedSteps.associate { it.oldId to UUID.randomUUID().toString() }
        return copiedSteps.map { snapshot ->
            snapshot.toActionStep(idMap)
        }
    }

    private data class StepSnapshot(
        val oldId: String,
        val moduleId: String,
        val parameters: Map<String, Any?>,
        val isDisabled: Boolean,
        val indentationLevel: Int
    ) {
        fun toActionStep(idMap: Map<String, String>): ActionStep {
            val newId = idMap[oldId] ?: UUID.randomUUID().toString()
            return ActionStep(
                moduleId = moduleId,
                parameters = rewriteStepParameters(deepCopyParameters(parameters), idMap),
                isDisabled = isDisabled,
                indentationLevel = indentationLevel,
                id = newId
            )
        }
    }
}

private fun deepCopyParameters(parameters: Map<String, Any?>): Map<String, Any?> {
    return parameters.mapValues { (_, value) -> deepCopyValue(value) }
}

private fun deepCopyValue(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> value.entries.associate { (key, mapValue) ->
            key.toString() to deepCopyValue(mapValue)
        }
        is List<*> -> value.map { item -> deepCopyValue(item) }
        else -> value
    }
}

private fun rewriteStepParameters(
    parameters: Map<String, Any?>,
    idMap: Map<String, String>
): Map<String, Any?> {
    return parameters.mapValues { (_, value) -> rewriteStepReferences(value, idMap) }
}

private fun rewriteStepReferences(value: Any?, idMap: Map<String, String>): Any? {
    return when (value) {
        null -> null
        is String -> rewriteStepReferenceString(value, idMap)
        is Map<*, *> -> value.entries.associate { (key, mapValue) ->
            key.toString() to rewriteStepReferences(mapValue, idMap)
        }
        is List<*> -> value.map { item -> rewriteStepReferences(item, idMap) }
        is Set<*> -> value.mapTo(linkedSetOf()) { item -> rewriteStepReferences(item, idMap) }
        is Array<*> -> value.map { item -> rewriteStepReferences(item, idMap) }
        else -> value
    }
}

private fun rewriteStepReferenceString(value: String, idMap: Map<String, String>): String {
    var updated = value
    idMap.forEach { (oldId, newId) ->
        updated = updated.replace("{{$oldId.", "{{$newId.")
            .replace("{{$oldId}}", "{{$newId}}")
    }
    return updated
}
