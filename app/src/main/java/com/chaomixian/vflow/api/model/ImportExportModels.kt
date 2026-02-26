package com.chaomixian.vflow.api.model

/**
 * 导出工作流响应
 */
data class ExportWorkflowResponse(
    val workflow: WorkflowDetail,
    val exportedAt: Long,
    val format: String
)

/**
 * 导入工作流响应
 */
data class ImportWorkflowResponse(
    val imported: List<ImportedWorkflow>,
    val skipped: List<SkippedWorkflow>,
    val errors: List<ImportError>,
    val total: Int
)

/**
 * 导入的工作流
 */
data class ImportedWorkflow(
    val workflowId: String,
    val name: String
)

/**
 * 跳过的工作流
 */
data class SkippedWorkflow(
    val name: String,
    val reason: String
)

/**
 * 导入错误
 */
data class ImportError(
    val filename: String,
    val error: String
)

/**
 * 批量导入响应
 */
data class BatchImportResponse(
    val imported: List<ImportedWorkflow>,
    val skipped: List<SkippedWorkflow>,
    val errors: List<ImportError>,
    val total: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
)
