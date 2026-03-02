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
import android.util.Base64 // 引入 Base64

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
        const val EXTRA_TEXT_BASE64 = "text_base64"
        const val EXTRA_COMMIT_ACTION = "commit_action"
        const val EXTRA_KEY_ACTION = "key_action"

        // 按键操作常量
        const val KEY_ACTION_NONE = "none"
        const val KEY_ACTION_ENTER = "enter"
        const val KEY_ACTION_TAB = "tab"
        const val KEY_ACTION_NEXT = "next"
    }

    private val inputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_INPUT_TEXT) {
                var text = intent.getStringExtra(EXTRA_TEXT)
                val commitAction = intent.getBooleanExtra(EXTRA_COMMIT_ACTION, false)
                val keyAction = intent.getStringExtra(EXTRA_KEY_ACTION) ?: KEY_ACTION_NONE

                val base64Text = intent.getStringExtra(EXTRA_TEXT_BASE64)
                if (!base64Text.isNullOrEmpty()) {
                    try {
                        val bytes = Base64.decode(base64Text, Base64.DEFAULT)
                        text = String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "Base64 解码失败: $base64Text", e)
                    }
                }

                if (text != null) {
                    inputText(text, commitAction, keyAction)
                }
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

    override fun onCreateInputView(): View? {
        // 返回 null，不显示任何输入法 UI
        return null
    }

    private fun inputText(text: String, performEditorAction: Boolean, keyAction: String) {
        val inputConnection = currentInputConnection ?: return

        DebugLogger.d(TAG, "IME Committing text: $text, keyAction: $keyAction")

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

        // 3. 执行按键操作
        when (keyAction) {
            KEY_ACTION_ENTER -> sendKeyEvent(inputConnection, android.view.KeyEvent.KEYCODE_ENTER)
            KEY_ACTION_TAB -> sendKeyEvent(inputConnection, android.view.KeyEvent.KEYCODE_TAB)
            KEY_ACTION_NEXT -> {
                // 移动焦点到下一个，尝试 ACTION_DOWN + ACTION_UP
                sendKeyEvent(inputConnection, android.view.KeyEvent.KEYCODE_TAB)
            }
            KEY_ACTION_NONE -> {
                // 不做任何操作
            }
        }
    }

    /**
     * 发送按键事件
     */
    private fun sendKeyEvent(inputConnection: InputConnection, keyCode: Int) {
        try {
            val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)

            inputConnection.sendKeyEvent(downEvent)
            inputConnection.sendKeyEvent(upEvent)

            DebugLogger.d(TAG, "Sent key event: $keyCode")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to send key event: $keyCode", e)
        }
    }
}