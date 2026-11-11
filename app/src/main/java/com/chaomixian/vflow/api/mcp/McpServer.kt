package com.chaomixian.vflow.api.mcp

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.chaomixian.vflow.api.auth.AuthManager
import com.chaomixian.vflow.core.recording.UiRecordingStore
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.ui.chat.ChatAgentArtifactStore
import com.chaomixian.vflow.ui.chat.ChatAgentModuleExecutor
import com.chaomixian.vflow.ui.chat.ChatAgentToolDefinition
import com.chaomixian.vflow.ui.chat.ChatAgentToolRegistry
import com.chaomixian.vflow.ui.chat.ChatAgentToolRiskLevel
import com.chaomixian.vflow.ui.chat.ChatAgentToolUsageScope
import com.chaomixian.vflow.ui.chat.ChatToolCall
import com.chaomixian.vflow.ui.chat.ChatToolResultStatus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** MCP 服务器监听所有网卡，允许局域网以及本机端口转发接入。 */
private const val MCP_BIND_ADDRESS = "0.0.0.0"

/** 单张 MCP 图片内容的最大原始字节数，避免一次响应过大。 */
private const val MAX_MCP_IMAGE_BYTES = 6 * 1024 * 1024

/** 工作流导出工具名。 */
private const val WORKFLOW_EXPORT_TOOL_NAME = "vflow_workflow_export"

/** 从 UI 录制生成工作流的工具名。 */
private const val UI_RECORDING_WORKFLOW_TOOL_NAME = "vflow_workflow_from_recording"
private const val WORKFLOW_GROUP_DELAY_HINT =
    "每一组完整操作完成后必须等待至少 2 秒，再进行下一组操作。"

/**
 * vFlow MCP 服务器。
 *
 * 使用 MCP Streamable HTTP 传输：AI 客户端向 `POST /mcp` 发送 JSON-RPC 请求，
 * 服务器同步执行对应的 vFlow 本地模块并直接返回结果。
 *
 * 与旧的远程 API 不同，这里不会创建或排队工作流，而是复用 Chat Agent 的
 * [ChatAgentModuleExecutor]，直接调用 [com.chaomixian.vflow.core.module.ActionModule.execute]，
 * 因此单次工具调用没有工作流调度延迟。
 */
