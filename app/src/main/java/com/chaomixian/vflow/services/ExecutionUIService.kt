package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.ui.common.InputRequestActivity
import kotlinx.coroutines.CompletableDeferred

// 文件：ExecutionUIService.kt
// 描述：提供一个在工作流执行期间请求用户界面交互的服务。

/**
 * 执行时UI服务。
 * 负责处理模块在执行过程中需要用户交互的请求，例如弹出输入对话框。
 * 它通过启动一个专门的Activity来展示UI，并挂起执行流程直到UI返回结果。
 */
class ExecutionUIService(private val context: Context) {

    companion object {
        // 用于在Activity和Service之间传递结果的CompletableDeferred对象
        var inputCompletable: CompletableDeferred<Any?>? = null
    }

    /**
     * 挂起函数，用于请求用户输入。
     * @param type 输入类型 ("文本", "数字", "时间")。
     * @param title 对话框的标题或提示信息。
     * @return 用户输入的值，如果用户取消则返回null。
     */
    suspend fun requestInput(type: String, title: String): Any? {
        // 创建一个新的CompletableDeferred来等待结果
        val deferred = CompletableDeferred<Any?>()
        inputCompletable = deferred

        // 创建启动InputRequestActivity的Intent
        val intent = Intent(context, InputRequestActivity::class.java).apply {
            putExtra(InputRequestActivity.EXTRA_INPUT_TYPE, type)
            putExtra(InputRequestActivity.EXTRA_TITLE, title)
            // 必须使用此Flag，因为我们从一个没有UI的上下文（Service/Executor）启动Activity
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        // 等待InputRequestActivity完成并通过inputCompletable返回结果
        return deferred.await()
    }
}