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
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ReceiveShareTriggerModule
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_list.WorkflowImportHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    companion object {
        private const val SHARE_TYPE_ANY = "any"
        private const val SHARE_TYPE_TEXT = "text"
        private const val SHARE_TYPE_LINK = "link"
        private const val SHARE_TYPE_IMAGE = "image"
        private const val SHARE_TYPE_FILE = "file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workflowManager = WorkflowManager(applicationContext)
        val importHelper = WorkflowImportHelper(
            context = this,
            workflowManager = workflowManager,
            folderManager = FolderManager(applicationContext)
        ) {
            finish()
        }

        // 使用协程在后台处理可能耗时的文件操作和UI交互
        CoroutineScope(Dispatchers.IO).launch {
            val shouldFinishImmediately = when (intent.action) {
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
                        handleJsonFile(intent, importHelper)
                    } else {
                        // 处理普通分享内容
                        val shareableWorkflows = workflowManager.findShareableWorkflows()
                        val triggerData = handleIncomingIntent(intent)
                        val sharedType = resolveSharedType(intent)
                        val matchingWorkflows = shareableWorkflows.filter {
                            resolveMatchingShareTriggerId(it, sharedType) != null
                        }

                        when {
                            matchingWorkflows.isEmpty() -> {
                                // 在主线程显示 Toast
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(applicationContext, R.string.share_no_matching_workflow, Toast.LENGTH_SHORT).show()
                                }
                            }
                            matchingWorkflows.size == 1 -> {
                                val workflow = matchingWorkflows.first()
                                executeWorkflow(workflow, triggerData)
                            }
                            else -> {
                                val uiService = ExecutionUIService(applicationContext)
                                val selectedWorkflowId = uiService.showWorkflowChooser(matchingWorkflows)
                                if (selectedWorkflowId != null) {
                                    val selectedWorkflow = workflowManager.getWorkflow(selectedWorkflowId)
                                    if (selectedWorkflow != null) {
                                        executeWorkflow(selectedWorkflow, triggerData)
                                    }
                                }
                            }
                        }
                        true
                    }
                }
                Intent.ACTION_VIEW -> {
                    // 处理查看 JSON 文件，导入工作流
                    handleJsonFile(intent, importHelper)
                }
                else -> true
            }

            if (shouldFinishImmediately) {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private fun executeWorkflow(workflow: Workflow, triggerData: Parcelable?) {
        val triggerId = resolveMatchingShareTriggerId(workflow, resolveSharedType(intent))
        WorkflowExecutor.execute(
            workflow = workflow,
            context = applicationContext,
            triggerData = triggerData,
            triggerStepId = triggerId
        )
        // 在主线程显示 Toast
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, getString(R.string.share_workflow_started, workflow.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveSharedType(intent: Intent): String {
        return when {
            intent.type?.startsWith("image/") == true -> SHARE_TYPE_IMAGE
            intent.type?.startsWith("text/") == true -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.startsWith("http://") || text.startsWith("https://")) SHARE_TYPE_LINK else SHARE_TYPE_TEXT
            }
            else -> SHARE_TYPE_FILE
        }
    }

    private fun resolveMatchingShareTriggerId(workflow: Workflow, sharedType: String): String? {
        val acceptedTypeInput = ReceiveShareTriggerModule().getInputs().first { it.id == "acceptedType" }
        return workflow.triggerStepsByType("vflow.trigger.share")
            .firstOrNull { step ->
                when (acceptedTypeInput.normalizeEnumValue(step.parameters["acceptedType"] as? String, SHARE_TYPE_ANY)) {
                    SHARE_TYPE_ANY -> true
                    else -> acceptedTypeInput.normalizeEnumValue(step.parameters["acceptedType"] as? String, SHARE_TYPE_ANY) == sharedType
                }
            }
            ?.id
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
    private suspend fun handleJsonFile(
        intent: Intent,
        importHelper: WorkflowImportHelper
    ): Boolean {
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
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, getString(R.string.toast_import_failed, getString(R.string.import_error_cannot_get_file)), Toast.LENGTH_LONG).show()
            }
            return true
        }

        try {
            // 检查是否是 JSON 文件
            val mimeType = contentResolver.getType(uri)
            if (mimeType != "application/json" && !uri.toString().endsWith(".json", ignoreCase = true)) {
                // 如果不是 JSON 文件，尝试按普通分享处理
                handleIncomingIntent(intent)
                return true
            }

            // 读取 JSON 文件内容
            val jsonString = contentResolver.openInputStream(uri)?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }

            if (jsonString == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.toast_import_failed, getString(R.string.import_error_cannot_read_file)), Toast.LENGTH_LONG).show()
                }
                return true
            }

            val startedImport = withContext(Dispatchers.Main) {
                importHelper.importFromJson(jsonString)
            }
            return !startedImport

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
            return true
        }
    }
}
