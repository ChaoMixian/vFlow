package com.chaomixian.vflow.api.model

import com.google.gson.annotations.SerializedName

/**
 * 执行状态
 */
enum class ExecutionStatus {
    @SerializedName("running")
    RUNNING,
    @SerializedName("completed")
    COMPLETED,
    @SerializedName("failed")
    FAILED,
    @SerializedName("cancelled")
    CANCELLED,
    @SerializedName("timeout")
    TIMEOUT
}

/**
 * 执行工作流请求
 */
data class ExecuteWorkflowRequest(
    @SerializedName("input_variables")
    val inputVariables: Map<String, VObjectDto>? = null,
    val async: Boolean = true,
    val timeout: Int? = null
)

/**
 * 执行响应
 */
data class ExecutionResponse(
    @SerializedName("execution_id")
    val executionId: String,
    @SerializedName("workflow_id")
    val workflowId: String,
    val status: ExecutionStatus,
    @SerializedName("started_at")
    val startedAt: Long
)

/**
 * 当前步骤信息
 */
data class CurrentStepInfo(
    val id: String,
    @SerializedName("module_id")
    val moduleId: String,
    val name: String
)

/**
 * 执行详情
 */
data class ExecutionDetail(
    @SerializedName("execution_id")
    val executionId: String,
    @SerializedName("workflow_id")
    val workflowId: String,
    @SerializedName("workflow_name")
    val workflowName: String,
    val status: ExecutionStatus,
    @SerializedName("current_step_index")
    val currentStepIndex: Int,
    @SerializedName("total_steps")
    val totalSteps: Int,
    @SerializedName("current_step")
    val currentStep: CurrentStepInfo?,
    @SerializedName("started_at")
    val startedAt: Long,
    @SerializedName("completed_at")
    val completedAt: Long?,
    val duration: Long?,
    val outputs: Map<String, Map<String, VObjectDto>>,
    val error: ErrorResponse?,
    val variables: Map<String, VObjectDto>
)

/**
 * 错误响应
 */
data class ErrorResponse(
    val title: String,
    val message: String,
    @SerializedName("step_index")
    val stepIndex: Int?
)

/**
 * 日志级别
 */
enum class LogLevel {
    INFO,
    WARNING,
    ERROR
}

/**
 * 日志条目
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    @SerializedName("step_index")
    val stepIndex: Int?,
    @SerializedName("module_id")
    val moduleId: String?,
    val message: String,
    val details: Map<String, Any?>? = null
)

/**
 * 执行日志响应
 */
data class ExecutionLogsResponse(
    val logs: List<LogEntry>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

/**
 * 执行记录摘要
 */
data class ExecutionRecord(
    @SerializedName("execution_id")
    val executionId: String,
    @SerializedName("workflow_id")
    val workflowId: String,
    @SerializedName("workflow_name")
    val workflowName: String,
    val status: ExecutionStatus,
    @SerializedName("started_at")
    val startedAt: Long,
    @SerializedName("completed_at")
    val completedAt: Long?,
    val duration: Long?,
    @SerializedName("triggered_by")
    val triggeredBy: String,
    val error: String?
)

/**
 * 执行列表响应
 */
data class ExecutionListResponse(
    val executions: List<ExecutionRecord>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
