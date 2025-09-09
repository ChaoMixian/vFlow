// 文件：ExecutionUIService.kt
// 描述：提供一个在工作流执行期间请求用户界面交互的服务。
package com.chaomixian.vflow.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ImageVariable
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.ui.common.OverlayUIActivity
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * 执行时UI服务。
 * 负责处理模块在执行过程中需要用户交互的请求，例如弹出输入对话框或显示信息。
 * [最终方案] 实现了智能三层回退逻辑来启动UI：
 * 1. 尝试直接启动 (适用于前台)
 * 2. 如果失败，则尝试 Shizuku 强制启动 (适用于后台)
 * 3. 如果再次失败，则回退到发送通知 (最终保障)
 */
class ExecutionUIService(private val context: Context) {

    companion object {
        private const val TAG = "ExecutionUIService"
        var inputCompletable: CompletableDeferred<Any?>? = null
        private const val INTERACTIVE_CHANNEL_ID = "vflow_interactive_notifications"
        private const val INTERACTIVE_CHANNEL_NAME = "交互式请求"
        private val notificationIdCounter = AtomicInteger(1000)
    }

    private val gson = Gson()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INTERACTIVE_CHANNEL_ID,
                INTERACTIVE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于在工作流执行期间请求用户交互"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * [核心] 使用三层回退逻辑来启动UI并等待结果。
     */
    private suspend fun startActivityAndAwaitResult(intent: Intent, title: String, content: String): CompletableDeferred<Any?> {
        val deferred = CompletableDeferred<Any?>()
        inputCompletable = deferred

        // --- 尝试 1: 直接启动 (最适合前台场景) ---
        try {
            Log.d(TAG, "尝试 1: 直接调用 startActivity...")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            // 注意：如果应用在后台，这一步可能会“静默失败”，即不抛出异常但Activity也无法启动。
            // 这是一个Android系统限制，我们通过后续步骤来弥补。但如果调用来自前台，这将直接成功。
            return deferred
        } catch (e: Exception) {
            // 如果抛出异常，明确表示我们无法直接启动（例如，在某些Android版本上的后台限制）。
            Log.w(TAG, "尝试 1: 直接启动失败，抛出异常。回退到后台启动策略。")
        }

        // --- 后台启动策略 (仅当直接启动失败时执行) ---

        // --- 尝试 2: 使用 Shizuku 强制启动 ---
        if (ShizukuManager.isShizukuActive(context)) {
            Log.d(TAG, "尝试 2: 使用 Shizuku...")
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) // 添加CLEAR_TASK以确保行为一致
                val command = buildAmStartCommand(intent)
                Log.d(TAG, "正在通过 Shizuku 执行: $command")
                val result = ShizukuManager.execShellCommand(context, command)

                if (!result.startsWith("Error:") && !result.contains("Exception")) {
                    Log.d(TAG, "尝试 2: Shizuku 启动成功。")
                    return deferred
                } else {
                    Log.w(TAG, "尝试 2: Shizuku 命令执行失败: $result。回退到通知。")
                }
            } catch (e: Exception) {
                Log.e(TAG, "尝试 2: Shizuku 执行时抛出异常。回退到通知。", e)
            }
        } else {
            Log.d(TAG, "尝试 2: Shizuku 未激活，跳过。")
        }

        // --- 尝试 3: 回退到发送高优先级通知 ---
        Log.d(TAG, "尝试 3: 回退到发送通知。")
        postNotification(intent, title, content)

        return deferred
    }

    /**
     * [核心修改] 从 Intent 构建 `am start` 命令，并处理 Serializable extra。
     */
    private fun buildAmStartCommand(intent: Intent): String {
        val component = intent.component?.flattenToString() ?: throw IllegalArgumentException("Intent 必须有明确的组件")
        val commandBuilder = StringBuilder("am start -n $component")

        val flags = intent.flags
        if (flags != 0) {
            commandBuilder.append(" -f $flags")
        }

        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = extras.get(key)

                // [新增] 特殊处理 workflow_list，将其序列化为JSON字符串
                if (key == "workflow_list" && value is Serializable) {
                    val jsonValue = gson.toJson(value)
                    val escapedJson = "'${jsonValue.replace("'", "'\\''")}'"
                    commandBuilder.append(" --es \"workflow_list_json\" $escapedJson")
                    continue
                }

                val escapedValue = if (value is String) "'${value.replace("'", "'\\''")}'" else value?.toString()

                when (value) {
                    is String -> commandBuilder.append(" --es \"$key\" $escapedValue")
                    is Boolean -> commandBuilder.append(" --ez \"$key\" $value")
                    is Int -> commandBuilder.append(" --ei \"$key\" $value")
                    is Long -> commandBuilder.append(" --el \"$key\" $value")
                    is Float -> commandBuilder.append(" --ef \"$key\" $value")
                }
            }
        }
        return commandBuilder.toString()
    }

    private fun postNotification(intent: Intent, title: String, content: String) {
        val notificationId = notificationIdCounter.getAndIncrement()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, INTERACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_keyboard_external_input_24)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        Log.d(TAG, "已发送交互式通知 (ID: $notificationId)。")
    }

    suspend fun requestInput(type: String, title: String): Any? {
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "input")
            putExtra("input_type", type)
            putExtra("title", title)
        }
        return startActivityAndAwaitResult(intent, title, "点击这里输入 $type").await()
    }

    /**
     * 新增：挂起函数，用于显示快速查看窗口。
     * @param title 窗口标题。
     * @param content 要显示的文本内容。
     */
    suspend fun showQuickView(title: String, content: String) {
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "quick_view")
            putExtra("title", title)
            putExtra("content", content)
        }
        startActivityAndAwaitResult(intent, title, content.take(50)).await()
    }

    /**
     * 新增：挂起函数，用于显示快速查看图片窗口。
     * @param title 窗口标题。
     * @param imageUri 要显示的图片的URI字符串。
     */
    suspend fun showQuickViewImage(title: String, imageUri: String) {
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "quick_view_image")
            putExtra("title", title)
            putExtra("content", imageUri)
        }
        startActivityAndAwaitResult(intent, title, "点击查看图片").await()
    }


    /**
     * 新增：挂起函数，用于请求用户选择一张图片。
     * @return 用户选择的图片的URI字符串，如果用户取消则返回null。
     */
    suspend fun requestImage(): String? {
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "pick_image")
        }
        return startActivityAndAwaitResult(intent, "选择图片", "请点击以从相册中选择一张图片").await() as? String
    }

    /**
     * 新增：挂起函数，用于显示工作流选择器对话框。
     * @param workflows 可供选择的工作流列表。
     * @return 用户选择的工作流的ID，如果用户取消则返回null。
     */
    suspend fun showWorkflowChooser(workflows: List<Workflow>): String? {
        val workflowInfo = workflows.associate { it.id to it.name }
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "workflow_chooser")
            putExtra("workflow_list", workflowInfo as Serializable)
        }
        return startActivityAndAwaitResult(intent, "选择工作流", "有多个工作流可处理此操作，请选择一个").await() as? String
    }

    /**
     * 新增：挂起函数，用于请求系统分享。
     * @param content 要分享的内容（文本或图片变量）。
     * @return 分享是否成功启动。
     */
    suspend fun requestShare(content: Any?): Boolean? {
        val intent = Intent(context, OverlayUIActivity::class.java).apply {
            putExtra("request_type", "share")
            when (content) {
                is TextVariable -> {
                    putExtra("share_type", "text")
                    putExtra("share_content", content.value)
                }
                is String -> {
                    putExtra("share_type", "text")
                    putExtra("share_content", content)
                }
                is ImageVariable -> {
                    putExtra("share_type", "image")
                    putExtra("share_content", content.uri)
                }
                else -> return false
            }
        }
        return startActivityAndAwaitResult(intent, "分享", "点击以打开系统分享菜单").await() as? Boolean
    }
}