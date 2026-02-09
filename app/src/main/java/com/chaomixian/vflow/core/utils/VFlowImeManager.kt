// 文件: main/java/com/chaomixian/vflow/core/utils/VFlowImeManager.kt
package com.chaomixian.vflow.core.utils

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.VFlowIME
import kotlinx.coroutines.delay

/**
 * 管理 vFlow 输入法的切换和输入操作。
 */
object VFlowImeManager {
    private const val TAG = "VFlowImeManager"

    // vFlow 输入法的完整 ID (包名/类名)
    private const val VFLOW_IME_ID = "com.chaomixian.vflow/.services.VFlowIME"

    /**
     * 使用 vFlow IME 输入文本。
     * 自动处理：备份当前IME -> 切换vFlow IME -> 发送文本 -> 恢复IME。
     *
     * @param context 上下文
     * @param text 要输入的文本
     * @param keyAction 输入后的按键操作 ("none", "enter", "tab", "next")
     */
    suspend fun inputText(context: Context, text: String, keyAction: String = "none"): Boolean {
        if (!ShellManager.isShizukuActive(context) && !ShellManager.isRootAvailable()) {
            DebugLogger.e(TAG, "IME 输入需要 Shizuku 或 Root 权限。")
            return false
        }

        // 1. 获取当前默认输入法
        val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        DebugLogger.d(TAG, "当前输入法: $currentIme")

        // 2. 如果当前不是 vFlow IME，则切换
        if (currentIme != VFLOW_IME_ID) {
            val enabledMethods = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)

            // 2.1 确保 vFlow IME 已启用 (如果没有，尝试通过 Shell 启用它)
            if (!enabledMethods.contains(VFLOW_IME_ID)) {
                DebugLogger.d(TAG, "vFlow IME 未启用，尝试启用...")
                // 注意：直接修改 enabled_input_methods 比较危险，更安全的是让用户去设置里开一次
                // 但在 Shell 模式下，我们可以尝试 append
                val newEnabled = "$enabledMethods:$VFLOW_IME_ID"
                ShellManager.execShellCommand(context, "settings put secure ${Settings.Secure.ENABLED_INPUT_METHODS} \"$newEnabled\"", ShellManager.ShellMode.AUTO)
            }

            // 2.2 切换到 vFlow IME
            DebugLogger.d(TAG, "切换到 vFlow IME...")
            ShellManager.execShellCommand(context, "ime set $VFLOW_IME_ID", ShellManager.ShellMode.AUTO)
            delay(500) // 等待系统切换
        }

        // 将中文转为 Base64 字符串 (NO_WRAP 避免换行)
        val base64Text = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // 使用 --es 指定新的 EXTRA_TEXT_BASE64 和 EXTRA_KEY_ACTION 参数
        val cmd = "am broadcast -a ${VFlowIME.ACTION_INPUT_TEXT} --es ${VFlowIME.EXTRA_TEXT_BASE64} \"$base64Text\" --es ${VFlowIME.EXTRA_KEY_ACTION} \"$keyAction\""

        DebugLogger.d(TAG, "发送输入广播(Base64): $base64Text (原文本: $text, keyAction: $keyAction)")

        val result = ShellManager.execShellCommand(context, cmd, ShellManager.ShellMode.AUTO)

        val success = !result.startsWith("Error")

        // 4. 给一点时间让文字上屏
        delay(300)

        // 5. 恢复原来的输入法 (如果之前切过)
        if (currentIme != VFLOW_IME_ID && !currentIme.isNullOrBlank()) {
            DebugLogger.d(TAG, "恢复原输入法: $currentIme")
            ShellManager.execShellCommand(context, "ime set $currentIme", ShellManager.ShellMode.AUTO)
        }

        return success
    }

    /**
     * 检查是否已经启用了 vFlow 输入法 (在系统设置中勾选)
     */
    fun isImeEnabled(context: Context): Boolean {
        val enabledMethods = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        return enabledMethods?.contains(VFLOW_IME_ID) == true
    }
}