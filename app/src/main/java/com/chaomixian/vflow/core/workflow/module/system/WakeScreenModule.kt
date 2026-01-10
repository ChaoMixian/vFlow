// 文件: WakeScreenModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.os.PowerManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.delay

/**
 * "唤醒屏幕" 模块。
 * 在息屏状态下点亮屏幕，并解除非安全锁屏（如滑动解锁）。
 *
 * 注意：此模块无法绕过安全锁屏（PIN、密码、图案、指纹等）。
 * 仅适用于未设置密码的设备。
 */
class WakeScreenModule : BaseModule() {

    override val id = "vflow.system.wake_screen"
    override val metadata = ActionMetadata(
        name = "唤醒屏幕（无密码）",
        description = "点亮息屏状态的屏幕并解除滑动锁屏。仅适用于未设置密码的设备，需要 Shizuku 或 Root 权限。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME),
        OutputDefinition("screen_on", "屏幕是否点亮", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "唤醒屏幕并解除锁屏"
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在唤醒屏幕..."))

        return try {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

            val success = wakeScreenViaShell(appContext, onProgress)

            // 短暂延迟后检查屏幕状态
            delay(500)
            val isScreenOn = powerManager.isInteractive

            if (success) {
                ExecutionResult.Success(mapOf(
                    "success" to BooleanVariable(true),
                    "screen_on" to BooleanVariable(isScreenOn)
                ))
            } else {
                ExecutionResult.Failure("唤醒失败", "无法唤醒屏幕，请检查 Shizuku 或 Root 权限。")
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发生未知错误")
        }
    }

    private suspend fun wakeScreenViaShell(
        context: Context,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Boolean {
        // 使用 input keyevent 命令唤醒屏幕
        // KEYCODE_WAKEUP = 224
        val wakeResult = ShellManager.execShellCommand(context, "input keyevent 224")

        if (wakeResult.startsWith("Error:")) {
            // 尝试使用 KEYCODE_POWER (26) 作为备选
            val powerResult = ShellManager.execShellCommand(context, "input keyevent 26")
            if (powerResult.startsWith("Error:")) {
                return false
            }
        }

        onProgress(ProgressUpdate("屏幕已唤醒"))

        // 解除锁屏
        onProgress(ProgressUpdate("正在尝试解除锁屏..."))
        // 使用 Shell 命令解除锁屏
        // KEYCODE_MENU = 82，某些设备上可以解除滑动锁屏
        ShellManager.execShellCommand(context, "input keyevent 82")

        // 短暂延迟后尝试滑动解锁
        delay(300)

        // 获取屏幕尺寸并执行滑动解锁手势
        val displayResult = ShellManager.execShellCommand(context, "wm size")
        if (!displayResult.startsWith("Error:")) {
            // 解析屏幕尺寸，格式如 "Physical size: 1080x2400"
            val sizeMatch = Regex("(\\d+)x(\\d+)").find(displayResult)
            if (sizeMatch != null) {
                val width = sizeMatch.groupValues[1].toIntOrNull() ?: 1080
                val height = sizeMatch.groupValues[2].toIntOrNull() ?: 1920

                // 从屏幕底部中央向上滑动
                val startX = width / 2
                val startY = height * 4 / 5
                val endY = height / 3

                ShellManager.execShellCommand(
                    context,
                    "input swipe $startX $startY $startX $endY 300"
                )
            }
        }

        return true
    }
}
