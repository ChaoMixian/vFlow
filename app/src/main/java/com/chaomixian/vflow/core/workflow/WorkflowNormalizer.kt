package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep

object WorkflowNormalizer {
    private const val TRIGGER_MODULE_PREFIX = "vflow.trigger."
    const val MANUAL_TRIGGER_MODULE_ID = "vflow.trigger.manual"

    data class NormalizedWorkflowContent(
        val triggers: List<ActionStep>,
        val steps: List<ActionStep>
    )

    fun normalize(
        triggers: List<ActionStep>?,
        steps: List<ActionStep>?,
        legacyTriggerConfigs: List<Map<String, Any?>> = emptyList(),
        ensureTrigger: Boolean = true
    ): NormalizedWorkflowContent {
        val explicitTriggers = triggers.orEmpty().filter(::isTriggerStep)
        val (leadingTriggerSteps, remainingSteps) = splitLeadingTriggerSteps(steps.orEmpty())
        val legacyTriggers = if (explicitTriggers.isEmpty() && leadingTriggerSteps.isEmpty()) {
            legacyTriggerConfigs.mapNotNull(::legacyTriggerConfigToStep)
        } else {
            emptyList()
        }

        val normalizedTriggers = when {
            explicitTriggers.isNotEmpty() -> explicitTriggers
            leadingTriggerSteps.isNotEmpty() -> leadingTriggerSteps
            else -> legacyTriggers
        }
            .distinctBy(ActionStep::id)
            .ifEmpty {
                if (ensureTrigger) listOf(createManualTrigger()) else emptyList()
            }

        return NormalizedWorkflowContent(
            triggers = normalizedTriggers,
            steps = remainingSteps
        )
    }

    fun createManualTrigger(): ActionStep {
        return ActionStep(
            moduleId = MANUAL_TRIGGER_MODULE_ID,
            parameters = emptyMap()
        )
    }

    fun isTriggerStep(step: ActionStep): Boolean = isTriggerModuleId(step.moduleId)

    private fun isTriggerModuleId(moduleId: String): Boolean {
        return moduleId.startsWith(TRIGGER_MODULE_PREFIX)
    }

    private fun splitLeadingTriggerSteps(steps: List<ActionStep>): Pair<List<ActionStep>, List<ActionStep>> {
        val firstActionIndex = steps.indexOfFirst { !isTriggerStep(it) }
        if (firstActionIndex == -1) {
            return steps to emptyList()
        }
        return steps.take(firstActionIndex) to steps.drop(firstActionIndex)
    }

    private fun legacyTriggerConfigToStep(config: Map<String, Any?>): ActionStep? {
        val moduleId = config["type"] as? String ?: return null
        if (!isTriggerModuleId(moduleId)) return null

        return ActionStep(
            moduleId = moduleId,
            parameters = config.filterKeys { it != "type" }
        )
    }
}
