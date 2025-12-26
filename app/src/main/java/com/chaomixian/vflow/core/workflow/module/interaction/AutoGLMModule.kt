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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

class AutoGLMModule : BaseModule() {

    override val id = "vflow.ai.autoglm"
    override val metadata = ActionMetadata(
        name = "AutoGLM 智能体",
        description = "复刻 AutoGLM 项目。基于思维链(CoT)和自定义指令协议，执行复杂的手机操作任务。",
        iconRes = R.drawable.rounded_hexagon_nodes_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = AutoGLMModuleUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(
            PermissionManager.ACCESSIBILITY,
            PermissionManager.STORAGE,
            PermissionManager.USAGE_STATS,
            PermissionManager.OVERLAY
        ) + ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("provider", "服务商", ParameterType.ENUM, "智谱", options = listOf("智谱", "自定义")),
        InputDefinition("base_url", "Base URL", ParameterType.STRING, "https://open.bigmodel.cn/api/paas/v4"),
        InputDefinition("api_key", "API Key", ParameterType.STRING, ""),
        InputDefinition("model", "模型", ParameterType.STRING, "autoglm-phone"),
        InputDefinition("instruction", "指令", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("max_steps", "最大步数", ParameterType.NUMBER, 30.0)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "最终结果", TextVariable.TYPE_NAME),
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawInstruction = step.parameters["instruction"]?.toString() ?: ""

        // 如果指令复杂，只显示标题，避免与预览框重复
        if (VariableResolver.isComplex(rawInstruction)) {
            return "AutoGLM 智能体"
        }

        val instructionPill = PillUtil.createPillFromParam(step.parameters["instruction"], getInputs().find { it.id == "instruction" })
        return PillUtil.buildSpannable(context, "AutoGLM: ", instructionPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val baseUrl = context.variables["base_url"] as? String ?: ""
        val apiKey = context.variables["api_key"] as? String ?: ""
        val model = context.variables["model"] as? String ?: "autoglm-phone"
        val instruction = VariableResolver.resolve(context.variables["instruction"]?.toString() ?: "", context)
        val maxSteps = (context.variables["max_steps"] as? Number)?.toInt() ?: 30

        if (apiKey.isBlank()) return ExecutionResult.Failure("配置错误", "API Key 不能为空")

        val overlayManager = AgentOverlayManager(context.applicationContext)
        withContext(Dispatchers.Main) { overlayManager.show() }

        try {
            val agentTools = AgentTools(context)
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val date = Date()
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
            val dateString = dateFormat.format(date)

            val systemPromptContent = """
                今天的日期是: $dateString
                你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
                你必须严格按照要求输出以下格式：
                <think>{think}</think>
                <answer>{action}</answer>
                
                其中：
                - {think} 是对你为什么选择这个操作的简短推理说明。
                - {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。
                
                操作指令及其作用如下：
                - do(action="Launch", app="xxx")  
                    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Tap", element=[x,y])  
                    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Tap", element=[x,y], message="重要操作")  
                    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
                - do(action="Type", text="xxx")  
                    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
                - do(action="Type_Name", text="xxx")  
                    Type_Name是输入人名的操作，基本功能同Type。
                - do(action="Interact")  
                    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
                - do(action="Swipe", start=[x1,y1], end=[x2,y2])  
                    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Note", message="True")  
                    记录当前页面内容以便后续总结。
                - do(action="Call_API", instruction="xxx")  
                    总结或评论当前页面或已记录的内容。
                - do(action="Long Press", element=[x,y])  
                    Long Pres是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
                - do(action="Double Tap", element=[x,y])  
                    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Take_over", message="xxx")  
                    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
                - do(action="Back")  
                    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Home") 
                    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
                - do(action="Wait", duration="x seconds")  
                    等待页面加载，x为需要等待多少秒。
                - finish(message="xxx")  
                    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。 
                
                必须遵循的规则：
                1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
                2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
                3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
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
                14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
                15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
                16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
                17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
                18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。 
                
                # 用户任务
                $instruction
            """.trimIndent()

            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPromptContent)
            })

            var currentStep = 0
            var taskResult: ExecutionResult? = null
            var errorCount = 0

