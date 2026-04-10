package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow

data class WorkflowEnumMigrationPreview(
    val originalWorkflow: Workflow,
    val migratedWorkflow: Workflow,
    val affectedStepCount: Int,
    val affectedFieldCount: Int
)

data class WorkflowBatchEnumMigrationPreview(
    val previews: List<WorkflowEnumMigrationPreview>
) {
    val affectedWorkflowCount: Int
        get() = previews.size

    val affectedStepCount: Int
        get() = previews.sumOf { it.affectedStepCount }

    val affectedFieldCount: Int
        get() = previews.sumOf { it.affectedFieldCount }

    val migratedWorkflows: List<Workflow>
        get() = previews.map { it.migratedWorkflow }
}

object WorkflowEnumMigration {
    fun scan(workflow: Workflow): WorkflowEnumMigrationPreview? {
        val editableSteps = workflow.allSteps
        var affectedStepCount = 0
        var affectedFieldCount = 0

        val migratedTriggers = workflow.triggers.map { step ->
            val migration = migrateLegacyEnumValues(step, editableSteps)
            if (migration != null) {
                affectedStepCount += 1
                affectedFieldCount += migration.second
                migration.first
            } else {
                step
            }
        }

        val migratedSteps = workflow.steps.map { step ->
            val migration = migrateLegacyEnumValues(step, editableSteps)
            if (migration != null) {
                affectedStepCount += 1
                affectedFieldCount += migration.second
                migration.first
            } else {
                step
            }
        }

        if (affectedFieldCount == 0) return null

        return WorkflowEnumMigrationPreview(
            originalWorkflow = workflow,
            migratedWorkflow = workflow.copy(
                triggers = migratedTriggers,
                steps = migratedSteps
            ),
            affectedStepCount = affectedStepCount,
            affectedFieldCount = affectedFieldCount
        )
    }

    fun scan(workflows: List<Workflow>): WorkflowBatchEnumMigrationPreview? {
        val previews = workflows.mapNotNull(::scan)
        if (previews.isEmpty()) return null
        return WorkflowBatchEnumMigrationPreview(previews)
    }

    private fun migrateLegacyEnumValues(
        step: ActionStep,
        allSteps: List<ActionStep>
    ): Pair<ActionStep, Int>? {
        val module = ModuleRegistry.getModule(step.moduleId) ?: return null
        val stepForUi = ActionStep(step.moduleId, step.parameters, step.indentationLevel, step.id)
        val inputDefinitions = (module.getInputs() + module.getDynamicInputs(stepForUi, allSteps))
            .distinctBy { it.id }

        val updatedParameters = step.parameters.toMutableMap()
        var migratedFieldCount = 0

        inputDefinitions.forEach { inputDef ->
            if (inputDef.staticType != ParameterType.ENUM) return@forEach

            val currentValue = updatedParameters[inputDef.id] as? String ?: return@forEach
            if (inputDef.options.contains(currentValue)) return@forEach

            val mappedValue = inputDef.normalizeEnumValueOrNull(currentValue) ?: return@forEach
            if (!inputDef.options.contains(mappedValue)) return@forEach

            updatedParameters[inputDef.id] = mappedValue
            migratedFieldCount += 1
        }

        if (migratedFieldCount == 0) return null
        return step.copy(parameters = updatedParameters) to migratedFieldCount
    }
}
