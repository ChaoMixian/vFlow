// 文件: main/java/com/chaomixian/vflow/services/VFlowIME.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger

/**
 * vFlow 专用输入法服务。
 * * 原理：
 * 1. 作为一个标准的 Android 输入法存在。
 * 2. 注册一个 BroadcastReceiver 监听特定广播。
 * 3. 收到广播后，获取当前 InputConnection，直接 commitText 上屏。
 */
class VFlowIME : InputMethodService() {

    companion object {
        private const val TAG = "VFlowIME"
        const val ACTION_INPUT_TEXT = "com.chaomixian.vflow.action.IME_INPUT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_COMMIT_ACTION = "commit_action" // 可选：输入后是否执行回车/搜索等
    }

    private val inputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_INPUT_TEXT) {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return
                val commitAction = intent.getBooleanExtra(EXTRA_COMMIT_ACTION, false)

                inputText(text, commitAction)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.d(TAG, "vFlow IME Created")
        // 注册广播接收器，允许外部 (Shell/App) 发送文字给输入法
        val filter = IntentFilter(ACTION_INPUT_TEXT)
        ContextCompat.registerReceiver(this, inputReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(inputReceiver)
        DebugLogger.d(TAG, "vFlow IME Destroyed")
    }

    override fun onCreateInputView(): View {
        // 加载一个极简布局，避免遮挡屏幕
        return layoutInflater.inflate(R.layout.layout_vflow_ime, null)
    }

    private fun inputText(text: String, performEditorAction: Boolean) {
        val inputConnection = currentInputConnection ?: return

        DebugLogger.d(TAG, "IME Committing text: $text")

        // 1. 提交文本
        // newCursorPosition = 1 表示光标移动到插入文本的后面
        inputConnection.commitText(text, 1)

        // 2. (可选) 执行编辑器动作 (如搜索、发送)
        if (performEditorAction) {
            val editorInfo = currentInputEditorInfo
            if (editorInfo != null) {
                inputConnection.performEditorAction(editorInfo.actionId)
            }
        }
    }
}