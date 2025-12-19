// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
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
            PermissionManager.SHIZUKU,
            PermissionManager.STORAGE,
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("provider", "服务商", ParameterType.ENUM, "OpenAI", options = listOf("阿里云百炼", "OpenAI", "自定义")),
        InputDefinition("base_url", "Base URL", ParameterType.STRING, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        InputDefinition("api_key", "API Key", ParameterType.STRING, ""),
        InputDefinition("model", "模型", ParameterType.STRING, "qwen-vl-max"),
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

    // Native Tools Schema 定义
    private fun getNativeToolsSchema(): JSONArray {
        val schema = JSONArray()
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "click_element", "description": "Click an element. Prefer EXACT text match from UI hierarchy.", "parameters": { "type": "object", "properties": { "target": { "type": "string", "description": "Text or ID of the element." } }, "required": ["target"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "input_text", "description": "Input text into focused field.", "parameters": { "type": "object", "properties": { "text": { "type": "string" } }, "required": ["text"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "scroll", "description": "Scroll the view. 'down' means going further down the page (finger moves up).", "parameters": { "type": "object", "properties": { "direction": { "type": "string", "enum": ["up", "down", "left", "right"] } }, "required": ["direction"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "press_key", "description": "System keys.", "parameters": { "type": "object", "properties": { "action": { "type": "string", "enum": ["back", "home", "recents"] } }, "required": ["action"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "launch_app", "description": "Launch app.", "parameters": { "type": "object", "properties": { "app_name": { "type": "string" } }, "required": ["app_name"] } } }"""))
        schema.put(JSONObject("""{ "type": "function", "function": { "name": "finish_task", "description": "Finish task. CALL THIS when goal is achieved or impossible.", "parameters": { "type": "object", "properties": { "result": { "type": "string" }, "success": { "type": "boolean" } }, "required": ["result", "success"] } } }"""))
        return schema
    }

    data class LastAction(val name: String, val args: String)

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val baseUrl = context.variables["base_url"] as? String ?: ""
        val apiKey = context.variables["api_key"] as? String ?: ""
        val model = context.variables["model"] as? String ?: "qwen-vl-max"
        val instruction = VariableResolver.resolve(context.variables["instruction"]?.toString() ?: "", context)
        val maxSteps = (context.variables["max_steps"] as? Number)?.toInt() ?: 15

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")

        val agentTools = AgentTools(context)
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        // --- 1. 获取动态环境信息 ---
        val date = Date()
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(date)
        val timeString = timeFormat.format(date)

        val messages = JSONArray()

        // --- 2. 构建 System Prompt ---
        // 参考了 prompts_zh.py 的优秀实践：
        // 1. 加入日期时间
        // 2. 强制 <think> 标签
        // 3. 细化操作规则 (App检查, 返回逻辑, 滑动查找, 验证生效)
        val systemPrompt = """
            You are vFlow Agent, an expert Android automation assistant.
            Current Date: $dateString
            Current Time: $timeString
            
            # Protocol
            You must output your reasoning in <think>...</think> tags before calling any tool.
            
            # Operational Rules (MUST FOLLOW):
            1. **Context Check**: Before operating, check if you are in the correct App. If not, use `launch_app`.
            2. **Visual & Structure**: Analyze both the screenshot and the XML hierarchy. The XML provides exact text/IDs, while the screenshot shows layout and icons.
            3. **Navigation**: 
               - If you enter an irrelevant page, use `press_key(action='back')`.
               - If you need to find something not visible, try `scroll(direction='down')`.
            4. **Interaction**:
               - Use `click_element` with exact text from XML if possible.
               - If a click doesn't work (screen didn't change), try adjusting the target or scrolling slightly.
            5. **Search**: If searching for specific content (e.g., specific chat or product), try exact keywords first. If failed, try broader keywords.
            6. **Loop Prevention**: If you perform the same action twice with no effect, STOP. Try a different approach (e.g., scroll, back, or finish with failure).
            7. **Verification**: After every action, observe the new screen state in the next turn to verify success.
            8. **Termination**: Call `finish_task` immediately when the goal is achieved or deemed impossible. Do not idle.
            
            # Goal
            Complete the user's instruction: "$instruction"
        """.trimIndent()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        var currentStep = 0
        var taskResult: ExecutionResult? = null

        var lastAction: LastAction? = null
        var repeatCount = 0

        while (currentStep < maxSteps) {
            onProgress(ProgressUpdate("正在观察屏幕 (步骤 ${currentStep + 1}/$maxSteps)..."))

            // --- 感知 ---
            val (screenshotBase64, _) = AgentUtils.captureScreen(context.applicationContext, context)
            val uiHierarchy = AgentUtils.dumpHierarchy(context.applicationContext)

            val contentParts = JSONArray()
            val stepsLeft = maxSteps - currentStep
            // 倒计时压力提示
            val urgencyWarning = if (stepsLeft <= 2) "\n[WARNING] Only $stepsLeft steps left! Wrap up the task." else ""

            val textContext = """
                Current Step: ${currentStep + 1} / $maxSteps $urgencyWarning
                
                UI Hierarchy (Simplified):
                ${uiHierarchy.take(8000)} ${if(uiHierarchy.length > 8000) "...(truncated)" else ""}
            """.trimIndent()

            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", textContext)
            })

            if (screenshotBase64 != null) {
                contentParts.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$screenshotBase64")
                    })
                })
            } else {
                contentParts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "[System: Screenshot failed. Please rely on UI Hierarchy XML.]")
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
                put("tool_choice", "auto")
            }

            val responseJson = callLLM(client, baseUrl, apiKey, requestBody)

            if (responseJson == null) {
                return ExecutionResult.Failure("API Error", "请求大模型失败，请检查网络或配置。")
            }

            val choice = responseJson.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            if (!message.has("content") || message.isNull("content")) message.put("content", "")
            messages.put(message)

            // 提取 <think> 内容用于日志展示
            val content = message.optString("content", "")
            if (content.contains("<think>")) {
                val thought = content.substringAfter("<think>").substringBefore("</think>").trim()
                if (thought.isNotEmpty()) {
                    DebugLogger.d("AgentModule", "Thought: $thought")
                    onProgress(ProgressUpdate("AI 想法: ${thought.take(50)}..."))
                }
            }

            // --- 行动 ---
            if (message.has("tool_calls")) {
                val toolCalls = message.getJSONArray("tool_calls")

                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val function = toolCall.getJSONObject("function")
                    val name = function.getString("name")
                    val argsStr = function.getString("arguments")
                    val callId = toolCall.getString("id")

                    val args = try { gson.fromJson(argsStr, Map::class.java) as Map<String, Any> } catch (e: Exception) { emptyMap<String, Any>() }

                    // 死循环检测
                    val currentAction = LastAction(name, argsStr)
                    var toolResult = ""

                    if (lastAction == currentAction) repeatCount++ else repeatCount = 0
                    lastAction = currentAction

                    // Scroll 允许最多5次重复，其他操作允许2次
                    val threshold = if (name == "scroll") 5 else 2

                    if (repeatCount >= threshold) {
                        DebugLogger.w("AgentModule", "检测到死循环: $name (重复 $repeatCount 次)")
                        toolResult = "SYSTEM_INTERVENTION: You repeated '$name' $repeatCount times. It seems ineffective. STOP. Try 'scroll' (reverse direction) or 'press_key' (back)."
                        onProgress(ProgressUpdate("检测到操作卡死，强制AI换策略"))
                    } else {
                        onProgress(ProgressUpdate("执行: $name"))
                        DebugLogger.d("AgentModule", "Calling tool: $name args: $args")

                        if (name == "finish_task") {
                            val res = args["result"]?.toString() ?: "Done"
                            val suc = args["success"] as? Boolean ?: true
                            taskResult = ExecutionResult.Success(mapOf("result" to TextVariable(res), "success" to BooleanVariable(suc)))
                            break
                        } else {
                            toolResult = when(name) {
                                "click_element" -> agentTools.clickElement(args["target"]?.toString() ?: "")
                                "input_text" -> agentTools.inputText(args["text"]?.toString() ?: "")
                                "scroll" -> agentTools.scroll(args["direction"]?.toString() ?: "up")
                                "press_key" -> agentTools.pressKey(args["action"]?.toString() ?: "")
                                "launch_app" -> agentTools.launchApp(args["app_name"]?.toString() ?: "")
                                else -> "Error: Unknown tool"
                            }
                        }
                    }

                    messages.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("content", toolResult)
                    })

                    if (taskResult != null) break
                    delay(500) // 增加等待时间，给 App 更多响应时间
                }
                if (taskResult != null) break
            } else {
                // 如果没有调用工具，大概率是在输出 <think> 或者纯对话
                onProgress(ProgressUpdate("AI 正在规划..."))
            }

            currentStep++
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