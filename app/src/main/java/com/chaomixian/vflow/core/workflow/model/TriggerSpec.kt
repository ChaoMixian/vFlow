package com.chaomixian.vflow.core.workflow.model

data class TriggerSpec(
    val workflow: Workflow,
    val step: ActionStep
) {
    val workflowId: String = workflow.id
    val stepId: String = step.id
    val triggerId: String = "$workflowId:$stepId"
    val workflowName: String = workflow.name
    val type: String = step.moduleId
    val parameters: Map<String, Any?> = step.parameters
}
