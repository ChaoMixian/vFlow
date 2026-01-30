// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/OperitModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Operit 模块 - 用于与 Operit AI 助手应用交互
 *
 * 支持两种功能：
 * 1. 发送消息给 Operit (EXTERNAL_CHAT)
 * 2. 触发 Operit 工作流 (WORKFLOW_TRIGGER)
 */
class OperitModule : BaseModule() {

    override val id = "vflow.interaction.operit"
    override val metadata = ActionMetadata(
        name = "Operit 交互",  // Fallback
        nameStringRes = R.string.module_vflow_interaction_operit_name,
        description = "与 Operit AI 助手交互：发送消息或触发工作流。",  // Fallback
        descriptionStringRes = R.string.module_vflow_interaction_operit_desc,
        iconRes = R.drawable.rounded_smart_toy_24,
        category = "界面交互"
    )

    private val modes = listOf("发送消息", "触发工作流")

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("message")

    // Operit 包名常量
    companion object {
        const val OPERIT_PACKAGE = "com.ai.assistance.operit"
        const val ACTION_EXTERNAL_CHAT = "com.ai.assistance.operit.EXTERNAL_CHAT"
        const val ACTION_EXTERNAL_CHAT_RESULT = "com.ai.assistance.operit.EXTERNAL_CHAT_RESULT"
        const val ACTION_TRIGGER_WORKFLOW = "com.ai.assistance.operit.TRIGGER_WORKFLOW"
        const val WORKFLOW_TASKER_RECEIVER = "$OPERIT_PACKAGE/.integrations.tasker.WorkflowTaskerReceiver"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // 通用参数
        InputDefinition(
            id = "mode",
            name = "交互模式",
            staticType = ParameterType.ENUM,
            defaultValue = "发送消息",
            options = modes,
            acceptsMagicVariable = false
        ),

        // EXTERNAL_CHAT 模式参数
        InputDefinition(
            id = "message",
            name = "消息内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        ),
        InputDefinition(
            id = "request_id",
            name = "请求ID",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            id = "create_new_chat",
            name = "创建新对话",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "group",
            name = "分组名称",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            id = "chat_id",
            name = "对话ID",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            id = "show_floating",
            name = "显示悬浮窗",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "auto_exit_after_ms",
            name = "自动退出时间(毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = -1.0,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "stop_after",
            name = "执行后停止",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isFolded = true
        ),

        // WORKFLOW_TRIGGER 模式参数
        InputDefinition(
            id = "workflow_action",
            name = "工作流Action",
            staticType = ParameterType.STRING,
            defaultValue = ACTION_TRIGGER_WORKFLOW,
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            id = "workflow_extras",
            name = "工作流参数",
            staticType = ParameterType.ANY,
            defaultValue = emptyMap<String, Any?>(),
            acceptsMagicVariable = true,
            isFolded = true
        ),

        // 回传配置
        InputDefinition(
            id = "wait_for_result",
            name = "等待结果",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            id = "timeout_ms",
            name = "超时时间(毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 30000.0,
            acceptsMagicVariable = false,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("ai_response", "AI回复", VTypeRegistry.STRING.id),
        OutputDefinition("chat_id", "对话ID", VTypeRegistry.STRING.id),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: "发送消息"

        // 使用 RichTextUIProvider 时，getSummary 只返回简单标题
        // 富文本预览会自动显示在下方
        return when (mode) {
            "发送消息" -> context.getString(R.string.summary_vflow_interaction_operit_send)
            "触发工作流" -> {
                val action = step.parameters["workflow_action"] as? String ?: ACTION_TRIGGER_WORKFLOW
                val actionPill = PillUtil.createPillFromParam(action, getInputs().find { it.id == "workflow_action" })
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_interaction_operit_trigger), actionPill)
            }
            else -> metadata.name
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val mode = context.variables["mode"] as? String ?: "发送消息"

        return when (mode) {
            "发送消息" -> executeExternalChat(context, onProgress)
            "触发工作流" -> executeWorkflowTrigger(context, onProgress)
            else -> ExecutionResult.Failure("参数错误", "不支持的交互模式: $mode")
        }
    }

    /**
     * 执行 EXTERNAL_CHAT - 发送消息给 Operit
     */
    private suspend fun executeExternalChat(
        execContext: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = execContext.applicationContext

        // 解析参数
        val rawMessage = execContext.variables["message"]?.toString() ?: ""
        val message = VariableResolver.resolve(rawMessage, execContext)

        if (message.isBlank()) {
            return ExecutionResult.Failure("参数错误", "消息内容不能为空")
        }

        val requestId = VariableResolver.resolve(
            execContext.variables["request_id"]?.toString() ?: "",
            execContext
        )
        val createNewChat = execContext.variables["create_new_chat"] as? Boolean ?: false
        val group = VariableResolver.resolve(
            execContext.variables["group"]?.toString() ?: "",
            execContext
        )
        val chatId = VariableResolver.resolve(
            execContext.variables["chat_id"]?.toString() ?: "",
            execContext
        )
        val showFloating = execContext.variables["show_floating"] as? Boolean ?: false
        val autoExitAfterMs = (execContext.variables["auto_exit_after_ms"] as? Number)?.toLong() ?: -1L
        val stopAfter = execContext.variables["stop_after"] as? Boolean ?: false
        val waitForResult = execContext.variables["wait_for_result"] as? Boolean ?: true
        val timeoutMs = (execContext.variables["timeout_ms"] as? Number)?.toLong() ?: 30000L

        onProgress(ProgressUpdate("正在发送消息到 Operit..."))

        // 构建发送给 Operit 的 Intent
        val intent = Intent(ACTION_EXTERNAL_CHAT).apply {
            `package` = OPERIT_PACKAGE
            putExtra("message", message)

            if (requestId.isNotBlank()) {
                putExtra("request_id", requestId)
            }
            if (createNewChat) {
                putExtra("create_new_chat", true)
                if (group.isNotBlank()) {
                    putExtra("group", group)
                }
            } else if (chatId.isNotBlank()) {
                putExtra("chat_id", chatId)
            }
            if (showFloating) {
                putExtra("show_floating", true)
                if (autoExitAfterMs > 0) {
                    putExtra("auto_exit_after_ms", autoExitAfterMs)
                }
            }
            if (stopAfter) {
                putExtra("stop_after", true)
            }

            // 设置回传配置
            putExtra("reply_action", ACTION_EXTERNAL_CHAT_RESULT)
            putExtra("reply_package", appContext.packageName)
        }

        return try {
            // 发送广播
            appContext.sendBroadcast(intent)
            DebugLogger.i("OperitModule", "已发送 EXTERNAL_CHAT 广播: message=$message, request_id=$requestId")

            onProgress(ProgressUpdate("消息已发送"))

            if (waitForResult) {
                // 等待结果回传
                val result = waitForResult(requestId, timeoutMs)
                if (result != null) {
                    if (result.success) {
                        ExecutionResult.Success(mapOf(
                            "success" to VBoolean(true),
                            "ai_response" to VString(result.aiResponse ?: ""),
                            "chat_id" to VString(result.chatId ?: ""),
                            "error" to VString("")
                        ))
                    } else {
                        ExecutionResult.Failure(
                            "Operit 返回错误",
                            result.error ?: "未知错误"
                        )
                    }
                } else {
                    // 超时，但消息已发送
                    onProgress(ProgressUpdate("等待结果超时，但消息已发送"))
                    ExecutionResult.Success(mapOf(
                        "success" to VBoolean(true),
                        "ai_response" to VString(""),
                        "chat_id" to VString(""),
                        "error" to VString("等待结果超时")
                    ))
                }
            } else {
                // 不等待结果，直接返回成功
                ExecutionResult.Success(mapOf(
                    "success" to VBoolean(true),
                    "ai_response" to VString(""),
                    "chat_id" to VString(""),
                    "error" to VString("")
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e("OperitModule", "发送消息失败", e)
            ExecutionResult.Failure("发送失败", "发送广播失败: ${e.message}")
        }
    }

    /**
     * 执行 WORKFLOW_TRIGGER - 触发 Operit 工作流
     */
    private suspend fun executeWorkflowTrigger(
        execContext: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = execContext.applicationContext

        // 解析参数
        val rawAction = execContext.variables["workflow_action"]?.toString() ?: ACTION_TRIGGER_WORKFLOW
        val workflowAction = VariableResolver.resolve(rawAction, execContext)

        @Suppress("UNCHECKED_CAST")
        val extrasMap = (execContext.magicVariables["workflow_extras"] as? VDictionary)?.raw
            ?: (execContext.variables["workflow_extras"] as? Map<String, Any?>)
            ?: emptyMap()

        if (workflowAction.isBlank()) {
            return ExecutionResult.Failure("参数错误", "工作流Action不能为空")
        }

        onProgress(ProgressUpdate("正在触发工作流: $workflowAction"))

        // 构建显式广播 Intent（推荐方式）
        val intent = Intent().apply {
            component = android.content.ComponentName(
                OPERIT_PACKAGE,
                "com.ai.assistance.operit.integrations.tasker.WorkflowTaskerReceiver"
            )
            action = workflowAction

            // 添加 extras
            extrasMap.forEach { (key, value) ->
                when (val strVal = value.toString()) {
                    "true" -> putExtra(key, true)
                    "false" -> putExtra(key, false)
                    else -> {
                        strVal.toIntOrNull()?.let { putExtra(key, it) }
                            ?: strVal.toDoubleOrNull()?.let { putExtra(key, it) }
                            ?: putExtra(key, strVal)
                    }
                }
            }
        }

        return try {
            // 发送显式广播
            appContext.sendBroadcast(intent)
            DebugLogger.i("OperitModule", "已触发工作流: action=$workflowAction, extras=$extrasMap")

            onProgress(ProgressUpdate("工作流已触发"))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "ai_response" to VString(""),
                "chat_id" to VString(""),
                "error" to VString("")
            ))
        } catch (e: Exception) {
            DebugLogger.e("OperitModule", "触发工作流失败", e)
            ExecutionResult.Failure("触发失败", "发送工作流广播失败: ${e.message}")
        }
    }

    /**
     * 等待 Operit 返回结果
     * 使用挂起函数和 BroadcastReceiver 实现
     */
    private suspend fun waitForResult(requestId: String, timeoutMs: Long): OperitResult? {
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_EXTERNAL_CHAT_RESULT) {
                        val resultRequestId = intent.getStringExtra("request_id") ?: ""

                        // 匹配请求ID
                        if (requestId.isBlank() || resultRequestId == requestId) {
                            val success = intent.getBooleanExtra("success", false)
                            val chatId = intent.getStringExtra("chat_id")
                            val aiResponse = intent.getStringExtra("ai_response")
                            val error = intent.getStringExtra("error")

                            DebugLogger.i(
                                "OperitModule",
                                "收到回传: request_id=$resultRequestId, success=$success, chat_id=$chatId"
                            )

                            // 取消注册并恢复协程
                            try {
                                context?.unregisterReceiver(this)
                            } catch (e: Exception) {
                                DebugLogger.w("OperitModule", "取消注册receiver失败", e)
                            }

                                    val result = OperitResult(
                                        success = success,
                                        chatId = chatId,
                                        aiResponse = aiResponse,
                                        error = error
                                    )
                                    continuation.resume(result)
                                }
                            }
                }
            }

            try {
                // 注册广播接收器
                val appContext = com.chaomixian.vflow.core.logging.LogManager.applicationContext
                val filter = android.content.IntentFilter(ACTION_EXTERNAL_CHAT_RESULT)
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // 必须使用 RECEIVER_EXPORTED 才能接收来自 Operit（外部应用）的广播
                    android.content.Context.RECEIVER_EXPORTED
                } else {
                    0
                }
                appContext.registerReceiver(receiver, filter, flags)

                // 设置超时
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        DebugLogger.w("OperitModule", "取消注册receiver失败(超时)", e)
                    }
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }, timeoutMs)

                // 当协程被取消时，取消注册
                continuation.invokeOnCancellation {
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        DebugLogger.w("OperitModule", "取消注册receiver失败(取消)", e)
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("OperitModule", "注册广播接收器失败", e)
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Operit 返回结果的数据类
     */
    private data class OperitResult(
        val success: Boolean,
        val chatId: String?,
        val aiResponse: String?,
        val error: String?
    )
}