package com.chaomixian.vflow.api.model

data class VObjectDto(
    val type: String,
    val value: Any?
) {
    companion object {
        const val TYPE_NULL = "null"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_NUMBER = "number"
        const val TYPE_STRING = "string"
        const val TYPE_LIST = "list"
        const val TYPE_DICTIONARY = "dictionary"
        const val TYPE_IMAGE = "image"
        const val TYPE_COORDINATE = "coordinate"
        const val TYPE_SCREEN_ELEMENT = "screen_element"
        const val TYPE_NOTIFICATION = "notification"
        const val TYPE_UI_COMPONENT = "ui_component"
        const val TYPE_TIME = "time"
    }
}

data class ActionStepDto(
    val id: String,
    val moduleId: String,
    val indentationLevel: Int,
    val parameters: Map<String, VObjectDto>
)

data class SimpleActionStepDto(
    val id: String,
    val moduleId: String,
    val indentationLevel: Int? = 0,
    val parameters: Map<String, Any?>? = emptyMap()
) {
    fun toActionStep(): com.chaomixian.vflow.core.workflow.model.ActionStep {
        return com.chaomixian.vflow.core.workflow.model.ActionStep(
            id = id,
            moduleId = moduleId,
            indentationLevel = indentationLevel ?: 0,
            parameters = parameters ?: emptyMap()
        )
    }
}

data class SimpleCreateWorkflowRequest(
    val name: String,
    val description: String? = "",
    val folderId: String? = null,
    val triggers: List<SimpleActionStepDto>? = emptyList(),
    val steps: List<SimpleActionStepDto>? = emptyList(),
    val triggerConfig: Map<String, Any?>? = null,
    val triggerConfigs: List<Map<String, Any?>>? = null,
    val isEnabled: Boolean? = false,
    val tags: List<String>? = emptyList(),
    val maxExecutionTime: Int? = null
)

data class WorkflowSummary(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val isFavorite: Boolean,
    val folderId: String?,
    val order: Int,
    val stepCount: Int,
    val triggerCount: Int,
    val modifiedAt: Long,
    val tags: List<String>,
    val version: String
)

data class WorkflowMetadata(
    val author: String,
    val homepage: String,
    val vFlowLevel: Int,
    val tags: List<String>,
    val description: String
)

data class WorkflowDetail(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val isFavorite: Boolean,
    val folderId: String?,
    val order: Int,
    val triggers: List<ActionStepDto>,
    val steps: List<ActionStepDto>,
    val modifiedAt: Long,
    val version: String,
    val maxExecutionTime: Int?,
    val metadata: WorkflowMetadata
)

data class CreateWorkflowRequest(
    val name: String,
    val description: String = "",
    val folderId: String? = null,
    val triggers: List<ActionStepDto> = emptyList(),
    val steps: List<ActionStepDto> = emptyList(),
    val triggerConfig: Map<String, Any?>? = null,
    val triggerConfigs: List<Map<String, Any?>>? = null,
    val isEnabled: Boolean = false,
    val tags: List<String> = emptyList(),
    val maxExecutionTime: Int? = null
)

data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val isEnabled: Boolean? = null,
    val isFavorite: Boolean? = null,
    val folderId: String? = null,
    val order: Int? = null,
    val triggers: List<ActionStepDto>? = null,
    val steps: List<ActionStepDto>? = null,
    val maxExecutionTime: Int? = null,
    val tags: List<String>? = null
)

data class DuplicateWorkflowRequest(
    val newName: String? = null,
    val targetFolderId: String? = null
)

data class BatchWorkflowRequest(
    val action: BatchAction,
    val workflowIds: List<String>,
    val targetFolderId: String? = null
)

enum class BatchAction {
    DELETE,
    ENABLE,
    DISABLE,
    MOVE
}

data class BatchOperationResponse(
    val succeeded: List<String>,
    val failed: List<String>,
    val skipped: List<String>
)
