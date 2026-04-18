package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.ClipboardTriggerModule
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ClipboardTriggerHandler : ListeningTriggerHandler() {

    private var clipboardManager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var coreWatcherJob: Job? = null
    private var lastStandardSignature: String? = null

    companion object {
        private const val TAG = "ClipboardTriggerHandler"
    }

    override fun startListening(context: Context) {
        syncStandardListener(context)
        startCoreWatcher(context.applicationContext)
        DebugLogger.d(TAG, "剪贴板变更监听已启动")
    }

    override fun stopListening(context: Context) {
        coreWatcherJob?.cancel()
        coreWatcherJob = null
        unregisterStandardListener()
        lastStandardSignature = null
    }

    private fun handleClipboardChanged(context: Context) {
        triggerScope.launch {
            DebugLogger.d(TAG, "收到剪贴板变更事件")
            val standardTriggers = listeningTriggers.filter { getTriggerMode(it) == ClipboardTriggerModule.MODE_STANDARD }
            if (standardTriggers.isEmpty()) {
                return@launch
            }

            val snapshot = snapshotClipboardData(context) ?: return@launch
            val signature = snapshot.first
            if (signature == lastStandardSignature) {
                return@launch
            }
            lastStandardSignature = signature

            standardTriggers.forEach { trigger ->
                DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}'，事件: clipboard_changed (standard)")
                executeTrigger(context, trigger, VDictionary(snapshot.second))
            }
        }
    }

    private fun startCoreWatcher(context: Context) {
        if (coreWatcherJob != null) return
        coreWatcherJob = triggerScope.launch {
            while (isActive) {
                syncStandardListener(context)
                val hasCoreTriggers = listeningTriggers.any { getTriggerMode(it) == ClipboardTriggerModule.MODE_CORE }
                if (!hasCoreTriggers) {
                    delay(1_000)
                    continue
                }

                val connected = VFlowCoreBridge.connect(context)
                if (!connected) {
                    DebugLogger.w(TAG, "Core 剪贴板监听连接失败，稍后重试")
                    delay(2_000)
                    continue
                }

                val streamConnected = VFlowCoreBridge.streamClipboardEvents { event ->
                    if (event.event != "clipboard_changed") {
                        return@streamClipboardEvents
                    }

                    val outputs = snapshotCoreEventData(context, event)
                    val latestCoreTriggers = listeningTriggers.filter { getTriggerMode(it) == ClipboardTriggerModule.MODE_CORE }
                    latestCoreTriggers.forEach { trigger ->
                        DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}'，事件: clipboard_changed (core)")
                        executeTrigger(context, trigger, VDictionary(outputs))
                    }
                }

                if (!streamConnected && isActive) {
                    DebugLogger.w(TAG, "Core 剪贴板事件流已断开，稍后重连")
                    delay(1_000)
                }
            }
        }
    }

    private fun getTriggerMode(trigger: TriggerSpec): String {
        val modeInput = ClipboardTriggerModule().getInputs().first { it.id == "mode" }
        return modeInput.normalizeEnumValueOrNull(trigger.parameters["mode"] as? String)
            ?: ClipboardTriggerModule.MODE_STANDARD
    }

    private fun syncStandardListener(context: Context) {
        val needsStandardListener = listeningTriggers.any { getTriggerMode(it) == ClipboardTriggerModule.MODE_STANDARD }
        if (!needsStandardListener) {
            unregisterStandardListener()
            lastStandardSignature = null
            return
        }

        if (listener != null) {
            return
        }

        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        lastStandardSignature = snapshotClipboardSignature(context)
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChanged(context)
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
    }

    private fun unregisterStandardListener() {
        listener?.let { activeListener ->
            try {
                clipboardManager?.removePrimaryClipChangedListener(activeListener)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "移除剪贴板监听失败: ${e.message}")
            }
        }
        listener = null
        clipboardManager = null
    }

    private fun snapshotCoreEventData(
        context: Context,
        event: VFlowCoreBridge.ClipboardStreamEvent
    ): Map<String, VObject> {
        val outputs = mutableMapOf<String, VObject>(
            "text_content" to VString(event.text)
        )

        val imageUri = event.imageUri
        if (!imageUri.isNullOrBlank()) {
            snapshotImageFromUri(context, imageUri)?.let { image ->
                outputs["image_content"] = image
            }
        }

        return outputs
    }

    private fun snapshotClipboardSignature(context: Context): String? {
        return snapshotClipboardData(context)?.first
    }

    private fun snapshotImageFromUri(context: Context, uriString: String): VImage? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val tempFile = File(StorageManager.tempDir, "clipboard_${UUID.randomUUID()}.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            VImage(tempFile.toURI().toString())
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Core 模式复制剪贴板图片失败: ${e.message}")
            null
        }
    }

    private fun snapshotClipboardData(context: Context): Pair<String, Map<String, VObject>>? {
        val manager = clipboardManager ?: return null
        if (!manager.hasPrimaryClip()) {
            return "empty" to mapOf("text_content" to VString(""))
        }

        val clipData = manager.primaryClip ?: return "empty" to mapOf("text_content" to VString(""))
        if (clipData.itemCount <= 0) {
            return "empty" to mapOf("text_content" to VString(""))
        }

        val item = clipData.getItemAt(0)
        val outputs = mutableMapOf<String, VObject>("text_content" to VString(""))
        val signatureParts = mutableListOf<String>()

        if (clipData.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            val text = item.text?.toString() ?: ""
            outputs["text_content"] = VString(text)
            signatureParts += "text:$text"
        }

        val uri = item.uri
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("image/") == true) {
                val tempFile = File(StorageManager.tempDir, "clipboard_${UUID.randomUUID()}.tmp")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outputs["image_content"] = VImage(tempFile.toURI().toString())
                    signatureParts += "image:${uri}"
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "复制剪贴板图片失败: ${e.message}")
                }
            }
        }

        if (signatureParts.isEmpty()) {
            signatureParts += "unknown:${item.coerceToText(context)}"
        }

        return signatureParts.joinToString("|") to outputs
    }
}
