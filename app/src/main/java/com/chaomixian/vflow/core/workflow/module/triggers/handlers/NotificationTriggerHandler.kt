// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/NotificationTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.DictionaryVariable
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.services.VFlowNotificationListenerService
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class NotificationTriggerHandler : ListeningTriggerHandler() {

    companion object {
        private const val TAG = "NotificationTriggerHandler"

        // 持有 NotificationListenerService 的静态引用，以便服务可以与之通信
        var notificationListener: NotificationListenerService? = null

        // 当收到新通知时，由 NotificationListenerService 调用
        fun onNotificationPosted(sbn: StatusBarNotification) {
            // 触发器处理器通常在后台线程工作，这里也保持一致
            // 注意：因为 onNotificationPosted 是静态的，它无法直接访问 triggerScope
            // 这是一个设计上的权衡，实际触发在下面的 checkAndExecute 中完成
            instance?.handleNotification(sbn)
        }

        // 静态实例，用于让服务回调
        private var instance: NotificationTriggerHandler? = null
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.notification"

    override fun startListening(context: Context) {
        instance = this
        DebugLogger.d(TAG, "开始监听通知事件。请确保通知使用权已授予。")
        // 实际的监听由 VFlowNotificationListenerService 完成，这里只需标记为活动状态

        // [核心修复] 如果服务当前未连接，主动请求系统重新绑定
        if (notificationListener == null) {
            DebugLogger.d(TAG, "通知监听服务当前未连接，正在请求重新绑定...")
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, VFlowNotificationListenerService::class.java)
                )
            } catch (e: Exception) {
                DebugLogger.e(TAG, "请求重新绑定通知监听服务时出错", e)
            }
        }
    }

    override fun stopListening(context: Context) {
        instance = null
        DebugLogger.d(TAG, "停止监听通知事件。")
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras
        val packageName = sbn.packageName
        val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        DebugLogger.d(TAG, "收到通知: [$packageName] $title: $content")

        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val appFilter = config["app_filter"] as? String
                val titleFilter = config["title_filter"] as? String
                val contentFilter = config["content_filter"] as? String

                val appMatches = appFilter.isNullOrBlank() || appFilter == packageName
                val titleMatches = titleFilter.isNullOrBlank() || title.contains(titleFilter, ignoreCase = true)
                val contentMatches = contentFilter.isNullOrBlank() || content.contains(contentFilter, ignoreCase = true)

                if (appMatches && titleMatches && contentMatches) {
                    DebugLogger.i(TAG, "通知满足条件，触发工作流 '${workflow.name}'")
                    val triggerData = DictionaryVariable(mapOf(
                        "package_name" to TextVariable(packageName),
                        "title" to TextVariable(title),
                        "content" to TextVariable(content),
                        "id" to TextVariable(sbn.key)
                    ))
                    // 在这里获取 applicationContext
                    val appContext = workflowManager.context.applicationContext
                    WorkflowExecutor.execute(workflow, appContext, triggerData)
                }
            }
        }
    }
}