            val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            while (currentStep < maxSteps) {
                try { overlayManager.awaitState() } catch (e: CancellationException) { return ExecutionResult.Failure("任务取消", "用户手动停止了任务。") }

                overlayManager.updateStatus("分析屏幕...", "准备截屏")
                onProgress(ProgressUpdate("步骤 ${currentStep + 1}/$maxSteps: 观察中..."))

                overlayManager.hideForScreenshot()
                val screenshotResult = AgentUtils.captureScreen(context.applicationContext, context)
                val currentUI = AgentUtils.getCurrentUIInfo(context.applicationContext)
                DebugLogger.d("AutoGLM", "Step ${currentStep + 1} Context: $currentUI")
                overlayManager.restoreAfterScreenshot()

                val contentParts = JSONArray()
                contentParts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Current Step: ${currentStep + 1}\nCurrent Interface: $currentUI")
                })

                if (screenshotResult.base64 != null) {
                    contentParts.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,${screenshotResult.base64}")
                        })
                    })
                }

                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentParts)
                })

                overlayManager.updateStatus("AutoGLM 思考中...", "生成计划")

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("temperature", 0.1)
                }

                val responseJson = callLLM(client, baseUrl, apiKey, requestBody)
                    ?: return ExecutionResult.Failure("API Error", "网络请求失败")

                val choice = responseJson.getJSONArray("choices").getJSONObject(0)
                val message = choice.getJSONObject("message")
                val content = message.optString("content", "")
                DebugLogger.d("AutoGLM: ", content)

                // 清理历史图片消息
                if (messages.length() > 0) {
                    val lastUserMsg = messages.getJSONObject(messages.length() - 1)
                    if (lastUserMsg.optString("role") == "user") {
                        val contents = lastUserMsg.optJSONArray("content")
                        if (contents != null) {
                            // 遍历 content 数组，移除 image_url 类型的内容
                            for (i in contents.length() - 1 downTo 0) {
                                val item = contents.getJSONObject(i)
                                if (item.optString("type") == "image_url") contents.remove(i)
                            }
                            contents.put(JSONObject().apply {
                                put("type", "text")
                                put("text", "[Image removed]")
                            })
                        }
                    }
                }

                messages.put(message)

                // --- 解析 AutoGLM 格式 ---
                val think = extractTag(content, "think")
                var answer = extractTag(content, "answer")

                // 容错提取
                if (answer.isEmpty()) {
                    val commandRegex = Regex("""((?:do\(action=|finish\()(?:[^)]|"(?:[^"\\]|\\.)*")*\))""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val match = commandRegex.find(content)
                    if (match != null) {
                        answer = match.value.trim()
                        // 括号平衡检查
                        if (answer.count { it == '(' } > answer.count { it == ')' }) {
                            answer += ")"
                        }
                        DebugLogger.w("AutoGLM", "未找到标准 XML 标签，通过正则回退提取到指令: $answer")
                    }
                }

                if (think.isNotEmpty()) overlayManager.updateStatus(think, "决策中...")

                if (answer.isNotEmpty()) {
                    errorCount = 0
                    onProgress(ProgressUpdate("指令: $answer"))
                    DebugLogger.d("AutoGLM", "Answer: $answer")

                    val command = parseCommand(answer)
                    val actionName = command["action"] ?: "unknown"

                    overlayManager.updateStatus(null, "执行: $actionName")

                    var actionFeedback = "Action '$actionName' executed."

                    when (actionName) {
                        "Tap" -> {
                            val rawPos = command["element"] as? List<*>
                            if (rawPos != null && rawPos.size == 2) {
                                val normX = (rawPos[0] as Number).toInt()
                                val normY = (rawPos[1] as Number).toInt()
                                val realX = (normX / 999.0 * screenWidth).toInt()
                                val realY = (normY / 999.0 * screenHeight).toInt()
                                agentTools.clickPoint(realX, realY)
                            }
                        }
                        "Type", "Type_Name" -> { // 处理 Type 和 Type_Name
                            val text = command["text"] as? String ?: ""
                            agentTools.inputText(text)
                        }
                        "Launch" -> {
                            val app = command["app"] as? String ?: ""
                            agentTools.launchApp(app)
                        }
                        "Swipe" -> {
                            val start = command["start"] as? List<*>
                            val end = command["end"] as? List<*>
                            if (start != null && end != null && start.size == 2 && end.size == 2) {
                                // 解析归一化坐标 (0-1000)
                                val normSX = (start[0] as Number).toInt()
                                val normSY = (start[1] as Number).toInt()
                                val normEX = (end[0] as Number).toInt()
                                val normEY = (end[1] as Number).toInt()

                                // 映射到真实屏幕坐标
                                val realSX = (normSX / 1000.0 * screenWidth).toInt()
                                val realSY = (normSY / 1000.0 * screenHeight).toInt()
                                val realEX = (normEX / 1000.0 * screenWidth).toInt()
                                val realEY = (normEY / 1000.0 * screenHeight).toInt()

                                // 调用精确滑动
                                actionFeedback = agentTools.swipe(realSX, realSY, realEX, realEY)
                            } else {
                                actionFeedback = "Failed: Invalid swipe coordinates."
                            }
                        }
                        "Double Tap" -> {
                            val rawPos = command["element"] as? List<*>
                            if (rawPos != null && rawPos.size == 2) {
                                val normX = (rawPos[0] as Number).toInt()
                                val normY = (rawPos[1] as Number).toInt()
                                val realX = (normX / 999.0 * screenWidth).toInt()
                                val realY = (normY / 999.0 * screenHeight).toInt()
                                agentTools.doubleTap(realX, realY)
                            }
                        }
                        "Long Press" -> {
                            val rawPos = command["element"] as? List<*>
                            if (rawPos != null && rawPos.size == 2) {
                                val normX = (rawPos[0] as Number).toInt()
                                val normY = (rawPos[1] as Number).toInt()
                                val realX = (normX / 999.0 * screenWidth).toInt()
                                val realY = (normY / 999.0 * screenHeight).toInt()
                                agentTools.longPress(realX, realY)
                            }
                        }
                        "Note" -> {
                            val msg = command["message"] as? String ?: ""
                            DebugLogger.i("AutoGLM", "Note recorded: $msg")
                            actionFeedback = "Note saved locally."
                        }
                        "Call_API" -> {
                            val instr = command["instruction"] as? String ?: ""
                            DebugLogger.i("AutoGLM", "Internal Call_API: $instr")
                            actionFeedback = "Sub-task '$instr' processed (simulated)."
                        }
                        "Interact" -> {
                            // 通知用户并短暂等待
                            val msg = "Interact triggered. Waiting for user choice..."
                            overlayManager.updateStatus("请协助选择", "等待用户...")
                            agentTools.wait(5)
                            actionFeedback = "Waited 5 seconds for user interaction."
                        }
                        "Take_over" -> {
                            val msg = command["message"] as? String ?: "Manual help needed"
                            overlayManager.updateStatus("需要人工接管", msg)
                            // 等待较长时间让用户操作
                            agentTools.wait(10)
                            actionFeedback = "Paused 10s for manual takeover."
                        }
                        "Back" -> agentTools.pressKey("back")
                        "Home" -> agentTools.pressKey("home")
                        "Wait" -> {
                            val durationStr = command["duration"] as? String ?: "5"
                            val sec = durationStr.filter { it.isDigit() }.toIntOrNull() ?: 5
                            agentTools.wait(sec)
                        }
                        "finish" -> {
                            val msg = command["message"] as? String ?: "Done"
                            taskResult = ExecutionResult.Success(mapOf(
                                "result" to TextVariable(msg),
                                "success" to BooleanVariable(true)
                            ))
                        }
                        else -> {
                            if (answer.startsWith("finish")) {
                                val msg = command["message"] as? String ?: answer
                                taskResult = ExecutionResult.Success(mapOf(
                                    "result" to TextVariable(msg),
                                    "success" to BooleanVariable(true)
                                ))
                            }
                        }
                    }

                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Action '$actionName' executed.")
                    })

                } else {
                    errorCount++
                    DebugLogger.w("AutoGLM", "未找到指令，AI 回复: $content")
                    if (errorCount >= 3) return ExecutionResult.Failure("AI 响应异常", "连续 3 次未输出有效指令。AI 最后回复: $content")
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", "SYSTEM ERROR: No valid action found. Output <answer>do(...)</answer>.")
                    })
                    onProgress(ProgressUpdate("格式错误，重试 ($errorCount/3)..."))
                    continue
                }

                if (taskResult != null) break
                currentStep++
            }

            return taskResult ?: ExecutionResult.Success(mapOf(
                "result" to TextVariable("Max steps reached"),
                "success" to BooleanVariable(false)
            ))

        } finally {
            withContext(NonCancellable) { withContext(Dispatchers.Main) { overlayManager.dismiss() } }
        }
    }

    private fun extractTag(text: String, tag: String): String {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val startIndex = text.indexOf(startTag)
        val endIndex = text.indexOf(endTag)
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex + startTag.length, endIndex).trim()
        }
        return ""
    }

    private fun parseCommand(cmd: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        var cleanCmd = cmd.trim()

        // 提取参数时开启 DOT_MATCHES_ALL 以支持参数内的换行符
        val extractionOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

        if (cleanCmd.startsWith("finish")) {
            result["action"] = "finish"

            // 从后往前查找 ") 组合，避免中间的引号导致截断
            val msgTag = "message=\""
            val startIdx = cleanCmd.indexOf(msgTag)
            // 查找最后一个 ") 组合
            val endIdx = cleanCmd.lastIndexOf("\")")

            if (startIdx != -1 && endIdx > startIdx) {
                // 截取 message=" 和 ") 之间的所有内容
                val rawMsg = cleanCmd.substring(startIdx + msgTag.length, endIdx)
                result["message"] = unescape(rawMsg)
            } else {
                // 如果格式极不标准，回退到正则（通常不会执行到这里）
                Regex("message=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let {
                    result["message"] = unescape(it.groupValues[1])
                }
            }
            return result
        }

        if (cleanCmd.startsWith("do(")) {
            cleanCmd = cleanCmd.removePrefix("do(").removeSuffix(")")
        } else {
            // 容错处理：提取最外层括号内容
            val start = cleanCmd.indexOf('(')
            val end = cleanCmd.lastIndexOf(')')
            if (start != -1 && end > start) {
                cleanCmd = cleanCmd.substring(start + 1, end)
            }
        }

        Regex("action=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["action"] = it.groupValues[1] }
        Regex("element=\\[(.*?)\\]", extractionOptions).find(cleanCmd)?.let { match ->
            val parts = match.groupValues[1].split(",")
            if (parts.size == 2) {
                result["element"] = listOf(parts[0].trim().toIntOrNull() ?: 0, parts[1].trim().toIntOrNull() ?: 0)
            }
        }

        Regex("start=\\[(.*?)\\]", extractionOptions).find(cleanCmd)?.let { match ->
            result["start"] = match.groupValues[1].split(",").map { it.trim().toIntOrNull() ?: 0 }
        }
        Regex("end=\\[(.*?)\\]", extractionOptions).find(cleanCmd)?.let { match ->
            result["end"] = match.groupValues[1].split(",").map { it.trim().toIntOrNull() ?: 0 }
        }

        // 使用增强正则提取文本类参数，并反转义
        Regex("text=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["text"] = unescape(it.groupValues[1]) }
        Regex("app=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["app"] = unescape(it.groupValues[1]) }
        Regex("duration=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["duration"] = unescape(it.groupValues[1]) }
        Regex("message=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["message"] = unescape(it.groupValues[1]) }
        Regex("instruction=\"((?:[^\"\\\\]|\\\\.)*)\"", extractionOptions).find(cleanCmd)?.let { result["instruction"] = unescape(it.groupValues[1]) }

        return result
    }

    // 简单的反转义函数，处理 \" -> " 和 \\ -> \
    private fun unescape(str: String): String {
        return str.replace("\\\"", "\"").replace("\\\\", "\\")
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
                continuation.invokeOnCancellation { try { call.cancel() } catch (e: Exception) {} }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val str = response.body?.string()
                            if (response.isSuccessful && str != null) {
                                continuation.resume(JSONObject(str))
                            } else {
                                DebugLogger.e("AutoGLM", "LLM API Error: Code=${response.code}, Msg=${response.message}, Body=$str")
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                })
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DebugLogger.e("AutoGLM", "LLM Network Exception", e)
            null
        }
    }
}