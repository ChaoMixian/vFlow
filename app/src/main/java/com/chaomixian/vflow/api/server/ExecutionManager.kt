package com.chaomixian.vflow.api.server

import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.workflow.WorkflowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 执行管理器
 * 管理工作流执行的生命周期
 */
class ExecutionManager(
    private val workflowManager: WorkflowManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executions = ConcurrentHashMap<String, ExecutionRecord>()
    private val executionLogs = ConcurrentHashMap<String, MutableList<LogEntry>>()
    private val mutex = Mutex()

    /**
     * 执行工作流
     */
    fun executeWorkflow(
        workflowId: String,
        inputVariables: Map<String, VObjectDto>? = null,
        async: Boolean = true,
        timeout: Int? = null,
        callback: ((ExecutionResult) -> Unit)? = null
    ): ExecutionDetail {

        val workflow = workflowManager.getWorkflow(workflowId)
            ?: throw IllegalArgumentException("Workflow not found")

        // 注意：通过API执行时，允许执行禁用的工作流（用户手动触发）
        // 只有自动触发器（如定时器、事件）才需要检查isEnabled状态

        val executionId = "exec-${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        // 创建执行记录
        val record = ExecutionRecord(
            executionId = executionId,
            workflowId = workflowId,
            workflowName = workflow.name,
            status = ExecutionStatus.RUNNING,
            startedAt = now,
            completedAt = null,
            duration = null,
            triggeredBy = "manual",
            error = null
        )
        executions[executionId] = record
        executionLogs[executionId] = mutableListOf()

        if (async) {
            // 异步执行
            scope.launch {
                try {
                    executeWorkflowInternal(workflow, executionId, inputVariables, timeout, callback)
                } catch (e: Exception) {
                    handleExecutionError(executionId, e)
                }
            }
        } else {
            // 同步执行
            try {
                runBlocking {
                    executeWorkflowInternal(workflow, executionId, inputVariables, timeout, callback)
                }
            } catch (e: Exception) {
                handleExecutionError(executionId, e)
            }
        }

        return getExecutionDetail(executionId)!!
    }

    /**
     * 内部执行逻辑
     */
    private suspend fun executeWorkflowInternal(
        workflow: com.chaomixian.vflow.core.workflow.model.Workflow,
        executionId: String,
        inputVariables: Map<String, VObjectDto>?,
        timeout: Int?,
        callback: ((ExecutionResult) -> Unit)?
    ) {
        val context = workflowManager.context

        // 记录开始日志
        addLog(
            executionId,
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = LogLevel.INFO,
                stepIndex = null,
                moduleId = null,
                message = "Workflow execution started"
            )
        )

        try {
            // 执行工作流
            WorkflowExecutor.execute(workflow, context, null)

            // 简化处理 - 假设执行成功
            val now = System.currentTimeMillis()
            val record = executions[executionId]!!
            val completedRecord = record.copy(
                status = ExecutionStatus.COMPLETED,
                completedAt = now,
                duration = now - record.startedAt,
                error = null
            )
            executions[executionId] = completedRecord

            // 记录完成日志
            addLog(
                executionId,
                LogEntry(
                    timestamp = now,
                    level = LogLevel.INFO,
                    stepIndex = null,
                    moduleId = null,
                    message = "Workflow completed successfully"
                )
            )

            // TODO: 调用回调
            callback?.invoke(ExecutionResult.Success(emptyMap()))

        } catch (e: Exception) {
            handleExecutionError(executionId, e)
        }
    }

    /**
     * 处理执行错误
     */
    private fun handleExecutionError(executionId: String, error: Throwable) {
        val now = System.currentTimeMillis()
        val record = executions[executionId]!!

        val errorMessage = "${error.javaClass.simpleName}: ${error.message ?: "Unknown error"}"

        executions[executionId] = record.copy(
            status = ExecutionStatus.FAILED,
            completedAt = now,
            duration = now - record.startedAt,
            error = errorMessage
        )

        addLog(
            executionId,
            LogEntry(
                timestamp = now,
                level = LogLevel.ERROR,
                stepIndex = null,
                moduleId = null,
                message = "Execution failed: $errorMessage"
            )
        )
    }

    /**
     * 停止执行
     */
    fun stopExecution(executionId: String): Boolean {
        val record = executions[executionId] ?: return false

        if (record.status != ExecutionStatus.RUNNING) {
            return false
        }

        val now = System.currentTimeMillis()
        executions[executionId] = record.copy(
            status = ExecutionStatus.CANCELLED,
            completedAt = now,
            duration = now - record.startedAt
        )

        addLog(
            executionId,
            LogEntry(
                timestamp = now,
                level = LogLevel.INFO,
                stepIndex = null,
                moduleId = null,
                message = "Execution cancelled by user"
            )
        )

        return true
    }

    /**
     * 获取执行详情
     */
    fun getExecutionDetail(executionId: String): ExecutionDetail? {
        val record = executions[executionId] ?: return null

        val errorResponse = if (record.error != null) {
            ErrorResponse(
                title = "Execution Error",
                message = record.error,
                stepIndex = null
            )
        } else null

        return ExecutionDetail(
            executionId = record.executionId,
            workflowId = record.workflowId,
            workflowName = record.workflowName,
            status = record.status,
            currentStepIndex = 0, // TODO: 跟踪当前步骤
            totalSteps = 0, // TODO: 获取工作流步骤数
            currentStep = null,
            startedAt = record.startedAt,
            completedAt = record.completedAt,
            duration = record.duration,
            outputs = emptyMap(), // TODO: 收集输出
            error = errorResponse,
            variables = emptyMap() // TODO: 收集变量
        )
    }

    /**
     * 获取执行日志
     */
    fun getExecutionLogs(
        executionId: String,
        level: LogLevel? = null,
        stepIndex: Int? = null,
        limit: Int = 100,
        offset: Int = 0
    ): ExecutionLogsResponse {
        val allLogs = executionLogs[executionId] ?: emptyList()

        var filteredLogs = allLogs

        // 应用过滤器
        if (level != null) {
            filteredLogs = filteredLogs.filter { it.level == level }
        }
        if (stepIndex != null) {
            filteredLogs = filteredLogs.filter { it.stepIndex == stepIndex }
        }

        val total = filteredLogs.size
        val paginatedLogs = filteredLogs
            .drop(offset)
            .take(limit)

        return ExecutionLogsResponse(
            logs = paginatedLogs,
            total = total,
            limit = limit,
            offset = offset
        )
    }

    /**
     * 列出执行记录
     */
    fun listExecutions(
        workflowId: String? = null,
        status: ExecutionStatus? = null,
        limit: Int = 20,
        offset: Int = 0
    ): ExecutionListResponse {
        var filtered = executions.values.toList()

        // 应用过滤器
        if (workflowId != null) {
            filtered = filtered.filter { it.workflowId == workflowId }
        }
        if (status != null) {
            filtered = filtered.filter { it.status == status }
        }

        // 按时间倒序排序
        filtered = filtered.sortedByDescending { it.startedAt }

        val total = filtered.size
        val paginated = filtered.drop(offset).take(limit)

        return ExecutionListResponse(
            executions = paginated,
            total = total,
            limit = limit,
            offset = offset
        )
    }

    /**
     * 删除执行历史
     */
    fun deleteExecutionHistory(
        executionId: String? = null,
        workflowId: String? = null,
        olderThan: Long? = null
    ): Int {
        var toDelete = setOf<String>()

        when {
            executionId != null -> toDelete = setOf(executionId)
            workflowId != null -> {
                toDelete = executions
                    .filter { it.value.workflowId == workflowId }
                    .keys
            }
            olderThan != null -> {
                toDelete = executions
                    .filter { it.value.startedAt < olderThan }
                    .keys
            }
        }

        toDelete.forEach {
            executions.remove(it)
            executionLogs.remove(it)
        }

        return toDelete.size
    }

    /**
     * 添加日志
     */
    private fun addLog(executionId: String, log: LogEntry) {
        executionLogs.getOrPut(executionId) { mutableListOf() }.add(log)
    }

    /**
     * 清理旧的执行记录
     */
    suspend fun cleanupOldExecutions(maxAge: Long = 86400000L * 7) { // 7天
        val now = System.currentTimeMillis()
        val oldExecutions = executions.filter { now - it.value.startedAt > maxAge }.keys

        mutex.withLock {
            oldExecutions.forEach {
                executions.remove(it)
                executionLogs.remove(it)
            }
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): ExecutionStats {
        val allExecutions = executions.values
        val now = System.currentTimeMillis()
        val todayStart = now - (now % 86400000L)

        return ExecutionStats(
            totalExecutions = allExecutions.size,
            runningExecutions = allExecutions.count { it.status == ExecutionStatus.RUNNING },
            todayExecutions = allExecutions.count { it.startedAt >= todayStart },
            successfulExecutions = allExecutions.count { it.status == ExecutionStatus.COMPLETED },
            failedExecutions = allExecutions.count { it.status == ExecutionStatus.FAILED }
        )
    }
}

/**
 * 执行统计
 */
data class ExecutionStats(
    val totalExecutions: Int,
    val runningExecutions: Int,
    val todayExecutions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int
)
