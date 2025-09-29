// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/SmsTriggerHandler.kt
// 描述: 监听系统短信广播，并根据规则触发工作流。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.DictionaryVariable
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.utils.CodeExtractor // 导入 CodeExtractor
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsTriggerHandler : ListeningTriggerHandler() {

    private var smsReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "SmsTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.sms"

    override fun startListening(context: Context) {
        if (smsReceiver != null) return
        DebugLogger.d(TAG, "启动短信监听...")

        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages.isNotEmpty()) {
                        val sender = messages[0].originatingAddress ?: ""
                        val content = messages.joinToString("") { it.messageBody }
                        DebugLogger.d(TAG, "收到短信来自: $sender")
                        findAndExecuteWorkflows(ctx, sender, content)
                    }
                }
            }
        }
        // 注册广播接收器
        context.registerReceiver(smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
    }

    override fun stopListening(context: Context) {
        smsReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "短信监听已停止。")
            } finally {
                smsReceiver = null
            }
        }
    }

    private fun findAndExecuteWorkflows(context: Context, sender: String, content: String) {
        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                // [修改] 检查过滤器，并将匹配结果（包含验证码）返回
                val matchResult = checkFilters(sender, content, config)
                if (matchResult.isMatch) {
                    DebugLogger.i(TAG, "短信满足条件，触发工作流 '${workflow.name}'")
                    val triggerDataMap = mutableMapOf(
                        "sender" to TextVariable(sender),
                        "content" to TextVariable(content)
                    )
                    // 如果提取到了验证码，也将其加入触发数据
                    matchResult.verificationCode?.let {
                        triggerDataMap["verification_code"] = TextVariable(it)
                    }
                    WorkflowExecutor.execute(workflow, context.applicationContext, DictionaryVariable(triggerDataMap))
                }
            }
        }
    }

    // [新增] 定义一个数据类来返回匹配结果
    private data class FilterMatchResult(val isMatch: Boolean, val verificationCode: String? = null)

    /**
     * [核心修改] 检查过滤器，并在匹配时提取验证码。
     */
    private fun checkFilters(sender: String, content: String, config: Map<String, Any?>): FilterMatchResult {
        val senderFilterType = config["sender_filter_type"] as? String ?: "任意号码"
        val senderFilterValue = config["sender_filter_value"] as? String ?: ""
        val contentFilterType = config["content_filter_type"] as? String ?: "任意内容"
        val contentFilterValue = config["content_filter_value"] as? String ?: ""

        val senderMatches = when (senderFilterType) {
            "任意号码" -> true
            "号码包含" -> sender.contains(senderFilterValue)
            "号码不包含" -> !sender.contains(senderFilterValue)
            "正则匹配" -> try { Pattern.compile(senderFilterValue).matcher(sender).find() } catch (e: Exception) { false }
            else -> false
        }

        if (!senderMatches) return FilterMatchResult(false)

        // 当内容过滤器是“识别验证码”时
        if (contentFilterType == "识别验证码") {
            // 使用 CodeExtractor 提取验证码
            val code = CodeExtractor.getCode(content)
            return if (code != null) {
                FilterMatchResult(true, code)
            } else {
                FilterMatchResult(false)
            }
        }

        // 处理其他内容过滤器
        val contentMatches = when (contentFilterType) {
            "任意内容" -> true
            "内容包含" -> content.contains(contentFilterValue, ignoreCase = true)
            "内容不包含" -> !content.contains(contentFilterValue, ignoreCase = true)
            "正则匹配" -> try { Pattern.compile(contentFilterValue).matcher(content).find() } catch (e: Exception) { false }
            else -> false
        }

        return FilterMatchResult(contentMatches)
    }
}