class McpServer(
    port: Int,
    private val authManager: AuthManager,
    context: Context,
) : NanoHTTPD(MCP_BIND_ADDRESS, port) {

    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val toolRegistry = ChatAgentToolRegistry(appContext)
    private val toolExecutor = ChatAgentModuleExecutor(appContext, toolRegistry)
    private val artifactStore = ChatAgentArtifactStore()
    private val workflowExporter = McpWorkflowExporter(appContext)
    private val recordingWorkflowBuilder = UiRecordingWorkflowBuilder(appContext)
    private val executionMutex = Mutex()
    private val operationHistory = mutableListOf<RecordedMcpOperation>()
    private val operationHistoryLock = Any()

    private val tools: List<ChatAgentToolDefinition> = buildToolList()
    private val toolByName: Map<String, ChatAgentToolDefinition> = tools.associateBy { it.name }
    private val serverVersion: String = resolveAppVersion()

    @Volatile
    private var running = false

    /**
     * 是否已经调用过“获取手机状态”工具。
     *
     * 该工具是 MCP 的上下文入口：服务器在它被成功调用之前拒绝执行其他工具，
     * 避免 AI 在不了解当前权限和设备状态的情况下盲目调用。
     */
    @Volatile
    private var phoneStatusChecked = false

    /**
     * 启动 MCP 服务器。
     */
    @Throws(IOException::class)
    fun startServer(): Boolean {
        if (running) {
            return true
        }
        start()
        running = true
        startCleanupTask()
        return true
    }

    /**
     * 停止 MCP 服务器。
     */
    fun stopServer() {
        if (!running) {
            return
        }
        running = false
        scope.cancel()
        stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return withCommonHeaders(
                newFixedLengthResponse(Response.Status.OK, "application/json", "")
            )
        }

        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        return try {
            when {
                uri == "/health" -> handleHealth(session)
                uri == "/mcp" -> handleMcpEndpoint(session)
                uri == "/" -> handleServerInfo()
                else -> jsonResponse(
                    Response.Status.NOT_FOUND,
                    errorBody("Not found. MCP endpoint is /mcp.")
                )
            }
        } catch (e: Exception) {
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                errorBody(e.message ?: "Internal server error")
            )
        }
    }

    private fun handleHealth(session: IHTTPSession): Response {
        val body = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("server", "vFlow MCP")
            addProperty("version", serverVersion)
            addProperty("protocolVersion", LATEST_PROTOCOL_VERSION)
            addProperty("bindAddress", MCP_BIND_ADDRESS)
            addProperty("tools", tools.size)
            addProperty("authRequired", authManager.getTokens().isNotEmpty())
        }
        return jsonResponse(Response.Status.OK, gson.toJson(body))
    }

    private fun handleServerInfo(): Response {
        val body = JsonObject().apply {
            addProperty("name", "vFlow MCP")
            addProperty("version", serverVersion)
            addProperty("endpoint", "/mcp")
            addProperty("transport", "streamable-http")
            addProperty("bindAddress", MCP_BIND_ADDRESS)
            addProperty("tools", tools.size)
            addProperty("authRequired", authManager.getTokens().isNotEmpty())
        }
        return jsonResponse(Response.Status.OK, gson.toJson(body))
    }

    private fun handleMcpEndpoint(session: IHTTPSession): Response {
        return when (session.method) {
            Method.POST -> handleMcpPost(session)
            Method.GET -> jsonResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                errorBody("SSE stream is not used. Send JSON-RPC requests with POST /mcp."),
                allow = "POST, DELETE, OPTIONS"
            )
            Method.DELETE -> {
                // 无状态服务器，会话终止请求直接成功。
                withCommonHeaders(
                    newFixedLengthResponse(Response.Status.OK, "application/json", "")
                )
            }
            else -> jsonResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                errorBody("Unsupported method."),
                allow = "POST, DELETE, OPTIONS"
            )
        }
    }

    private fun handleMcpPost(session: IHTTPSession): Response {
        if (!isAuthorized(session)) {
            return jsonResponse(
                Response.Status.UNAUTHORIZED,
                errorBody("Invalid or missing MCP access token."),
                extraHeaders = mapOf("WWW-Authenticate" to "Bearer")
            )
        }

        val body = try {
            readRequestBody(session)
        } catch (e: Exception) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                gson.toJson(rpcError(null, -32700, "Parse error: ${e.message}"))
            )
        }

        if (body.isBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                gson.toJson(rpcError(null, -32700, "Parse error: empty request body"))
            )
        }

        val payload = try {
            JsonParser.parseString(body)
        } catch (e: Exception) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                gson.toJson(rpcError(null, -32700, "Parse error: ${e.message}"))
            )
        }

        val responseElement = when {
            payload.isJsonArray -> handleBatch(payload.asJsonArray)
            payload.isJsonObject -> handleSingleRequest(payload.asJsonObject)
            else -> rpcError(null, -32600, "Invalid Request")
        }

        if (responseElement == null) {
            // JSON-RPC 通知（例如 notifications/initialized）不需要响应体。
            return withCommonHeaders(
                newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", "")
            )
        }

        return jsonResponse(Response.Status.OK, gson.toJson(responseElement))
    }

    private fun handleBatch(batch: JsonArray): JsonElement? {
        if (batch.size() == 0) {
            return rpcError(null, -32600, "Invalid Request: empty batch")
        }
        val responses = JsonArray()
        batch.forEach { element ->
            if (!element.isJsonObject) {
                responses.add(rpcError(null, -32600, "Invalid Request"))
            } else {
                handleSingleRequest(element.asJsonObject)?.let(responses::add)
            }
        }
        return if (responses.size() == 0) null else responses
    }

    /**
     * 处理单条 JSON-RPC 请求。通知返回 null。
     */
    private fun handleSingleRequest(request: JsonObject): JsonObject? {
        val id = request.get("id")
        val isNotification = id == null || id.isJsonNull
        val jsonrpc = request.get("jsonrpc")?.takeIf { it.isJsonPrimitive }?.asString
            ?: return if (isNotification) null else rpcError(id, -32600, "Invalid Request: missing jsonrpc")
        if (jsonrpc != "2.0") {
            return if (isNotification) null else rpcError(id, -32600, "Invalid Request: jsonrpc must be 2.0")
        }

        val methodElement = request.get("method")?.takeIf { it.isJsonPrimitive }
            ?: return if (isNotification) null else rpcError(id, -32600, "Invalid Request: missing method")
        val method = methodElement.asString
        val params = request.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        if (isNotification) {
            // 客户端完成初始化、取消请求等通知均无需响应。
            return null
        }

        return try {
            val result = dispatch(method, params)
            rpcSuccess(id, result)
        } catch (e: RpcException) {
            rpcError(id, e.code, e.message ?: "Request failed")
        } catch (e: Throwable) {
            rpcError(id, -32603, "Internal error: ${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun dispatch(method: String, params: JsonObject): JsonElement {
        return when (method) {
            "initialize" -> buildInitializeResult(params)
            "ping" -> JsonObject()
            "tools/list" -> buildToolsListResult()
            "tools/call" -> callTool(params)
            "resources/list" -> JsonObject().apply { add("resources", JsonArray()) }
            "resources/templates/list" -> JsonObject().apply { add("resourceTemplates", JsonArray()) }
            "prompts/list" -> JsonObject().apply { add("prompts", JsonArray()) }
            "logging/setLevel" -> JsonObject()
            "completion/complete" -> JsonObject().apply {
                add("completion", JsonObject().apply {
                    add("values", JsonArray())
                    addProperty("total", 0)
                    addProperty("hasMore", false)
                })
            }
            else -> throw RpcException(-32601, "Method not found: $method")
        }
    }

    private fun buildInitializeResult(params: JsonObject): JsonObject {
        val requested = params.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
        val negotiated = requested?.takeIf { it in SUPPORTED_PROTOCOL_VERSIONS }
            ?: LATEST_PROTOCOL_VERSION

        // 新一轮 MCP 会话重新要求先获取手机状态。
        phoneStatusChecked = false
        synchronized(operationHistoryLock) {
            operationHistory.clear()
        }

        return JsonObject().apply {
            addProperty("protocolVersion", negotiated)
            add("capabilities", JsonObject().apply {
                add("tools", JsonObject().apply {
                    addProperty("listChanged", false)
                })
            })
            add("serverInfo", JsonObject().apply {
                addProperty("name", "vFlow MCP")
                addProperty("version", serverVersion)
            })
            addProperty(
                "instructions",
                "vFlow 本地自动化工具服务器。每个工具直接对应一个 vFlow 模块，调用后会立即在 Android 设备上执行，不会创建或排队工作流。" +
                    "重要：在使用任何其他 vFlow MCP 工具之前，必须先调用一次 `$PHONE_STATUS_TOOL_NAME`。" +
                    "该工具默认返回尽可能完整的摘要：已授予/缺失权限、不可用/部分可用工具、设备与屏幕状态，" +
                    "以及当前活动窗口默认全部、不设上限的可能用到控件（含文字/描述、可点击容器、可滚动列表、输入框等）的位置和状态；" +
                    "不返回截图和完整工具清单。只有在确实需要限制长度时，才传 max_ui_nodes>0（最低 20）；" +
                    "需要截图或完整清单时显式传 include_screenshot=true、include_permission_details=true、include_tool_details=true。" +
                    "完成操作后，可调用 `$WORKFLOW_EXPORT_TOOL_NAME` 把本次会话的成功动作导出为 vFlow JSON 工作流；" +
                    "导出工具会自动移除状态查询、查找/OCR 等观察步骤和重复调用，只保留实际动作，并返回清理报告。" +
                    "可先传 dry_run=true 预览清理结果，再保存。" +
                    "也可调用 `$UI_RECORDING_WORKFLOW_TOOL_NAME` 读取 `/sdcard/vFlow/recordings` 中的 UI 录制并生成工作流：" +
                    "recording 参数可传录制 ID、名称或 JSON 文件绝对路径；不传 recording 时返回可用录制列表。" +
                    "该工具会优先按点击/输入/滚动事件还原，并综合使用 GKD 表达式、控件 ID、类型、文本、描述和边界生成选择器。" +
                    WORKFLOW_GROUP_DELAY_HINT +
                    "未完成此步骤时服务器会拒绝其他工具调用。"
            )
        }
    }

    private fun buildToolsListResult(): JsonObject {
        val toolArray = JsonArray()
        tools.forEach { definition ->
            toolArray.add(JsonObject().apply {
                addProperty("name", definition.name)
                addProperty("title", definition.title)
                addProperty("description", buildToolDescription(definition))
                add("inputSchema", parseSchema(definition))
                add("annotations", JsonObject().apply {
                    addProperty("title", definition.title)
                    addProperty("readOnlyHint", definition.riskLevel == ChatAgentToolRiskLevel.READ_ONLY)
                    addProperty("destructiveHint", definition.riskLevel == ChatAgentToolRiskLevel.HIGH)
                    addProperty("idempotentHint", false)
                    addProperty("openWorldHint", true)
                })
            })
        }
        return JsonObject().apply {
            add("tools", toolArray)
        }
    }

    private fun buildToolDescription(definition: ChatAgentToolDefinition): String {
        return buildString {
            append(definition.description)
            if (definition.name == PHONE_STATUS_TOOL_NAME) {
                append("\n\nIMPORTANT: Call this tool once before calling any other vFlow MCP tool. ")
                append("By default it returns a broad summary: permission counts and missing permissions, ")
                append("only partial/unavailable tools, device/screen status, and ALL effective controls from the active window ")
                append("(no artificial upper limit; pass max_ui_nodes > 0, minimum 20, only to cap it), including labeled controls, ")
                append("unlabeled clickable containers, scrollable lists and input fields. Screenshots and full lists are opt-in via ")
                append("include_screenshot, include_permission_details, include_tool_details.")
            }
            append("\n\nvFlow module: ")
            append(definition.moduleId)
            append("\nRisk level: ")
            append(definition.riskLevel.name.lowercase())
            if (definition.permissionNames.isNotEmpty()) {
                append("\nRequired permissions: ")
                append(definition.permissionNames.joinToString(", "))
            }
        }
    }

    private fun parseSchema(definition: ChatAgentToolDefinition): JsonElement {
        return try {
            JsonParser.parseString(definition.inputSchema.toString())
        } catch (e: Exception) {
            JsonObject().apply { addProperty("type", "object") }
        }
    }

    /**
     * 把图片工件转换为 MCP image content。
     *
     * 截图等工具输出的是 [VImage]（文件 URI），MCP 客户端无法直接读取手机上的
     * artifact 句柄，因此这里读取文件并 Base64 编码后随工具结果一起返回。
     * 超过 [MAX_MCP_IMAGE_BYTES] 的图片只保留文本句柄，避免响应体过大。
     */
    private fun buildImageContent(handle: String): JsonObject? {
        val image = artifactStore.resolve(handle) as? VImage ?: return null
        val bytes = runCatching {
            if (image.uriString.startsWith("/")) {
                File(image.uriString).readBytes()
            } else {
                appContext.contentResolver.openInputStream(Uri.parse(image.uriString))?.use { it.readBytes() }
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_MCP_IMAGE_BYTES) return null

        val mimeType = when {
            image.uriString.endsWith(".png", ignoreCase = true) -> "image/png"
            image.uriString.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }

        return JsonObject().apply {
            addProperty("type", "image")
            addProperty("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            addProperty("mimeType", mimeType)
        }
    }

    private fun callTool(params: JsonObject): JsonObject {
        val name = params.get("name")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: throw RpcException(-32602, "Invalid params: missing tool name")

        if (name !in toolByName) {
            throw RpcException(-32602, "Unknown tool: $name")
        }

        if (name != PHONE_STATUS_TOOL_NAME &&
            name != WORKFLOW_EXPORT_TOOL_NAME &&
            name != UI_RECORDING_WORKFLOW_TOOL_NAME &&
            !phoneStatusChecked
        ) {
            return buildStatusRequiredResult(name)
        }

        val arguments = params.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        if (name == WORKFLOW_EXPORT_TOOL_NAME) {
            return exportWorkflow(arguments)
        }

        if (name == UI_RECORDING_WORKFLOW_TOOL_NAME) {
            return workflowFromRecording(arguments)
        }

        val callId = UUID.randomUUID().toString()
        val toolCall = ChatToolCall(
            id = callId,
            name = name,
            argumentsJson = gson.toJson(arguments)
        )

        val outcome = runBlocking(Dispatchers.Default) {
            executionMutex.withLock {
                val batch = toolExecutor.prepareBatch(listOf(toolCall), artifactStore)
                if (batch.missingPermissions.isNotEmpty()) {
                    McpToolOutcome(
                        isError = true,
                        status = ChatToolResultStatus.PERMISSION_REQUIRED,
                        summary = "Permission required",
                        text = buildString {
                            append("缺少权限：")
                            append(batch.missingPermissions.joinToString("、") { it.getLocalizedName(appContext) })
                            append("\n请在 vFlow 中授予相应权限后重试。")
                        },
                        artifacts = emptyList()
                    )
                } else {
                    val result = toolExecutor.executeBatch(batch, artifactStore).firstOrNull()
                    if (result == null) {
                        McpToolOutcome(
                            isError = true,
                            status = ChatToolResultStatus.ERROR,
                            summary = "No result",
                            text = "工具没有返回结果。",
                            artifacts = emptyList()
                        )
                    } else {
                        McpToolOutcome(
                            isError = result.status != ChatToolResultStatus.SUCCESS,
                            status = result.status,
                            summary = result.summary,
                            text = buildString {
                                append(result.outputText.ifBlank { result.summary })
                                if (result.artifacts.isNotEmpty()) {
                                    append("\n\nArtifacts:")
                                    result.artifacts.forEach { artifact ->
                                        append("\n- ")
                                        append(artifact.key)
                                        append(" (")
                                        append(artifact.typeLabel)
                                        append("): ")
                                        append(artifact.handle)
                                    }
                                }
                            },
                            artifacts = result.artifacts.map { artifact ->
                                JsonObject().apply {
                                    addProperty("key", artifact.key)
                                    addProperty("type", artifact.typeLabel)
                                    addProperty("handle", artifact.handle)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (name == PHONE_STATUS_TOOL_NAME && !outcome.isError) {
            phoneStatusChecked = true
        }

        // 记录成功的实际动作，供工作流导出工具清理后生成步骤。
        if (name != PHONE_STATUS_TOOL_NAME &&
            name != WORKFLOW_EXPORT_TOOL_NAME &&
            !outcome.isError &&
            outcome.status == ChatToolResultStatus.SUCCESS
        ) {
            toolByName[name]?.let { definition ->
                synchronized(operationHistoryLock) {
                    operationHistory += RecordedMcpOperation(
                        toolName = name,
                        moduleId = definition.moduleId,
                        arguments = arguments.deepCopy(),
                        summary = outcome.summary,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }

        val content = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", outcome.text)
            })
            // 图片类工件直接作为 MCP image content 返回，AI 不需要再解析 artifact 句柄。
            outcome.artifacts.forEach { artifact ->
                if (artifact.get("type")?.asString == "image") {
                    artifact.get("handle")?.asString?.let { handle ->
                        buildImageContent(handle)?.let(::add)
                    }
                }
            }
        }

        return JsonObject().apply {
            add("content", content)
            addProperty("isError", outcome.isError)
            add("_meta", JsonObject().apply {
                addProperty("status", outcome.status.name)
                addProperty("summary", outcome.summary)
                add("artifacts", JsonArray().apply { outcome.artifacts.forEach(::add) })
            })
        }
    }

    /**
     * 导出本次 MCP 会话中成功执行的动作。
     *
     * 清理逻辑由 [McpWorkflowExporter] 完成；这里只负责解析参数和构造 MCP 响应。
     */
    private fun exportWorkflow(arguments: JsonObject): JsonObject {
        val name = arguments.get("name")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            .orEmpty()
        val description = arguments.get("description")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            .orEmpty()
        val save = argumentBoolean(arguments, "save", true)
        val keepObservations = argumentBoolean(arguments, "keep_observations", false)
        val dryRun = argumentBoolean(arguments, "dry_run", false)

        val snapshot = synchronized(operationHistoryLock) { operationHistory.toList() }
        val result = runBlocking(Dispatchers.Default) {
            executionMutex.withLock {
                workflowExporter.export(
                    operations = snapshot,
                    name = name,
                    description = description,
                    save = save,
                    keepObservations = keepObservations,
                    dryRun = dryRun
                )
            }
        }

        val text = buildString {
            if (result.error != null) {
                append(result.error)
            } else {
                append("工作流导出完成")
                if (dryRun) append("（预览，未保存）")
                append("。\n")
                append("工作流 ID：").append(result.workflow?.id.orEmpty()).append('\n')
                append("保留步骤：").append(result.keptSteps).append('\n')
                append("已保存到 vFlow：").append(if (result.saved) "是" else "否").append('\n')
                result.filePath?.let { append("JSON 文件：").append(it).append('\n') }
                result.warning?.let { append("警告：").append(it).append('\n') }
                if (result.removedSteps.isNotEmpty()) {
                    append("\n自动检查清理了 ").append(result.removedSteps.size).append(" 步：\n")
                    result.removedSteps.forEach { removed ->
                        append("- ").append(removed.toolName)
                            .append("（").append(removed.summary).append("）：")
                            .append(removed.reason).append('\n')
                    }
                }
                if (result.json.isNotBlank()) {
                    append("\n可用于 vFlow 的工作流 JSON：\n```json\n")
                    append(result.json)
                    append("\n```")
                }
            }
        }

        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            })
            addProperty("isError", result.error != null)
            add("_meta", JsonObject().apply {
                addProperty("status", if (result.error == null) "SUCCESS" else "ERROR")
                addProperty("summary", "导出工作流")
                add("artifacts", JsonArray())
                addProperty("workflow_id", result.workflow?.id.orEmpty())
                addProperty("step_count", result.keptSteps)
                addProperty("removed_count", result.removedSteps.size)
                addProperty("saved", result.saved)
                addProperty("dry_run", dryRun)
                result.filePath?.let { addProperty("json_path", it) }
            })
        }
    }

    /**
     * 从 `/sdcard/vFlow/recordings` 中的 UI 录制生成工作流。
     *
     * 录制参数支持 ID、名称或 JSON 绝对路径；未提供或找不到时返回可用录制列表，
     * 方便调用方重新选择。生成逻辑由 [UiRecordingWorkflowBuilder] 完成。
     */
    private fun workflowFromRecording(arguments: JsonObject): JsonObject {
        val recordingArg = arguments.get("recording")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            .orEmpty()
        if (recordingArg.isBlank()) {
            return buildRecordingSelectionResult("缺少 recording 参数。请传入 UI 录制的 ID、名称或 JSON 文件绝对路径。")
        }

        val recording = runCatching { UiRecordingStore.load(recordingArg) }.getOrNull()
            ?: return buildRecordingSelectionResult("没有找到录制「$recordingArg」。")

        val name = arguments.get("name")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            .orEmpty()
        val description = arguments.get("description")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            .orEmpty()
        val save = argumentBoolean(arguments, "save", true)
        val dryRun = argumentBoolean(arguments, "dry_run", false)

        val result = runBlocking(Dispatchers.IO) {
            executionMutex.withLock {
                recordingWorkflowBuilder.build(
                    recording = recording,
                    name = name,
                    description = description,
                    save = save,
                    dryRun = dryRun
                )
            }
        }

        val text = buildString {
            if (result.error != null) {
                append(result.error).append('\n')
            } else {
                append("已从 UI 录制生成工作流")
                if (dryRun) append("（预览，未保存）")
                append("。\n")
                append("工作流 ID：").append(result.workflowId).append('\n')
                append("工作流名称：").append(result.workflow?.name.orEmpty()).append('\n')
                append("生成步骤：").append(result.stepCount).append('\n')
                append("已保存到 vFlow：").append(if (result.saved) "是" else "否").append('\n')
                result.filePath?.let { append("JSON 文件：").append(it).append('\n') }
            }
            result.screenshotDir?.let { append("来源截图目录：").append(it).append('\n') }
            result.saveWarning?.let { append("警告：").append(it).append('\n') }

            if (result.skipped.isNotEmpty()) {
                append("\n跳过 ").append(result.skipped.size).append(" 项：\n")
                result.skipped.forEach { append("- ").append(it).append('\n') }
            }
            if (result.warnings.isNotEmpty()) {
                append("\n还原提示 ").append(result.warnings.size).append(" 条：\n")
                result.warnings.forEach { append("- ").append(it).append('\n') }
            }
            if (result.json.isNotBlank()) {
                append("\n可用于 vFlow 的工作流 JSON：\n```json\n")
                append(result.json)
                append("\n```")
            }
        }

        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            })
            addProperty("isError", result.error != null)
            add("_meta", JsonObject().apply {
                addProperty("status", if (result.error == null) "SUCCESS" else "ERROR")
                addProperty("summary", "从 UI 录制生成工作流")
                add("artifacts", JsonArray())
                addProperty("workflow_id", result.workflowId)
                addProperty("step_count", result.stepCount)
                addProperty("source_recording", recording.id)
                addProperty("recording_name", recording.name)
                addProperty("file_path", result.filePath.orEmpty())
                addProperty("saved", result.saved)
                addProperty("dry_run", dryRun)
                addProperty("skipped_count", result.skippedCount)
                result.screenshotDir?.let { addProperty("screenshot_dir", it) }
            })
        }
    }

    /**
     * 未提供录制或录制查找失败时，返回可用录制列表。
     */
    private fun buildRecordingSelectionResult(message: String): JsonObject {
        val recordings = runCatching { UiRecordingStore.listRecordings() }.getOrDefault(emptyList())
        val text = buildString {
            append(message).append('\n')
            if (recordings.isEmpty()) {
                append("当前没有可用的 UI 录制。请先在 vFlow 中完成一次 UI 录制。")
            } else {
                append("\n可用录制（").append(UiRecordingStore.recordingsDir.absolutePath).append("）：\n")
                recordings.take(50).forEach { item ->
                    append("- ").append(item.id)
                    append("｜").append(item.name.ifBlank { "未命名" })
                    append("｜").append(item.segments.size).append(" 段 / ")
                    append(item.events.size).append(" 事件")
                    append("｜总时长 ").append(item.durationMs / 1000).append("s\n")
                }
            }
        }
        val available = JsonArray()
        recordings.take(50).forEach { item ->
            available.add(JsonObject().apply {
                addProperty("id", item.id)
                addProperty("name", item.name)
                addProperty(
                    "path",
                    File(UiRecordingStore.recordingsDir, "${item.id}.json").absolutePath
                )
                addProperty("segments", item.segments.size)
                addProperty("events", item.events.size)
            })
        }

        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            })
            addProperty("isError", true)
            add("_meta", JsonObject().apply {
                addProperty("status", "ERROR")
                addProperty("summary", "从 UI 录制生成工作流")
                add("artifacts", JsonArray())
                add("available_recordings", available)
            })
        }
    }

    private fun argumentBoolean(arguments: JsonObject, key: String, default: Boolean): Boolean {
        return runCatching {
            val element = arguments.get(key) ?: return default
            if (!element.isJsonPrimitive) return default
            val primitive = element.asJsonPrimitive
            if (primitive.isBoolean) primitive.asBoolean else default
        }.getOrDefault(default)
    }

    /**
     * 原生 MCP 工作流导出工具定义。
     *
     * 该工具不对应普通 vFlow 模块，因此直接追加到 MCP 工具列表，不经过
     * [ChatAgentModuleExecutor]。
     */
    private fun buildWorkflowExportToolDefinition(): ChatAgentToolDefinition {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "工作流名称；不传则自动生成包含时间的名称。")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "工作流描述。")
                }
                putJsonObject("save") {
                    put("type", "boolean")
                    put("default", true)
                    put("description", "是否保存到 vFlow 的工作流列表，默认 true。")
                }
                putJsonObject("keep_observations") {
                    put("type", "boolean")
                    put("default", false)
                    put("description", "是否保留状态查询、查找、OCR 等观察步骤；默认 false，会自动清理。")
                }
                putJsonObject("dry_run") {
                    put("type", "boolean")
                    put("default", false)
                    put("description", "只预览清理后的 JSON 和清理报告，不保存；确认后再用 false 导出。")
                }
            }
            put("additionalProperties", false)
        }

        return ChatAgentToolDefinition(
            name = WORKFLOW_EXPORT_TOOL_NAME,
            title = "导出工作流",
            description = "把本次 MCP 会话中成功执行的动作导出为 vFlow 可导入的 JSON 工作流；" +
                "自动移除状态查询、查找/OCR 等观察步骤和完全重复的调用，只保留真正执行的动作，并返回清理报告。" +
                WORKFLOW_GROUP_DELAY_HINT,
            moduleId = "vflow.mcp.workflow_export",
            moduleDisplayName = "MCP 工作流导出",
            inputSchema = schema,
            permissionNames = emptyList(),
            riskLevel = ChatAgentToolRiskLevel.LOW,
            usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL)
        )
    }

    /**
     * 从 UI 录制生成工作流的原生 MCP 工具定义。
     *
     * 与 [buildWorkflowExportToolDefinition] 一样，该工具不对应普通 vFlow 模块，
     * 直接追加到 MCP 工具列表并绕过模块执行器。
     */
    private fun buildRecordingWorkflowToolDefinition(): ChatAgentToolDefinition {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("recording") {
                    put("type", "string")
                    put(
                        "description",
                        "UI 录制的 ID、名称或 JSON 文件绝对路径；文件通常位于 /sdcard/vFlow/recordings。"
                    )
                }
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "生成的工作流名称；不传则使用“录制名 + 工作流”。")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "工作流描述；不传则自动写入录制来源和截图目录。")
                }
                putJsonObject("save") {
                    put("type", "boolean")
                    put("default", true)
                    put("description", "是否保存到 vFlow 工作流列表，默认 true。")
                }
                putJsonObject("dry_run") {
                    put("type", "boolean")
                    put("default", false)
                    put("description", "只返回生成结果和 JSON，不保存工作流、不写导出文件。")
                }
            }
            putJsonArray("required") {
                add(kotlinx.serialization.json.JsonPrimitive("recording"))
            }
            put("additionalProperties", false)
        }

        return ChatAgentToolDefinition(
            name = UI_RECORDING_WORKFLOW_TOOL_NAME,
            title = "从UI录制生成工作流",
            description = "读取 /sdcard/vFlow/recordings 中的 UI 录制（界面节点快照、交互事件和截图路径），" +
                "把点击、文本输入、滚动还原为可执行的 vFlow 工作流。" +
                "选择器会优先使用录制时生成的 GKD 表达式，并综合控件 ID、类型、文本、描述、边界和状态信息，" +
                "同时保留坐标兜底；没有事件时会根据相邻界面段落的差异谨慎推断。" +
                "recording 可传录制 ID、名称或 JSON 文件绝对路径；不传时返回可用录制列表。" +
                WORKFLOW_GROUP_DELAY_HINT,
            moduleId = "vflow.mcp.workflow_from_recording",
            moduleDisplayName = "从 UI 录制生成工作流",
            inputSchema = schema,
            permissionNames = emptyList(),
            riskLevel = ChatAgentToolRiskLevel.LOW,
            usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL)
        )
    }

    /**
     * 构建 MCP 工具列表。
     *
     * 暴露全部可独立执行的本地模块；触发器无法直接调用，块控制模块（If/Loop 等）
     * 依赖完整工作流上下文，二者不作为 MCP 工具。
     */
    private fun buildToolList(): List<ChatAgentToolDefinition> {
        val definitions = LinkedHashMap<String, ChatAgentToolDefinition>()
        ModuleRegistry.getAllModules()
            .asSequence()
            .filter { it.blockBehavior.type == BlockType.NONE }
            .filterNot { toolRegistry.isTriggerModule(it.id) }
            .sortedBy { it.id }
            .forEach { module ->
                val definition = toolRegistry.ensureToolForModuleId(module.id) ?: return@forEach
                if (definition.moduleId != module.id) {
                    return@forEach
                }
                definitions.putIfAbsent(definition.name, definition)
            }
        definitions.putIfAbsent(WORKFLOW_EXPORT_TOOL_NAME, buildWorkflowExportToolDefinition())
        definitions.putIfAbsent(UI_RECORDING_WORKFLOW_TOOL_NAME, buildRecordingWorkflowToolDefinition())
        return definitions.values.sortedWith(
            compareBy(
                { if (it.name == PHONE_STATUS_TOOL_NAME) 0 else 1 },
                { it.name }
            )
        )
    }

    /**
     * 未先调用“获取手机状态”时返回的错误结果。
     */
    private fun buildStatusRequiredResult(requestedTool: String): JsonObject {
        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty(
                        "text",
                        "请先调用工具 `$PHONE_STATUS_TOOL_NAME` 获取当前权限、设备状态和屏幕状态，" +
                            "然后再调用 `$requestedTool`。该步骤用于避免在缺少权限或屏幕状态未知时执行操作。"
                    )
                })
            })
            addProperty("isError", true)
            add("_meta", JsonObject().apply {
                addProperty("status", "STATUS_REQUIRED")
                addProperty("summary", "Phone status required")
                add("artifacts", JsonArray())
            })
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        authManager.cleanupExpiredTokens()
        // 没有创建任何令牌时允许局域网直接接入；一旦存在令牌则强制校验。
        if (authManager.getTokens().isEmpty()) {
            return true
        }

        val token = session.headers["authorization"]
            ?.trim()
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)
            ?.trim()
            ?: session.headers["x-mcp-token"]?.trim()
            ?: session.parameters["token"]?.firstOrNull()?.trim()

        return !token.isNullOrBlank() && authManager.verifyToken(token) != null
    }

    private fun readRequestBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: -1L
        if (contentLength >= 0L) {
            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                throw IllegalArgumentException("request body too large")
            }
            val buffer = ByteArray(contentLength.toInt())
            var offset = 0
            while (offset < buffer.size) {
                val read = session.inputStream.read(buffer, offset, buffer.size - offset)
                if (read <= 0) {
                    break
                }
                offset += read
            }
            return String(buffer, 0, offset, Charsets.UTF_8)
        }

        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        return bodyMap["postData"] ?: ""
    }

    private fun startCleanupTask() {
        scope.launch {
            while (running) {
                delay(60_000)
                authManager.cleanupExpiredTokens()
            }
        }
    }

    private fun resolveAppVersion(): String {
        return try {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                ?: DEFAULT_VERSION
        } catch (e: Exception) {
            DEFAULT_VERSION
        }
    }

    private fun jsonResponse(
        status: Response.Status,
        body: String,
        allow: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        val response = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        if (allow != null) {
            response.addHeader("Allow", allow)
        }
        extraHeaders.forEach { (key, value) -> response.addHeader(key, value) }
        return withCommonHeaders(response)
    }

    private fun withCommonHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader(
            "Access-Control-Allow-Headers",
            "Authorization, Content-Type, X-MCP-Token, Mcp-Session-Id, MCP-Protocol-Version, Accept"
        )
        response.addHeader("Access-Control-Expose-Headers", "Mcp-Session-Id")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun errorBody(message: String): String {
        return gson.toJson(JsonObject().apply {
            addProperty("error", message)
        })
    }

    private fun rpcSuccess(id: JsonElement?, result: JsonElement): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("result", result)
        }
    }

    private fun rpcError(
        id: JsonElement?,
        code: Int,
        message: String,
        data: JsonElement? = null
    ): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
                if (data != null) {
                    add("data", data)
                }
            })
        }
    }

    private data class McpToolOutcome(
        val isError: Boolean,
        val status: ChatToolResultStatus,
        val summary: String,
        val text: String,
        val artifacts: List<JsonObject>,
    )

    private class RpcException(val code: Int, message: String) : Exception(message)

    companion object {
        private const val DEFAULT_VERSION = "1.5.3-pr22"
        private const val PHONE_STATUS_TOOL_NAME = "vflow_system_get_phone_status"
        private const val LATEST_PROTOCOL_VERSION = "2025-06-18"
        private val SUPPORTED_PROTOCOL_VERSIONS = setOf(
            "2024-11-05",
            "2025-03-26",
            "2025-06-18",
            "2025-11-25"
        )
        private const val MAX_REQUEST_BODY_BYTES = 4L * 1024L * 1024L
    }
}
