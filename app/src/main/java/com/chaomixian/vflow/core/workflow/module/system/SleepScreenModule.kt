// 文件: SleepScreenModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.os.PowerManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.delay

/**
 * "息屏" 模块。
 * 关闭屏幕，使设备进入息屏状态。
 * 需要 Shizuku 或 Root 权限。
 */
class SleepScreenModule : BaseModule() {

    override val id = "vflow.system.sleep_screen"
    override val metadata = ActionMetadata(
        name = "息屏",
        description = "关闭屏幕，使设备进入息屏状态。需要 Shizuku 或 Root 权限。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("screen_off", "屏幕是否关闭", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "关闭屏幕"
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在关闭屏幕..."))

        return try {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

            val success = sleepScreenViaShell(appContext, onProgress)

            // 短暂延迟后检查屏幕状态
            delay(500)
            val isScreenOff = !powerManager.isInteractive

            if (success) {
                ExecutionResult.Success(mapOf(
                    "success" to VBoolean(true),
                    "screen_off" to VBoolean(isScreenOff)
                ))
            } else {
                ExecutionResult.Failure("息屏失败", "无法关闭屏幕，请检查 Shizuku 或 Root 权限。")
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("执行失败", e.localizedMessage ?: "发生未知错误")
        }
    }

    private suspend fun sleepScreenViaShell(
        context: Context,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Boolean {
        // 使用 input keyevent 命令关闭屏幕
        // KEYCODE_SLEEP = 223
        val sleepResult = ShellManager.execShellCommand(context, "input keyevent 223")

        if (sleepResult.startsWith("Error:")) {
            // 尝试使用 KEYCODE_POWER (26) 作为备选（如果屏幕是亮的，按电源键会关闭屏幕）
            val powerResult = ShellManager.execShellCommand(context, "input keyevent 26")
            if (powerResult.startsWith("Error:")) {
                return false
            }
        }

        onProgress(ProgressUpdate("屏幕已关闭"))
        return true
    }
}
