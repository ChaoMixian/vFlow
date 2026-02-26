package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 文件夹Handler
 */
class FolderHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        val folderId = if (uri.matches(Regex("/api/v1/folders/[^/]+"))) {
            getPathParameter(uri, "/api/v1/folders/")
        } else null

        return when {
            // 列出文件夹
            uri == "/api/v1/folders" && method == NanoHTTPD.Method.GET -> {
                handleListFolders(session, tokenInfo)
            }
            // 创建文件夹
            uri == "/api/v1/folders" && method == NanoHTTPD.Method.POST -> {
                handleCreateFolder(session, tokenInfo)
            }
            // 获取文件夹详情
            folderId != null && method == NanoHTTPD.Method.GET -> {
                handleGetFolder(folderId, tokenInfo)
            }
            // 更新文件夹
            folderId != null && method == NanoHTTPD.Method.PUT -> {
                handleUpdateFolder(folderId, session, tokenInfo)
            }
            // 删除文件夹
            folderId != null && method == NanoHTTPD.Method.DELETE -> {
                handleDeleteFolder(folderId, session, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    private fun handleListFolders(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val params = parseQueryParams(session)
        val parentId = params["parentId"]

        // 从FolderManager获取文件夹列表
        val folderManager = deps.folderManager
        val folders = if (folderManager != null) {
            var allFolders = folderManager.getAllFolders()

            // 按父文件夹筛选
            if (parentId != null) {
                if (parentId == "null" || parentId.isEmpty()) {
                    // 返回顶级文件夹（没有父文件夹的）
                    allFolders = allFolders.filter { it.parentId == null }
                } else {
                    // 返回指定父文件夹的子文件夹
                    allFolders = allFolders.filter { it.parentId == parentId }
                }
            }

            allFolders.map { folder ->
                val workflowCount = deps.workflowManager.getAllWorkflows().count { it.folderId == folder.id }
                val subfolderCount = folderManager.getAllFolders().count { it.parentId == folder.id }

                Folder(
                    id = folder.id,
                    name = folder.name,
                    parentId = folder.parentId,
                    order = folder.order,
                    workflowCount = workflowCount,
                    subfolderCount = subfolderCount,
                    createdAt = folder.createdAt,
                    modifiedAt = folder.modifiedAt
                )
            }
        } else {
            emptyList()
        }

        return successResponse(mapOf("folders" to folders, "total" to folders.size))
    }

    private fun handleGetFolder(folderId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val folderManager = deps.folderManager
        if (folderManager == null) {
            return errorResponse(3001, "Folder not found")
        }

        val folder = folderManager.getFolder(folderId)
        if (folder == null) {
            return errorResponse(3001, "Folder not found")
        }

        // 获取文件夹中的工作流
        val workflows = deps.workflowManager.getAllWorkflows()
            .filter { it.folderId == folderId }
            .map { wf ->
                mapOf(
                    "id" to wf.id,
                    "name" to wf.name,
                    "isEnabled" to wf.isEnabled,
                    "order" to wf.order
                )
            }

        // 获取子文件夹（parentId == 当前文件夹ID）
        val subfolders = folderManager.getAllFolders()
            .filter { it.parentId == folderId }
            .map { sf ->
                mapOf(
                    "id" to sf.id,
                    "name" to sf.name,
                    "order" to sf.order
                )
            }

        val workflowCount = workflows.size
        val subfolderCount = subfolders.size

        return successResponse(mapOf(
            "id" to folder.id,
            "name" to folder.name,
            "parentId" to folder.parentId,
            "order" to folder.order,
            "workflowCount" to workflowCount,
            "subfolderCount" to subfolderCount,
            "workflows" to workflows,
            "subfolders" to subfolders,
            "createdAt" to folder.createdAt,
            "modifiedAt" to folder.modifiedAt
        ))
    }

    private fun handleCreateFolder(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val request = parseRequestBody(session, CreateFolderRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        if (request.name.isBlank()) {
            return errorResponse(400, "Folder name is required")
        }

        val folderManager = deps.folderManager
        if (folderManager == null) {
            return errorResponse(3001, "Folder manager not available")
        }

        // 创建新文件夹
        val newFolder = com.chaomixian.vflow.core.workflow.model.WorkflowFolder(
            name = request.name,
            parentId = request.parentId,
            order = request.order
        )
        folderManager.saveFolder(newFolder)

        return successResponse(mapOf(
            "id" to newFolder.id,
            "name" to newFolder.name,
            "createdAt" to newFolder.createdAt
        ))
    }

    private fun handleUpdateFolder(folderId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val request = parseRequestBody(session, UpdateFolderRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        val folderManager = deps.folderManager
        if (folderManager == null) {
            return errorResponse(3001, "Folder not found")
        }

        val existingFolder = folderManager.getFolder(folderId)
        if (existingFolder == null) {
            return errorResponse(3001, "Folder not found")
        }

        // 更新文件夹名称
        if (!request.name.isNullOrBlank()) {
            existingFolder.name = request.name
        }

        // 更新父文件夹
        existingFolder.parentId = request.parentId

        // 更新排序
        if (request.order != null) {
            existingFolder.order = request.order
        }

        existingFolder.modifiedAt = System.currentTimeMillis()
        folderManager.saveFolder(existingFolder)

        return successResponse(mapOf(
            "id" to folderId,
            "updatedAt" to existingFolder.modifiedAt
        ))
    }

    private fun handleDeleteFolder(folderId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val params = parseQueryParams(session)
        val deleteWorkflows = params["deleteWorkflows"]?.toBooleanStrictOrNull() ?: false
        val moveWorkflowsTo = params["moveWorkflowsTo"]

        val folderManager = deps.folderManager
        if (folderManager == null) {
            return errorResponse(3001, "Folder not found")
        }

        val existingFolder = folderManager.getFolder(folderId)
        if (existingFolder == null) {
            return errorResponse(3001, "Folder not found")
        }

        // 检查文件夹中是否有工作流
        val workflowsInFolder = deps.workflowManager.getAllWorkflows().filter { it.folderId == folderId }
        val workflowsCount = workflowsInFolder.size

        if (workflowsCount > 0 && !deleteWorkflows && moveWorkflowsTo.isNullOrBlank()) {
            return errorResponse(3002, "Folder not empty. Use deleteWorkflows=true or moveWorkflowsTo parameter")
        }

        // 处理工作流
        var workflowsDeleted = 0
        var workflowsMoved = 0

        if (workflowsCount > 0) {
            if (deleteWorkflows) {
                // 删除工作流
                workflowsInFolder.forEach { wf ->
                    deps.workflowManager.deleteWorkflow(wf.id)
                }
                workflowsDeleted = workflowsCount
            } else if (!moveWorkflowsTo.isNullOrBlank()) {
                // 移动工作流到其他文件夹
                workflowsInFolder.forEach { wf ->
                    val movedWorkflow = wf.copy(folderId = moveWorkflowsTo)
                    deps.workflowManager.saveWorkflow(movedWorkflow)
                }
                workflowsMoved = workflowsCount
            }
        }

        // 删除文件夹
        folderManager.deleteFolder(folderId)

        return successResponse(mapOf(
            "deleted" to true,
            "deletedAt" to System.currentTimeMillis(),
            "workflowsDeleted" to workflowsDeleted,
            "workflowsMoved" to workflowsMoved
        ))
    }
}
