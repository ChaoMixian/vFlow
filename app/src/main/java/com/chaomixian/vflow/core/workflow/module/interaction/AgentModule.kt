// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.utils.ToolSchemaGenerator
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.interaction.AgentModuleUIProvider
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AgentModule : BaseModule() {

    override val id = "vflow.ai.agent"
    override val metadata = ActionMetadata(
        name = "AI 智能体",
        description = "基于大模型的智能助手，能够观察屏幕并调用已启用的 vFlow 模块来完成任务。",
        iconRes = R.drawable.rounded_hexagon_nodes_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = AgentModuleUIProvider()

    private val gson = Gson()

    // 默认启用的工具模块 ID
    val defaultTools = listOf(
        "vflow.interaction.screen_operation", // 屏幕操作
        "vflow.interaction.ocr",             // OCR
        "vflow.device.send_key_event",       // 全局按键(返回/主页)
        "vflow.system.launch_app",           // 启动应用
        "vflow.data.input"                   // 输入文本
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(
            PermissionManager.ACCESSIBILITY,
            PermissionManager.SHIZUKU,
            PermissionManager.STORAGE
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("provider", "服务商", ParameterType.ENUM, "OpenAI", options = listOf("阿里云百炼", "OpenAI", "自定义")),
        InputDefinition("base_url", "Base URL", ParameterType.STRING, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        InputDefinition("api_key", "API Key", ParameterType.STRING, ""),
        InputDefinition("model", "模型", ParameterType.STRING, "glm-4.6"), // 推荐使用具备 Vision 能力的模型
        InputDefinition("instruction", "指令", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("tools", "已启用能力", ParameterType.ANY, defaultValue = defaultTools),
        InputDefinition("max_steps", "最大步数", ParameterType.NUMBER, 10.0)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "最终结果", TextVariable.TYPE_NAME),
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val instructionPill = PillUtil.createPillFromParam(step.parameters["instruction"], getInputs().find { it.id == "instruction" })
        return PillUtil.buildSpannable(context, "AI Agent 执行: ", instructionPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取参数
        val baseUrl = context.variables["base_url"] as? String ?: "https://dashscope.aliyuncs.com/compatible-mode/v1"
        val apiKey = context.variables["api_key"] as? String ?: ""
        val model = context.variables["model"] as? String ?: "glm-4.6"
        val rawInstruction = context.variables["instruction"] as? String ?: ""
        val instruction = VariableResolver.resolve(rawInstruction, context)
        val maxSteps = (context.variables["max_steps"] as? Number)?.toInt() ?: 10

        // 获取用户选择的工具列表
        val allowedTools = (context.variables["tools"] as? List<*>)?.map { it.toString() } ?: defaultTools

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")
        if (instruction.isBlank()) return ExecutionResult.Failure("配置错误", "指令不能为空")
        if (allowedTools.isEmpty()) return ExecutionResult.Failure("配置错误", "请至少启用一个工具模块")

        // 准备工具 Schema (OpenAI Function Calling Format)
        val toolsSchema = JSONArray()
        allowedTools.forEach { moduleId ->
            val module = ModuleRegistry.getModule(moduleId)
            if (module != null) {
                // 使用 ToolSchemaGenerator 自动生成 Schema
                toolsSchema.put(ToolSchemaGenerator.generateSchema(module))
            } else {
                DebugLogger.w("AgentModule", "未找到工具模块: $moduleId，已跳过")
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        // 初始化对话历史
        val messages = JSONArray()

        // 静态 System Prompt 部分
        val staticSystemPrompt = """
            You are vFlow Agent, an intelligent Android automation assistant.
            You interpret user instructions and execute actions using the provided tools.
            
            # CORE PRINCIPLES (MUST FOLLOW):
            1. **Check Context**: Before performing any action, ALWAYS check the `Current App` provided in the user message. If it's not the target app, use `vflow_system_launch_app` first.
            2. **Navigation Logic**: If you are on an irrelevant page, try to go back. Use `vflow_device_send_key_event` with `key_action="返回"`. If the page doesn't change, try to find and click a "Back" or "Close" (X) button on the screen.
            3. **Verification**: After executing an action (especially a click), you must observe the new screen state in the next turn to verify if it succeeded.
            4. **Error Handling**: 
               - If a click didn't work (screen didn't change), wait a bit or try adjusting the position/target.
               - If an element is not found, try to scroll or use OCR to find it.
               - If you are stuck for more than 3 consecutive steps, abort or skip the step.
            5. **Tool Usage**:
               - When using `vflow_interaction_ocr` or `vflow_interaction_screen_operation` (if using ImageVariable), the image parameter is AUTOMATICALLY injected. You do NOT need to pass a value for `image`.
               - For `vflow_interaction_screen_operation`: 'target' can be coordinates "x,y" or a view ID. Prioritize coordinates from OCR or Accessibility info.
            
            # Goal:
            Complete the user's task: "$instruction"
        """.trimIndent()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", staticSystemPrompt)
        })

        var currentStep = 0
        var finalResult = ""
        var lastActionDescription = "None (Start)"

        // 4. ReAct 循环
        while (currentStep < maxSteps) {
            // 收集当前上下文信息
            onProgress(ProgressUpdate("观察环境 (步骤 ${currentStep + 1}/$maxSteps)..."))

            val screenContext = captureScreenContext(context)
            val currentAppPackage = screenContext.currentPackage ?: "Unknown"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.getDefault()).format(Date())

            // 构建本轮的动态上下文 Prompt
            val contextPrompt = """
                --- ROUND INFO ---
                Current Step: ${currentStep + 1} / $maxSteps
                Time: $timestamp
                Current App: $currentAppPackage
                Last Action: $lastActionDescription
                
                Screen Hierarchy (Accessibility):
                ${screenContext.hierarchy.take(3000)} ${if(screenContext.hierarchy.length > 3000) "...(truncated)" else ""}
            """.trimIndent()

            // 构建用户消息 (包含截图和文本)
            val contentParts = JSONArray()

            // 添加文本上下文
            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", contextPrompt)
            })

            // 添加视觉上下文 (截图)
            if (screenContext.imageBase64 != null) {
                contentParts.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,${screenContext.imageBase64}")
                    })
                })
            } else {
                contentParts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "[Warning: Screenshot not available. Rely on Hierarchy XML.]")
                })
            }

            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentParts)
            })

            // 请求 LLM
            onProgress(ProgressUpdate("思考中 ($model)..."))
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("tools", toolsSchema) // 注入用户选择的工具
                put("tool_choice", "auto")
            }

            val responseJson = callLLM(client, baseUrl, apiKey, requestBody)

            if (responseJson == null) {
                return ExecutionResult.Failure("API错误", "请求大模型失败，请检查网络或API Key")
            }

            val choice = responseJson.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")

            // 移除 content 为 null 的情况，OpenAI 规范要求 content 不能为 null，除非有 tool_calls
            if (!message.has("content") || message.isNull("content")) {
                message.put("content", "")
            }

            messages.put(message)

            // 决策 (Act)
            if (message.has("tool_calls")) {
                val toolCalls = message.getJSONArray("tool_calls")
                val actionDescriptions = StringBuilder()

                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val function = toolCall.getJSONObject("function")
                    val functionName = function.getString("name")
                    val arguments = function.getString("arguments")
                    val toolCallId = toolCall.getString("id")

                    val actionDesc = "Call $functionName($arguments)"
                    actionDescriptions.append(actionDesc).append("; ")
                    onProgress(ProgressUpdate("执行: $functionName"))

                    // 传入截图路径
                    val screenshotPath = screenContext.imagePath ?: ""
                    val toolResult = executeTool(context, functionName, arguments, screenshotPath)

                    messages.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", toolCallId)
                        put("content", toolResult)
                    })
                }
                lastActionDescription = actionDescriptions.toString()
            } else {
                // 模型决定结束或给出最终回复
                finalResult = message.getString("content")
                onProgress(ProgressUpdate("Agent 完成任务"))
                break
            }

            currentStep++
            // 给应用一点反应时间
            delay(500)
        }

        if (currentStep >= maxSteps) {
            return ExecutionResult.Failure("超时", "达到最大执行步数 ($maxSteps)，任务未完成。\n最后状态: $lastActionDescription")
        }

        return ExecutionResult.Success(mapOf(
            "result" to TextVariable(finalResult),
            "success" to BooleanVariable(true)
        ))
    }

    // 辅助函数

    private suspend fun executeTool(context: ExecutionContext, functionName: String, argsJson: String, screenshotPath: String): String {
        // 将函数名 (vflow_device_click) 还原为模块 ID (vflow.device.click)
        var targetModule: ActionModule? = null
        val allModules = ModuleRegistry.getAllModules()

        for (module in allModules) {
            if (module.id.replace(".", "_") == functionName) {
                targetModule = module
                break
            }
        }

        if (targetModule == null) {
            val errorMsg = "Error: Module matching function '$functionName' not found."
            DebugLogger.e("AgentModule", errorMsg)
            return errorMsg
        }

        val argsMap = try {
            gson.fromJson(argsJson, Map::class.java) as MutableMap<String, Any?>
        } catch (e: Exception) {
            val errorMsg = "Error: Invalid arguments JSON: $argsJson"
            DebugLogger.e("AgentModule", errorMsg, e)
            return errorMsg
        }

        DebugLogger.d("AgentModule", "正在执行模块: ${targetModule.id} 参数: $argsMap")

        // 参数注入与变量解析逻辑

        // 准备截图变量
        val magicVars = mutableMapOf<String, Any?>()
        if (screenshotPath.isNotEmpty()) {
            val imageVar = ImageVariable(Uri.fromFile(File(screenshotPath)).toString())
            // 放入源变量，供内部使用
            magicVars["agent_screen_image"] = imageVar
        }

        // 检查并修正参数 (处理 LLM 可能传错的情况)
        val imageInput = targetModule.getInputs().find { it.id == "image" }
        if (imageInput != null) {
            val passedValue = argsMap["image"]?.toString()
            // 如果参数为空，或者 LLM 传了 "current"/"screen" 等占位符，或者传了正确的引用，
            // 我们都统一修正为标准的变量引用
            if (passedValue.isNullOrBlank() || passedValue == "current" || passedValue == "screen" || passedValue.contains("agent_screen_image")) {
                argsMap["image"] = "{{agent_screen_image}}"
            }
        }

        // 执行变量解析 (模拟 WorkflowExecutor 的行为)
        // 将 argsMap 中的引用字符串 (如 "{{agent_screen_image}}") 解析为实际的对象，
        // 并放入 resolvedMagicVars 中，key 为参数名 (如 "image")。
        // 这样目标模块 (如 OCRModule) 执行时，通过 context.magicVariables["image"] 就能取到 ImageVariable 对象。
        val resolvedMagicVars = mutableMapOf<String, Any?>()
        resolvedMagicVars.putAll(magicVars) // 保留基础变量

        argsMap.forEach { (key, value) ->
            if (value is String) {
                // 简单的引用检查
                if (value.startsWith("{{") && value.endsWith("}}")) {
                    val varName = value.removeSurrounding("{{", "}}")
                    val varValue = magicVars[varName]
                    if (varValue != null) {
                        resolvedMagicVars[key] = varValue
                        DebugLogger.d("AgentModule", "参数注入: $key -> Object(${varValue.javaClass.simpleName})")
                    }
                }
            }
        }

        // 创建临时执行上下文
        val tempContext = context.copy(
            variables = argsMap,
            magicVariables = resolvedMagicVars
        )

        try {
            val result = targetModule.execute(tempContext) { progress ->
                DebugLogger.d("AgentModule", "[Tool:${targetModule.metadata.name}] ${progress.message}")
            }

            return when (result) {
                is ExecutionResult.Success -> {
                    // 将结果序列化为 JSON 字符串返回给 LLM
                    val outputStr = gson.toJson(result.outputs)
                    DebugLogger.d("AgentModule", "工具执行完成: $outputStr")
                    outputStr
                }
                is ExecutionResult.Failure -> {
                    val errorStr = "Error: ${result.errorTitle} - ${result.errorMessage}"
                    DebugLogger.e("AgentModule", "工具执行失败: $errorStr")
                    errorStr
                }
                else -> "Success (Signal)"
            }
        } catch (e: Exception) {
            val crashMsg = "Exception during tool execution: ${e.message}"
            DebugLogger.e("AgentModule", crashMsg, e)
            return "Error: $crashMsg"
        }
    }

    data class ScreenContext(
        val imageBase64: String?,
        val imagePath: String?,
        val hierarchy: String,
        val currentPackage: String?
    )

    private suspend fun captureScreenContext(context: ExecutionContext): ScreenContext {
        var base64Img: String? = null
        var imagePath: String? = null
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen")

        if (captureModule != null) {
            val tempContext = context.copy(variables = mutableMapOf("mode" to "自动"))
            val result = captureModule.execute(tempContext) { }
            if (result is ExecutionResult.Success) {
                val imageVar = result.outputs["image"] as? ImageVariable
                if (imageVar != null) {
                    val uri = Uri.parse(imageVar.uri)
                    imagePath = uri.path
                    base64Img = imageUriToBase64(context.applicationContext, uri)
                }
            }
        }

        val accService = ServiceStateBus.getAccessibilityService()
        val rootNode = accService?.rootInActiveWindow

        val currentPackage = rootNode?.packageName?.toString()

        val hierarchy = rootNode?.let { root ->
            val sb = StringBuilder()
            dumpNode(root, sb, 0)
            sb.toString()
        } ?: "No accessibility info available (Service not connected or restricted)"

        return ScreenContext(base64Img, imagePath, hierarchy, currentPackage)
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (!node.isVisibleToUser) return

        val indent = "  ".repeat(depth)
        val text = node.text
        val desc = node.contentDescription
        val viewId = node.viewIdResourceName

        // 只记录有意义的节点以减少 Token 消耗
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable || !viewId.isNullOrBlank()) {
            sb.append(indent)
            sb.append("<node")
            sb.append(" class=\"${node.className.toString().substringAfterLast('.')}\"")
            if (!viewId.isNullOrBlank()) sb.append(" id=\"$viewId\"")
            if (!text.isNullOrBlank()) sb.append(" text=\"$text\"")
            if (!desc.isNullOrBlank()) sb.append(" desc=\"$desc\"")
            if (node.isClickable) sb.append(" clickable=\"true\"")
            if (node.isEditable) sb.append(" editable=\"true\"")

            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            sb.append(" bounds=\"[${rect.left},${rect.top}][${rect.right},${rect.bottom}]\"")

            sb.append(" />\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, sb, depth + 1) }
        }
    }

    private suspend fun callLLM(client: OkHttpClient, url: String, key: String, body: JSONObject): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = if (url.endsWith("/")) "${url}chat/completions" else "$url/chat/completions"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string()

                if (response.isSuccessful && responseStr != null) {
                    JSONObject(responseStr)
                } else {
                    DebugLogger.e("AgentModule", "LLM Error: ${response.code} $responseStr")
                    null
                }
            } catch (e: Exception) {
                DebugLogger.e("AgentModule", "LLM Exception", e)
                null
            }
        }
    }

    private fun imageUriToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 压缩图片以减少 Token 和网络负载
                val options = BitmapFactory.Options().apply { inSampleSize = 2 } // 1/2 采样
                val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return null

                val output = ByteArrayOutputStream()
                // 压缩质量 50
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }
}