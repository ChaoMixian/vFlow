package com.chaomixian.vflow.api.model

/**
 * API统一响应格式
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "success",
    val data: T? = null,
    val details: Map<String, Any?>? = null
) {
    companion object {
        const val CODE_SUCCESS = 0

        // Workflow errors (1001-1999)
        const val CODE_WORKFLOW_NOT_FOUND = 1001
        const val CODE_INVALID_WORKFLOW_DATA = 1002
        const val CODE_WORKFLOW_EXECUTION_FAILED = 1003
        const val CODE_WORKFLOW_ALREADY_EXISTS = 1004

        // Module errors (2001-2999)
        const val CODE_MODULE_NOT_FOUND = 2001
        const val CODE_INVALID_MODULE_PARAMETERS = 2002

        // Folder errors (3001-3999)
        const val CODE_FOLDER_NOT_FOUND = 3001
        const val CODE_FOLDER_NOT_EMPTY = 3002

        // Variable errors (4001-4999)
        const val CODE_VARIABLE_NOT_FOUND = 4001
        const val CODE_INVALID_VARIABLE_TYPE = 4002

        // Execution errors (5001-5999)
        const val CODE_EXECUTION_NOT_FOUND = 5001
        const val CODE_EXECUTION_ALREADY_STOPPED = 5002

        // Auth errors (6001-6999)
        const val CODE_INVALID_AUTHENTICATION = 6001
        const val CODE_TOKEN_EXPIRED = 6002
        const val CODE_INSUFFICIENT_PERMISSIONS = 6003

        // Rate limiting (7001-7999)
        const val CODE_RATE_LIMIT_EXCEEDED = 7001

        // File errors (8001-8999)
        const val CODE_INVALID_FILE_FORMAT = 8001
        const val CODE_FILE_SIZE_EXCEEDED = 8002

        // Server errors (9001-9999)
        const val CODE_INTERNAL_SERVER_ERROR = 9001
        const val CODE_SERVICE_UNAVAILABLE = 9002

        fun <T> success(data: T? = null, message: String = "success"): ApiResponse<T> {
            return ApiResponse(CODE_SUCCESS, message, data)
        }

        fun <T> error(
            code: Int,
            message: String,
            details: Map<String, Any?>? = null
        ): ApiResponse<T> {
            return ApiResponse(code, message, null, details)
        }
    }

    val isSuccess: Boolean
        get() = code == CODE_SUCCESS
}

/**
 * 分页响应
 */
data class PagedResponse<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int
) {
    val hasNextPage: Boolean
        get() = offset + limit < total

    val hasPreviousPage: Boolean
        get() = offset > 0

    val totalPages: Int
        get() = (total + limit - 1) / limit

    val currentPage: Int
        get() = offset / limit + 1
}
