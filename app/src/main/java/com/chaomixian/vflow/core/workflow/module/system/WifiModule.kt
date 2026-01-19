// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/WifiModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WifiModule : BaseModule() {

    override val id = "vflow.system.wifi"
    override val metadata = ActionMetadata(
        name = "Wi-Fi 设置",
        description = "开启、关闭或切换Wi-Fi状态。",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "应用与系统"
    )

    private val stateOptions = listOf("开启", "关闭", "切换")

    // 动态声明权限：如果使用 Shell (Root/Shizuku) 则需要相应权限
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        // 总是把 Shell 权限加进去，因为我们优先尝试 Shell
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "state",
            name = "状态",
            staticType = ParameterType.ENUM,
            defaultValue = "切换",
            options = stateOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val statePill = PillUtil.createPillFromParam(
            step.parameters["state"],
            getInputs().find { it.id == "state" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, statePill, " Wi-Fi")
    }

    @Suppress("DEPRECATION")
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val state = context.variables["state"] as? String ?: "切换"
        onProgress(ProgressUpdate("正在尝试 $state Wi-Fi..."))

        // 1. 优先尝试 Shell (Root 或 Shizuku)
        // 这样即使在 Android 10+ 也能静默操作
        val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val targetState = when (state) {
            "开启" -> true
            "关闭" -> false
            "切换" -> !wifiManager.isWifiEnabled
            else -> return ExecutionResult.Failure("参数错误", "无效的状态: $state")
        }
        val command = if (targetState) "svc wifi enable" else "svc wifi disable"

        onProgress(ProgressUpdate("尝试通过 Shell 执行..."))
        val shellResult = ShellManager.execShellCommand(appContext, command, ShellManager.ShellMode.AUTO)

        if (!shellResult.startsWith("Error")) {
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }

        // 2. Shell 失败，回退到原有逻辑
        DebugLogger.w("WifiModule", "Shell 执行失败: $shellResult，尝试回退到标准 API。")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 及以上版本，打开设置面板
            onProgress(ProgressUpdate("Shell 不可用，正在打开设置面板..."))
            val intent = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            // Android 10 以下版本，使用旧 API
            val success = wifiManager.setWifiEnabled(targetState)
            return ExecutionResult.Success(mapOf("success" to VBoolean(success)))
        }
    }
}