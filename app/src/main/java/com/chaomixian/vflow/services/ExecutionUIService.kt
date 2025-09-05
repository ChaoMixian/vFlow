package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.CompletableDeferred
import java.io.Serializable

// 文件：ExecutionUIService.kt
// 描述：提供一个在工作流执行期间请求用户界面交互的服务。

/**
 * 执行时UI服务。
 * 负责处理模块在执行过程中需要用户交互的请求，例如弹出输入对话框或显示信息。
 * (注意：此类现在不再直接处理UI，而是作为启动OverlayUIActivity的接口)
 */
class ExecutionUIService(private val context: Context) {

    companion object {
        // 用于在Activity和Service之间传递结果的CompletableDeferred对象
        var inputCompletable: CompletableDeferred<Any?>? = null
    }

    private fun startActivityAndAwaitResult(intent: Intent): CompletableDeferred<Any?> {
        val deferred = CompletableDeferred<Any?>()
        inputCompletable = deferred
        // 必须使用此Flag，因为我们从一个没有UI的上下文（Service/Executor）启动Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return deferred
    }

    /**
     * 挂起函数，用于请求用户输入。
     * @param type 输入类型 ("文本", "数字", "时间", "日期")。
     * @param title 对话框的标题或提示信息。
     * @return 用户输入的值，如果用户取消则返回null。
     */
    suspend fun requestInput(type: String, title: String): Any? {
        // 通过启动一个透明的Activity来承载对话框
        val intent = Intent(context, com.chaomixian.vflow.ui.common.OverlayUIActivity::class.java).apply {
            putExtra("request_type", "input")
            putExtra("input_type", type)
            putExtra("title", title)
        }
        return startActivityAndAwaitResult(intent).await()
    }

    /**
     * 新增：挂起函数，用于显示快速查看窗口。
     * @param title 窗口标题。
     * @param content 要显示的文本内容。
     */
    suspend fun showQuickView(title: String, content: String) {
        val intent = Intent(context, com.chaomixian.vflow.ui.common.OverlayUIActivity::class.java).apply {
            putExtra("request_type", "quick_view")
            putExtra("title", title)
            putExtra("content", content)
        }
        startActivityAndAwaitResult(intent).await()
    }

    /**
     * 新增：挂起函数，用于请求用户选择一张图片。
     * @return 用户选择的图片的URI字符串，如果用户取消则返回null。
     */
    suspend fun requestImage(): String? {
        val intent = Intent(context, com.chaomixian.vflow.ui.common.OverlayUIActivity::class.java).apply {
            putExtra("request_type", "pick_image")
        }
        return startActivityAndAwaitResult(intent).await() as? String
    }

    /**
     * 新增：挂起函数，用于显示工作流选择器对话框。
     * @param workflows 可供选择的工作流列表。
     * @return 用户选择的工作流的ID，如果用户取消则返回null。
     */
    suspend fun showWorkflowChooser(workflows: List<Workflow>): String? {
        val workflowInfo = workflows.associate { it.id to it.name }
        val intent = Intent(context, com.chaomixian.vflow.ui.common.OverlayUIActivity::class.java).apply {
            putExtra("request_type", "workflow_chooser")
            putExtra("workflow_list", workflowInfo as Serializable)
        }
        return startActivityAndAwaitResult(intent).await() as? String
    }
}