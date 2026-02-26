package com.chaomixian.vflow.api.model

/**
 * 设备信息
 */
data class DeviceInfo(
    val brand: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int
)

/**
 * 权限状态
 */
data class PermissionStatus(
    val name: String,
    val granted: Boolean,
    val description: String
)

/**
 * 系统能力
 */
data class Capabilities(
    val hasRoot: Boolean,
    val hasShizuku: Boolean,
    val hasCoreService: Boolean,
    val supportedFeatures: List<String>
)

/**
 * 服务器信息
 */
data class ServerInfo(
    val version: String,
    val startTime: Long,
    val uptime: Long
)

/**
 * 系统信息响应
 */
data class SystemInfoResponse(
    val device: DeviceInfo,
    val permissions: List<PermissionStatus>,
    val capabilities: Capabilities,
    val server: ServerInfo
)

/**
 * 存储使用情况
 */
data class StorageUsage(
    val usedBytes: Long,
    val totalBytes: Long,
    val used: String,
    val total: String,
    val percentage: Int
)

/**
 * 热门工作流
 */
data class TopWorkflow(
    val workflowId: String,
    val name: String,
    val executionCount: Int
)

/**
 * 系统统计响应
 */
data class SystemStatsResponse(
    val workflowCount: Int,
    val enabledWorkflowCount: Int,
    val folderCount: Int,
    val totalExecutions: Long,
    val todayExecutions: Long,
    val successfulExecutions: Long,
    val failedExecutions: Long,
    val successRate: Double,
    val averageExecutionTime: Long,
    val storageUsage: StorageUsage,
    val memoryUsage: StorageUsage,
    val topWorkflows: List<TopWorkflow>
)

/**
 * 健康检查响应
 */
data class HealthCheckResponse(
    val status: String,
    val version: String,
    val timestamp: Long,
    val uptime: Long
)
