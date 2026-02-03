package com.chaomixian.vflow.ui.common

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.ExecutionUIService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*

/**
 * 一个透明的 Activity，用于接收来自其他应用的分享意图 (ACTION_SEND) 或查看 JSON 文件 (ACTION_VIEW)。
 * - 对于普通分享内容：找到配置了相应分享别名的工作流，并带上分享的数据来执行它。
 * - 对于 JSON 文件：解析并导入工作流到 vFlow。
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workflowManager = WorkflowManager(applicationContext)

        // 使用协程在后台处理可能耗时的文件操作和UI交互
        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // 检查是否是 JSON 文件
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    val mimeType = intent.type
                    val isJsonFile = mimeType == "application/json" ||
                            (uri != null && uri.toString().endsWith(".json", ignoreCase = true))

                    if (isJsonFile && uri != null) {
                        // 处理 JSON 文件导入
                        handleJsonFile(intent)
                    } else {
                        // 处理普通分享内容
                        val shareableWorkflows = workflowManager.findShareableWorkflows()
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
                    }
                }
                Intent.ACTION_VIEW -> {
                    // 处理查看 JSON 文件，导入工作流
                    handleJsonFile(intent)
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
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { VString(it) }
            }
            // 分享的是图片
            intent.type?.startsWith("image/") == true -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let { uri ->
                    // 为了持久化访问，将分享的图片复制到应用的缓存目录中
                    val tempFile = File(StorageManager.tempDir, "shared_${UUID.randomUUID()}.tmp")
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        VImage(Uri.fromFile(tempFile).toString()) as Parcelable?
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            // 其他文件类型（暂未完全支持，作为文本处理其URI）
            else -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.toString()?.let { VString(it) }
            }
        }
    }

    /**
     * 处理 JSON 文件，导入工作流到 vFlow。
     * 支持两种方式：
     * 1. ACTION_VIEW + application/json (直接打开 JSON 文件)
     * 2. ACTION_SEND + application/json (分享 JSON 文件)
     */
    private fun handleJsonFile(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }

        if (uri == null) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, getString(R.string.toast_import_failed, "无法获取文件"), Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            // 检查是否是 JSON 文件
            val mimeType = contentResolver.getType(uri)
            if (mimeType != "application/json" && !uri.toString().endsWith(".json", ignoreCase = true)) {
                // 如果不是 JSON 文件，尝试按普通分享处理
                handleIncomingIntent(intent)
                return
            }

            // 读取 JSON 文件内容
            val jsonString = contentResolver.openInputStream(uri)?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }

            if (jsonString == null) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(applicationContext, getString(R.string.toast_import_failed, "无法读取文件"), Toast.LENGTH_LONG).show()
                }
                return
            }

            // 解析并导入工作流
            importWorkflowsFromJson(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 从 JSON 字符串导入工作流
     */
    private fun importWorkflowsFromJson(jsonString: String) {
        val gson = Gson()
        val workflowManager = WorkflowManager(applicationContext)

        try {
            val workflowsToImport = mutableListOf<Workflow>()

            // 尝试解析为工作流列表
            try {
                val listType = object : TypeToken<List<Workflow>>() {}.type
                val list: List<Workflow> = gson.fromJson(jsonString, listType) ?: emptyList()
                workflowsToImport.addAll(list)
            } catch (e: JsonSyntaxException) {
                // 尝试解析为单个工作流
                val singleWorkflow: Workflow = gson.fromJson(jsonString, Workflow::class.java)
                workflowsToImport.add(singleWorkflow)
            }

            if (workflowsToImport.isEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(applicationContext, getString(R.string.toast_no_workflow_in_file), Toast.LENGTH_SHORT).show()
                }
                return
            }

            // 导入工作流（自动处理ID冲突）
            var importedCount = 0
            var replacedCount = 0

            workflowsToImport.forEach { workflowToImport ->
                val existingWorkflow = workflowManager.getWorkflow(workflowToImport.id)

                if (existingWorkflow == null) {
                    // 无冲突，直接保存
                    workflowManager.saveWorkflow(workflowToImport)
                    importedCount++
                } else {
                    // 存在冲突，重命名后作为副本导入
                    val newWorkflow = workflowToImport.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${workflowToImport.name} (导入)"
                    )
                    workflowManager.saveWorkflow(newWorkflow)
                    importedCount++
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                val message = if (importedCount > 0) {
                    "成功导入 $importedCount 个工作流"
                } else {
                    "没有导入任何工作流"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}