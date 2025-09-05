package com.chaomixian.vflow.ui.common

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.ImageVariable
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.ExecutionUIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * 一个透明的 Activity，用于接收来自其他应用的分享意图 (ACTION_SEND)。
 * 它会解析分享的内容，找到配置了相应分享别名的工作流，并带上分享的数据来执行它。
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val workflowManager = WorkflowManager(applicationContext)
        val shareableWorkflows = workflowManager.findShareableWorkflows()

        // 使用协程在后台处理可能耗时的文件操作和UI交互
        CoroutineScope(Dispatchers.IO).launch {
            val triggerData = handleIncomingIntent(intent)

            when {
                shareableWorkflows.isEmpty() -> {
                    // 在主线程显示 Toast
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(applicationContext, "没有找到可处理分享的工作流", Toast.LENGTH_SHORT).show()
                    }
                }
                shareableWorkflows.size == 1 -> {
                    val workflow = shareableWorkflows.first()
                    executeWorkflow(workflow, triggerData)
                }
                else -> {
                    val uiService = ExecutionUIService(applicationContext)
                    val selectedWorkflowId = uiService.showWorkflowChooser(shareableWorkflows)
                    if (selectedWorkflowId != null) {
                        val selectedWorkflow = workflowManager.getWorkflow(selectedWorkflowId)
                        if (selectedWorkflow != null) {
                            executeWorkflow(selectedWorkflow, triggerData)
                        }
                    }
                }
            }
            // 无论结果如何，都关闭这个透明的 Activity
            finish()
        }
    }

    private fun executeWorkflow(workflow: Workflow, triggerData: Parcelable?) {
        WorkflowExecutor.execute(workflow, applicationContext, triggerData)
        // 在主线程显示 Toast
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, "已通过分享启动工作流: ${workflow.name}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 解析传入的 ACTION_SEND Intent，提取分享内容并包装成 Parcelable。
     */
    private fun handleIncomingIntent(intent: Intent): Parcelable? {
        return when {
            // 分享的是纯文本
            intent.type?.startsWith("text/") == true -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { TextVariable(it) }
            }
            // 分享的是图片
            intent.type?.startsWith("image/") == true -> {
                (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                    // 为了持久化访问，将分享的图片复制到应用的缓存目录中
                    val tempFile = File(cacheDir, "shared_${UUID.randomUUID()}.tmp")
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        ImageVariable(Uri.fromFile(tempFile).toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            // 其他文件类型（暂未完全支持，作为文本处理其URI）
            else -> {
                (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.toString()?.let {
                    TextVariable(it)
                }
            }
        }
    }
}