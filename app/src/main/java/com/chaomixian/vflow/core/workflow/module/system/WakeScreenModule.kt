package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.os.PowerManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.delay

/**
 * "唤醒屏幕" 模块。
 * 点亮息屏状态的屏幕，不处理锁屏界面的解锁动作。
 * 需要 Shizuku 或 Root 权限。
 */
class WakeScreenModule : BaseModule() {

    override val id = "vflow.system.wake_screen"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_wake_screen_name,
        descriptionStringRes = R.string.module_vflow_system_wake_screen_desc,
        name = "唤醒屏幕",
        description = "点亮息屏状态的屏幕，不执行解锁操作。需要 Shizuku 或 Root 权限。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs() = emptyList<com.chaomixian.vflow.core.module.InputDefinition>()

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
        return context.getString(R.string.summary_vflow_system_wake_screen)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_screen_running)))

        return try {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val success = wakeScreenViaShell(appContext, onProgress)

            delay(500)
            val isScreenOn = powerManager.isInteractive

            if (success) {
                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "screen_on" to VBoolean(isScreenOn)
                    )
                )
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_wake_screen_failed),
                    appContext.getString(R.string.error_vflow_system_wake_screen_permission_hint)
                )
            }
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_wake_screen_exec_failed),
                e.localizedMessage ?: "发生未知错误"
            )
        }
    }

    private suspend fun wakeScreenViaShell(
        context: Context,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Boolean {
        val wakeResult = ShellManager.execShellCommand(context, "input keyevent 224")

        if (wakeResult.startsWith("Error:")) {
            val powerResult = ShellManager.execShellCommand(context, "input keyevent 26")
            if (powerResult.startsWith("Error:")) {
                return false
            }
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wake_screen_done)))
        return true
    }
}
