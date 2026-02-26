package com.chaomixian.vflow.api.model

/**
 * VObject DTO - 用于API传输的VObject表示
 */
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

/**
 * 工作流步骤 DTO
 */
data class ActionStepDto(
    val id: String,
    val moduleId: String,
    val indentationLevel: Int,
    val parameters: Map<String, VObjectDto>
)

/**
 * 简单工作流步骤DTO - 接受普通JSON对象
 */
data class SimpleActionStepDto(
    val id: String,
    val moduleId: String,
    val indentationLevel: Int? = 0,
    val parameters: Map<String, Any?>? = emptyMap()
) {
    /**
     * 转换为ActionStep
     */
    fun toActionStep(): com.chaomixian.vflow.core.workflow.model.ActionStep {
        return com.chaomixian.vflow.core.workflow.model.ActionStep(
            id = id,
            moduleId = moduleId,
            indentationLevel = indentationLevel ?: 0,
            parameters = parameters ?: emptyMap()
        )
    }
}

/**
 * 简单创建工作流请求 - 接受普通JSON对象
 */
data class SimpleCreateWorkflowRequest(
    val name: String,
    val description: String? = "",
    val folderId: String? = null,
    val steps: List<SimpleActionStepDto>? = emptyList(),
    val isEnabled: Boolean? = false,
    val tags: List<String>? = emptyList(),
    val maxExecutionTime: Int? = null,
    val triggerConfig: Map<String, Any?>? = null
)

/**
 * 工作流摘要
 */
data class WorkflowSummary(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val isFavorite: Boolean,
    val folderId: String?,
    val order: Int,
    val stepCount: Int,
    val modifiedAt: Long,
    val tags: List<String>,
    val version: String,
    val triggerConfig: Map<String, Any?>?
)

/**
 * 工作流元数据
 */
data class WorkflowMetadata(
    val author: String,
    val homepage: String,
    val vFlowLevel: Int,
    val tags: List<String>,
    val description: String
)

/**
 * 工作流详情
 */
data class WorkflowDetail(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val isFavorite: Boolean,
    val folderId: String?,
    val order: Int,
    val steps: List<ActionStepDto>,
    val triggerConfig: Map<String, Any?>?,
    val modifiedAt: Long,
    val version: String,
    val maxExecutionTime: Int?,
    val metadata: WorkflowMetadata
)

/**
 * 创建工作流请求
 */
data class CreateWorkflowRequest(
    val name: String,
    val description: String = "",
    val folderId: String? = null,
    val steps: List<ActionStepDto> = emptyList(),
    val isEnabled: Boolean = false,
    val tags: List<String> = emptyList(),
    val maxExecutionTime: Int? = null,
    val triggerConfig: Map<String, Any?>? = null
)

/**
 * 更新工作流请求
 */
data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val isEnabled: Boolean? = null,
    val isFavorite: Boolean? = null,
    val folderId: String? = null,
    val order: Int? = null,
    val steps: List<ActionStepDto>? = null,
    val triggerConfig: Map<String, Any?>? = null,
    val maxExecutionTime: Int? = null,
    val tags: List<String>? = null
)

/**
 * 复制工作流请求
 */
data class DuplicateWorkflowRequest(
    val newName: String? = null,
    val targetFolderId: String? = null
)

/**
 * 批量操作请求
 */
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

/**
 * 批量操作响应
 */
data class BatchOperationResponse(
    val succeeded: List<String>,
    val failed: List<String>,
    val skipped: List<String>
)
