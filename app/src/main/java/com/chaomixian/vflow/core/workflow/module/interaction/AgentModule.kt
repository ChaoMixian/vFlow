// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AgentModule : BaseModule() {

    override val id = "vflow.ai.agent"
    override val metadata = ActionMetadata(
        name = "AI 智能体",
        description = "全自动 AI 助手。基于视觉和UI结构理解屏幕，自动完成任务。",
        iconRes = R.drawable.rounded_hexagon_nodes_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = AgentModuleUIProvider()
    private val gson = Gson()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(
            PermissionManager.ACCESSIBILITY,
            PermissionManager.STORAGE,
        ) + ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("provider", "服务商", ParameterType.ENUM, "智谱", options = listOf("阿里云百炼", "智谱", "自定义")),
        InputDefinition("base_url", "Base URL", ParameterType.STRING, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        InputDefinition("api_key", "API Key", ParameterType.STRING, ""),
        InputDefinition("model", "模型", ParameterType.STRING, "glm-4.6v-flash"),
        InputDefinition("instruction", "指令", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("max_steps", "最大步数", ParameterType.NUMBER, 15.0),
        InputDefinition("tools", "工具配置", ParameterType.ANY, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "最终结果", TextVariable.TYPE_NAME),
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val instructionPill = PillUtil.createPillFromParam(step.parameters["instruction"], getInputs().find { it.id == "instruction" })
        return PillUtil.buildSpannable(context, "AI Agent: ", instructionPill)
    }

    private fun getNativeToolsSchema(): JSONArray {
        val schema = JSONArray()
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "click_point", "description": "Tap at specific NORMALIZED coordinates (0-1000). x=0 is left, x=1000 is right. y=0 is top, y=1000 is bottom.", "parameters": { "type": "object", "properties": { "x": { "type": "integer", "description": "Normalized X coordinate (0-1000)" }, "y": { "type": "integer", "description": "Normalized Y coordinate (0-1000)" } }, "required": ["x", "y"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "click_element", "description": "Click an element by exact text. Only use this if visual coordinates are impossible to determine.", "parameters": { "type": "object", "properties": { "target": { "type": "string", "description": "Exact text or ID." } }, "required": ["target"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "input_text", "description": "Input text into focused field.", "parameters": { "type": "object", "properties": { "text": { "type": "string" } }, "required": ["text"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "scroll", "description": "Scroll the view. 'down' means going further down (swiping up).", "parameters": { "type": "object", "properties": { "direction": { "type": "string", "enum": ["up", "down", "left", "right"] } }, "required": ["direction"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "press_key", "description": "System keys.", "parameters": { "type": "object", "properties": { "action": { "type": "string", "enum": ["back", "home", "recents"] } }, "required": ["action"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "launch_app", "description": "Launch an app by name.", "parameters": { "type": "object", "properties": { "app_name": { "type": "string", "description": "Exact app name (e.g. 'WeChat')" } }, "required": ["app_name"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "wait", "description": "Wait for a specific amount of time (seconds).", "parameters": { "type": "object", "properties": { "seconds": { "type": "integer" } }, "required": ["seconds"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "finish_task", "description": "Call this when goal is achieved.", "parameters": { "type": "object", "properties": { "result": { "type": "string" }, "success": { "type": "boolean" } }, "required": ["result", "success"] } } }"""))
        return schema
    }

    data class LastAction(val name: String, val args: String)

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val baseUrl = context.variables["base_url"] as? String ?: ""
        val apiKey = context.variables["api_key"] as? String ?: ""
        val model = context.variables["model"] as? String ?: "glm-4.6v-flash"
        val instruction = VariableResolver.resolve(context.variables["instruction"]?.toString() ?: "", context)
        val maxSteps = (context.variables["max_steps"] as? Number)?.toInt() ?: 15

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")

        val agentTools = AgentTools(context)
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val date = Date()
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(date)
        val timeString = timeFormat.format(date)

        val messages = JSONArray()

        // System Prompt
        val systemPrompt = """
            You are vFlow Agent, an expert Android automation assistant.
            Current Date: $dateString
            Current Time: $timeString
            
            # CRITICAL INSTRUCTION: TOOL USAGE
            **You MUST use the native Function Calling mechanism.**
            - **DO NOT** output Markdown code blocks or XML tags for tools.
            - **DO NOT** just describe actions.
            - **First** `<think>...</think>`, **Then** `Tool Call`.
            
            # MANDATORY PROTOCOL:
            1. **LAUNCH APP FIRST**: If task requires a specific app (e.g. "WeChat") and it's not open, CALL `launch_app`.
            2. **ONE STEP AT A TIME**: ONE tool call per turn.
            3. **VISUAL PRIORITY**: Use `click_point(x, y)` with normalized coordinates (0-1000) from the screenshot.
            4. **VERIFY SUCCESS (CRITICAL)**: Before taking the next step, **CHECK the screen state** (Screenshot/XML) to confirm the previous action worked. 
               - Did the screen change? 
               - Did the text appear in the box?
               - **If NOT, DO NOT proceed. RETRY with a different method.**
            
            # MOBILE UI COMMON SENSE (CRITICAL):
            1. **Search Hints are NOT Content**: Text inside search bars (e.g., "Search...", "请输入", "Type here") is usually a **placeholder**. You do **NOT** need to delete it. Just click the field and call `input_text`.
            2. **Popups**: If a permission popup or ad appears, close or allow it first.
            
            # INPUT TEXT PROTOCOL (STRICT):
            1. **CLICK FIRST**: You CANNOT input text into a field that is not focused. **Step 1: Click the input field.**
            2. **WAIT & VERIFY**: Wait for the next turn. Look for visual signs of focus (cursor, caret, keyboard appearing) or check if `focused="true"` in XML.
            3. **THEN INPUT**: Only call `input_text` AFTER you have confirmed the field is focused in the current turn.
            
            # HYBRID PERCEPTION (VISUAL + HIERARCHY):
            - **Screenshot** is your EYES. Use it to determine **WHERE** (x, y) elements are located.
            - **UI Hierarchy (XML)** is your DATA. Use it to determine **WHAT** elements are:
              - Check if a node has `editable="true"` to confirm it's an input field.
              - Check if `text` matches your target EXACTLY to confirm you found the right item.
              - Note that XML might contain text that is NOT visible on screen (e.g. scrollable lists). **Always verify visibility with the Screenshot.**
            
            # REASONING GUIDE:
            - **Bad Reasoning**: "I see the search bar. I will call `input_text('hello')` immediately." (Wrong! It might not be focused).
            - **Good Reasoning (Turn 1)**: "I see the search bar. I must focus it first. I will call `click_point(500, 100)`."
            - **Good Reasoning (Turn 2)**: "I see the keyboard is now visible / the field cursor is blinking. Now it is safe to call `input_text('hello')`."
            - **Verification**: "I tried clicking 'Submit', but the screen is the same. The click might have failed. I will try `click_element` or adjust coordinates."
            
            # User Goal
            "$instruction"
        """.trimIndent()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        var currentStep = 0
        var taskResult: ExecutionResult? = null

        var lastAction: LastAction? = null
        var repeatCount = 0
        var noToolCallCount = 0

        while (currentStep < maxSteps) {
            onProgress(ProgressUpdate("正在观察屏幕 (步骤 ${currentStep + 1}/$maxSteps)..."))

            // --- 感知 ---
            val screenshotResult = AgentUtils.captureScreen(context.applicationContext, context)
            val uiHierarchy = AgentUtils.dumpHierarchy(context.applicationContext)

            val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val contentParts = JSONArray()
            val stepsLeft = maxSteps - currentStep
            val urgencyWarning = if (stepsLeft <= 2) "\n[WARNING] Only $stepsLeft steps left! Wrap up." else ""

            val textContext = """
                Step: ${currentStep + 1} / $maxSteps $urgencyWarning
                Screen Size: ${screenWidth}x${screenHeight} (Coordinates must be normalized 0-1000)
                
                UI Hierarchy (Reference Only):
                ${uiHierarchy.take(8000)} ${if(uiHierarchy.length > 8000) "...(truncated)" else ""}
            """.trimIndent()

            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", textContext)
            })

            if (screenshotResult.base64 != null) {
                contentParts.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,${screenshotResult.base64}")
                    })
                })
            } else {
                contentParts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "[System: Screenshot failed. Rely on XML.]")
                })
            }

            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentParts)
            })

            // --- 思考 ---
            onProgress(ProgressUpdate("思考中..."))

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("tools", getNativeToolsSchema())
                put("tool_choice", "required") // 强制工具调用
            }

            val responseJson = callLLM(client, baseUrl, apiKey, requestBody)

            if (responseJson == null) {
                return ExecutionResult.Failure("API Error", "请求大模型失败，请检查网络或配置。")
            }

            val choice = responseJson.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            if (!message.has("content") || message.isNull("content")) message.put("content", "")
            messages.put(message)

            val content = message.optString("content", "")
            if (content.isNotEmpty()) {
                DebugLogger.d("AgentModule", "AI Output: $content")
                onProgress(ProgressUpdate("AI: ${content.take(50)}..."))
            }

            // --- 行动 ---
            if (message.has("tool_calls")) {
                noToolCallCount = 0
                val toolCalls = message.getJSONArray("tool_calls")

                if (toolCalls.length() > 0) {
                    try {
                        // 使用 optJSONObject 和 optString 安全解析，防止崩溃
                        val toolCall = toolCalls.getJSONObject(0)
                        val function = toolCall.optJSONObject("function")

                        if (function == null) {
                            throw Exception("Missing 'function' object in tool call")
                        }

                        val name = function.optString("name", "")
                        val argsStr = function.optString("arguments", "{}")
                        val callId = toolCall.optString("id", "call_${System.currentTimeMillis()}")

                        if (name.isEmpty()) {
                            throw Exception("Tool name is missing or empty")
                        }

                        val args = try { gson.fromJson(argsStr, Map::class.java) as Map<String, Any> } catch (e: Exception) { emptyMap<String, Any>() }

                        // 死循环检测
                        val currentAction = LastAction(name, argsStr)
                        var toolResult = ""

                        if (lastAction == currentAction) repeatCount++ else repeatCount = 0
                        lastAction = currentAction

                        val threshold = if (name == "scroll" || name == "wait") 5 else 2

                        if (repeatCount >= threshold) {
                            DebugLogger.w("AgentModule", "检测到死循环: $name")
                            toolResult = "SYSTEM_INTERVENTION: Repeated action detected. STOP. Try 'scroll', 'press_key(back)', or 'finish_task(success=false)'."
                            onProgress(ProgressUpdate("检测到操作卡死，强制AI换策略"))
                        } else {
                            onProgress(ProgressUpdate("执行: $name"))
                            DebugLogger.d("AgentModule", "Calling tool: $name args: $args")

                            if (name == "finish_task") {
                                val res = args["result"]?.toString() ?: "Done"
                                val suc = args["success"] as? Boolean ?: true
                                taskResult = ExecutionResult.Success(mapOf("result" to TextVariable(res), "success" to BooleanVariable(suc)))
                            } else {
                                toolResult = when(name) {
                                    "click_point" -> {
                                        val normX = (args["x"] as? Number)?.toInt() ?: 0
                                        val normY = (args["y"] as? Number)?.toInt() ?: 0
                                        val realX = (normX / 1000.0 * screenWidth).toInt()
                                        val realY = (normY / 1000.0 * screenHeight).toInt()
                                        DebugLogger.d("AgentModule", "坐标映射: Norm($normX, $normY) -> Pixel($realX, $realY)")
                                        agentTools.clickPoint(realX, realY)
                                    }
                                    "click_element" -> agentTools.clickElement(args["target"]?.toString() ?: "")
                                    "input_text" -> agentTools.inputText(args["text"]?.toString() ?: "")
                                    "scroll" -> agentTools.scroll(args["direction"]?.toString() ?: "up")
                                    "press_key" -> agentTools.pressKey(args["action"]?.toString() ?: "")
                                    "launch_app" -> agentTools.launchApp(args["app_name"]?.toString() ?: "")
                                    "wait" -> agentTools.wait((args["seconds"] as? Number)?.toInt() ?: 5)
                                    else -> "Error: Unknown tool name '$name'"
                                }
                            }
                        }

                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("content", toolResult)
                        })

                        if (taskResult != null) break

                    } catch (e: Exception) {
                        DebugLogger.e("AgentModule", "Error parsing/executing tool call", e)
                        // 反馈错误给 AI，不崩溃
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", "error") // 占位
                            put("content", "SYSTEM ERROR: Invalid tool call format or parameters. Error: ${e.message}. Please generate a valid JSON tool call.")
                        })
                    }
                }
            } else {
                noToolCallCount++
                val thoughtSnippet = if (content.length > 20) content.take(20) + "..." else content
                onProgress(ProgressUpdate("AI 未执行操作 ($noToolCallCount/3): $thoughtSnippet"))

                if (noToolCallCount >= 3) {
                    return ExecutionResult.Failure("AI 响应异常", "连续 3 次未调用任何工具，任务终止。AI 回复: $content")
                }

                // 警告AI，要求使用工具
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "SYSTEM ERROR: You outputted text but did NOT call any tool. I cannot read your mind. You MUST call a tool (e.g. `launch_app`, `click_point`) to make things happen.")
                })

                continue
            }

            currentStep++
            delay(1000)
        }

        return taskResult ?: ExecutionResult.Success(mapOf(
            "result" to TextVariable("Task stopped: Max steps ($maxSteps) reached."),
            "success" to BooleanVariable(false)
        ))
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
                val str = response.body?.string()
                if (response.isSuccessful && str != null) JSONObject(str) else {
                    DebugLogger.e("AgentModule", "LLM Error: ${response.code} $str")
                    null
                }
            } catch (e: Exception) {
                DebugLogger.e("AgentModule", "LLM Exception", e)
                null
            }
        }
    }
}