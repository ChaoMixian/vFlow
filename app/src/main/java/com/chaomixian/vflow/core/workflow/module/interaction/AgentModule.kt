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
import com.chaomixian.vflow.ui.overlay.AgentOverlayManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.CancellationException

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
            PermissionManager.USAGE_STATS,
            PermissionManager.OVERLAY
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
        val rawInstruction = step.parameters["instruction"]?.toString() ?: ""

        // 如果指令复杂，只显示标题
        if (VariableResolver.isComplex(rawInstruction)) {
            return "AI Agent"
        }

        val instructionPill = PillUtil.createPillFromParam(step.parameters["instruction"], getInputs().find { it.id == "instruction" })
        return PillUtil.buildSpannable(context, "AI Agent: ", instructionPill)
    }

    // 定义工具 Schema
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
        // 获取参数
        val baseUrl = context.variables["base_url"] as? String ?: ""
        val apiKey = context.variables["api_key"] as? String ?: ""
        val model = context.variables["model"] as? String ?: "glm-4.6v-flash"
        val instruction = VariableResolver.resolve(context.variables["instruction"]?.toString() ?: "", context)
        val maxSteps = (context.variables["max_steps"] as? Number)?.toInt() ?: 15

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")

        // 初始化悬浮窗
        val overlayManager = AgentOverlayManager(context.applicationContext)
        withContext(Dispatchers.Main) {
            overlayManager.show()
        }

        try {
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

            val recentApps = AgentUtils.getRecentApps(context.applicationContext)

            val messages = JSONArray()

            // System Prompt
            val systemPrompt = """
            # SETUP
            You are a professional Android operation agent assistant that can fulfill the user's high-level instructions. Given a screenshot of the Android interface at each step, you first analyze the situation, then plan the best course of action using Python-style pseudo-code.

            Current Date: $dateString
            Current Time: $timeString

            Your response format must be structured as follows:

            Think first: Use <think>...</think> to analyze the current screen, identify key elements, and determine the most efficient action.
            
            Your output should STRICTLY follow the format:
            <think>
            [Your thought]
            </think>
            
            # RECENTLY USED APPS (Context):
                The user has used the following apps in the last 30 days (Sorted by usage frequency). 
                **PRIORITIZE these apps** when the user's request is vague (e.g., "play music" -> pick the music app **which is used most** from this list).
                $recentApps
                        
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
            
            # HYBRID PERCEPTION STRATEGY (VISION + XML):
            You have two inputs: **Screenshot (Vision)** and **UI Hierarchy (XML)**. You MUST combine them:
            1. **Vision First**: Locate the target element visually on the Screenshot to get approximate coordinates.
            2. **XML Verification (CRITICAL)**: **ALWAYS** look up the corresponding node in the XML using the visual coordinates.
               - Match the visual location with the `bounds="[l,t][r,b]"` in XML.
               - **CONFIRM** the element type: Is it an `EditText`? A `Button`? Or just `TextView` (label)?
               - **READ** the exact `text` or `id` from XML. Visual OCR can be wrong; XML is the ground truth for text content.
            3. **Action Selection**:
               - If the element has a unique `text` or `id` in XML, prefer `click_element(target="...")`. It is more robust than coordinates.
               - If it's an icon with no text/id, use `click_point(x,y)` with normalized coordinates.
               
            # REASONING GUIDE:
            - **Bad Reasoning**: "I see the search bar. I will call `input_text('hello')` immediately." (Wrong! It might not be focused).
            - **Good Reasoning (Turn 1)**: "I see the search bar. I must focus it first. I will call `click_point(500, 100)`."
            - **Good Reasoning (Turn 2)**: "I see the keyboard is now visible / the field cursor is blinking. Now it is safe to call `input_text('hello')`."
            - **Verification**: 
              - "I tried clicking 'Submit', but the screen is the same. The click might have failed. I will try `click_element` or adjust coordinates."
              
            # THINKING PROTOCOL:
                Your output format:
                <think>
                1. Observation: "I see a 'Search' bar at top."
                2. Cross-Check: "In XML, I found a node <EditText text='Search...' bounds='[100,50][900,150]' editable='true' /> matching that location."
                3. Reasoning: "The field is editable. I need to click it first to focus."
                4. Plan: "Call click_element('Search')."
                </think>
            
            # 必须遵循的规则：
                1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
                2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
                3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back 重新进入。
                4. 如果页面显示网络问题，需要重新加载，请点击重新加载。
                5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
                6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
                7. 在做小红书总结类任务时一定要筛选图文笔记。
                8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
                9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
                10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
                11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
                12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
                13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
                14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish_task result说明点击不生效。
                15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish_task result说明但没找到要求的项目。
                16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
                17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish_task(result="原因")。
                18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
                19. 播放视频、音乐时，三角形播放按钮出现意味着当前处于暂停状态，请点击以播放。
                
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

            // --- 主循环 ---
            while (currentStep < maxSteps) {
                // 在每一步开始前，检查是否暂停或被取消
                try {
                    overlayManager.awaitState()
                } catch (e: CancellationException) {
                    // 如果是用户点击悬浮窗的“结束”，会抛出异常
                    return ExecutionResult.Failure("任务取消", "用户手动停止了任务。")
                }

                overlayManager.updateStatus("分析屏幕...", "准备截屏")
                onProgress(ProgressUpdate("正在观察屏幕 (步骤 ${currentStep + 1}/$maxSteps)..."))

                // --- 感知 ---

                // 隐藏悬浮窗
                overlayManager.hideForScreenshot()

                val screenshotResult = AgentUtils.captureScreen(context.applicationContext, context)

                val uiHierarchy = AgentUtils.dumpHierarchy(context.applicationContext)

                // 恢复悬浮窗
                overlayManager.restoreAfterScreenshot()

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
                    ${uiHierarchy.take(16000)} ${if(uiHierarchy.length > 16000) "...(truncated)" else ""}
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
                overlayManager.updateStatus("AI 思考中...", "生成计划")
                onProgress(ProgressUpdate("思考中..."))

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("tools", getNativeToolsSchema())
                    put("tool_choice", "required") // 强制工具调用
                }

                // 再次检查暂停状态（因为截图和思考可能耗时）
                try { overlayManager.awaitState() } catch (e: CancellationException) { return ExecutionResult.Failure("任务取消", "用户手动停止了任务。") }

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
                    val thoughtDisplay = if (content.contains("<think>")) {
                        content.substringAfter("<think>").substringBefore("</think>").trim()
                    } else {
                        content
                    }
                    // 悬浮窗显示想法
                    overlayManager.updateStatus(thoughtDisplay, "决策中...")
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

                            // 更新悬浮窗显示即将执行的操作
                            val actionDisplay = when(name) {
                                "click_point" -> "点击坐标"
                                "click_element" -> "点击元素"
                                "input_text" -> "输入文本"
                                "scroll" -> "滚动屏幕"
                                "launch_app" -> "启动应用"
                                "wait" -> "等待中..."
                                "finish_task" -> "任务完成"
                                else -> "执行: $name"
                            }
                            overlayManager.updateStatus(null, actionDisplay)

                            // 行动前最后一次检查暂停，方便用户拦截操作
                            try { overlayManager.awaitState() } catch (e: CancellationException) { return ExecutionResult.Failure("任务取消", "用户手动停止了任务。") }

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
                                put("tool_call_id", "error")
                                put("content", "SYSTEM ERROR: Invalid tool call format or parameters. Error: ${e.message}. Please generate a valid JSON tool call.")
                            })
                        }
                    }
                } else {
                    noToolCallCount++
                    val thoughtSnippet = if (content.length > 20) content.take(20) + "..." else content
                    onProgress(ProgressUpdate("AI 未执行操作 ($noToolCallCount/3): $thoughtSnippet"))

                    if (noToolCallCount >= 3) {
                        return ExecutionResult.Failure("AI 响应异常", "连续多次未操作。AI 回复: $content")
                    }

                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", "SYSTEM ERROR: You MUST call a tool.")
                    })
                    continue
                }

                currentStep++

                // 我觉得没必要了
                // delay(1000)

            } // 主循环结束

            return taskResult ?: ExecutionResult.Success(mapOf(
                "result" to TextVariable("Task stopped: Max steps ($maxSteps) reached."),
                "success" to BooleanVariable(false)
            ))

        } finally {
            // 关闭悬浮窗
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) {
                    overlayManager.dismiss()
                }
            }
        }
    }

    private suspend fun callLLM(client: OkHttpClient, url: String, key: String, body: JSONObject): JSONObject? {
        val endpoint = if (url.endsWith("/")) "${url}chat/completions" else "$url/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)

                // 注册取消回调：一旦协程被取消，立即终止网络请求
                continuation.invokeOnCancellation {
                    try {
                        call.cancel()
                    } catch (e: Exception) {
                        DebugLogger.e("AgentModule", "Cancel request failed", e)
                    }
                }

                // 异步执行
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // 在回调中读取数据，注意异常处理
                        try {
                            val str = response.body?.string()
                            if (continuation.isActive) {
                                if (response.isSuccessful && str != null) {
                                    continuation.resume(JSONObject(str))
                                } else {
                                    DebugLogger.e("AgentModule", "LLM Error: ${response.code} $str")
                                    continuation.resume(null)
                                }
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            // 如果是取消异常，直接抛出，让外层 finally 块处理
            if (e is kotlinx.coroutines.CancellationException) throw e

            DebugLogger.e("AgentModule", "LLM Exception", e)
            null
        }
    }
}