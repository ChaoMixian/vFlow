package com.chaomixian.vflow.core.workflow.module.system

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.delay

/**
 * "唤醒并解锁屏幕" 模块。
 * 在息屏状态下点亮屏幕，并尝试完成滑动锁屏或输入 PIN/密码解锁。
 *
 * 注意：图案锁、指纹、人脸等交互式验证方式仍不支持。
 */
class WakeAndUnlockScreenModule : BaseModule() {
    companion object {
        const val INPUT_UNLOCK_PASSWORD = "unlock_password"
        private const val KEYCODE_ENTER = 66
        private const val DIGIT_KEYCODE_OFFSET = 7
    }

    override val id = "vflow.system.wake_and_unlock_screen"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_wake_and_unlock_screen_name,
        descriptionStringRes = R.string.module_vflow_system_wake_and_unlock_screen_desc,
        name = "唤醒屏幕并解锁",
        description = "点亮息屏状态的屏幕，并可选输入 PIN/密码完成解锁。图案和生物识别暂不支持，需要 Shizuku 或 Root 权限。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val uiProvider: ModuleUIProvider = WakeAndUnlockScreenModuleUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = INPUT_UNLOCK_PASSWORD,
            name = "锁屏密码",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            hint = "留空则仅尝试滑动解锁",
            nameStringRes = R.string.param_vflow_system_wake_and_unlock_screen_unlock_password_name,
            hintStringRes = R.string.hint_vflow_system_wake_and_unlock_screen_unlock_password
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_wake_screen_success_name
        ),
        OutputDefinition(
            id = "screen_on",
            name = "屏幕是否点亮",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_wake_screen_screen_on_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val unlockPassword = step.parameters[INPUT_UNLOCK_PASSWORD]?.toString().orEmpty()
        return if (unlockPassword.isNotBlank()) {
            context.getString(R.string.summary_vflow_system_wake_and_unlock_screen_with_password)
        } else {
            context.getString(R.string.summary_vflow_system_wake_and_unlock_screen)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_and_unlock_screen_running)))

        return try {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val unlockPassword = context.getVariableAsString(INPUT_UNLOCK_PASSWORD, "")

            if (unlockPassword.isNotBlank() && !isAsciiUnlockPassword(unlockPassword)) {
                return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_wake_and_unlock_screen_failed),
                    appContext.getString(R.string.error_vflow_system_wake_and_unlock_screen_password_charset)
                )
            }

            val shellSucceeded = wakeScreenViaShell(appContext, onProgress, unlockPassword)

            delay(700)
            val isScreenOn = powerManager.isInteractive
            val isLocked = keyguardManager.isKeyguardLocked

            if (shellSucceeded && isScreenOn && !isLocked) {
                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "screen_on" to VBoolean(isScreenOn)
                    )
                )
            } else if (shellSucceeded && isScreenOn) {
                val detailRes = if (unlockPassword.isBlank()) {
                    R.string.error_vflow_system_wake_and_unlock_screen_still_locked_without_password
                } else {
                    R.string.error_vflow_system_wake_and_unlock_screen_still_locked_with_password
                }
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_wake_and_unlock_screen_failed),
                    appContext.getString(detailRes)
                )
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_wake_and_unlock_screen_failed),
                    appContext.getString(R.string.error_vflow_system_wake_screen_permission_hint)
                )
            }
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_wake_and_unlock_screen_exec_failed),
                e.localizedMessage ?: "发生未知错误"
            )
        }
    }

    private suspend fun wakeScreenViaShell(
        context: Context,
        onProgress: suspend (ProgressUpdate) -> Unit,
        unlockPassword: String
    ): Boolean {
        val wakeResult = ShellManager.execShellCommand(context, "input keyevent 224")

        if (wakeResult.startsWith("Error:")) {
            val powerResult = ShellManager.execShellCommand(context, "input keyevent 26")
            if (powerResult.startsWith("Error:")) {
                return false
            }
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_and_unlock_screen_done)))
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_and_unlock_screen_unlocking)))
        ShellManager.execShellCommand(context, "input keyevent 82")

        delay(300)
        performSwipeUnlock(context)

        if (unlockPassword.isBlank()) {
            return true
        }

        delay(500)
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_and_unlock_screen_entering_password)))

        val commands = buildUnlockCommandSequence(unlockPassword)
        if (commands.isEmpty()) {
            return false
        }

        commands.forEach { command ->
            val result = ShellManager.execShellCommand(context, command)
            if (result.startsWith("Error:")) {
                return false
            }
            delay(80)
        }

        return true
    }

    private suspend fun performSwipeUnlock(context: Context) {
        val displayResult = ShellManager.execShellCommand(context, "wm size")
        val size = parseDisplaySize(displayResult) ?: return

        val startX = size.first / 2
        val startY = size.second * 4 / 5
        val endY = size.second / 3

        ShellManager.execShellCommand(
            context,
            "input swipe $startX $startY $startX $endY 300"
        )
    }

    internal fun parseDisplaySize(displayResult: String): Pair<Int, Int>? {
        if (displayResult.startsWith("Error:")) return null
        val sizeMatch = Regex("(\\d+)x(\\d+)").find(displayResult) ?: return null
        val width = sizeMatch.groupValues[1].toIntOrNull() ?: return null
        val height = sizeMatch.groupValues[2].toIntOrNull() ?: return null
        return width to height
    }

    internal fun buildUnlockCommandSequence(unlockPassword: String): List<String> {
        if (unlockPassword.isBlank()) return emptyList()

        return if (unlockPassword.all(Char::isDigit)) {
            unlockPassword.map { digit ->
                "input keyevent ${digitToKeyCode(digit)}"
            } + "input keyevent $KEYCODE_ENTER"
        } else {
            listOf(
                buildShellTextCommand(unlockPassword),
                "input keyevent $KEYCODE_ENTER"
            )
        }
    }

    internal fun buildShellTextCommand(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")
            .replace(" ", "%s")
        return "input text \"$escaped\""
    }

    internal fun isAsciiUnlockPassword(password: String): Boolean {
        return password.all { it.code in 32..126 }
    }

    private fun digitToKeyCode(digit: Char): Int {
        return DIGIT_KEYCODE_OFFSET + digit.digitToInt()
    }
}
