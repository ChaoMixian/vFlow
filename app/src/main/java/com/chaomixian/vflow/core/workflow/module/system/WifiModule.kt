// 文件: WifiModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
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
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val state = step.parameters["state"] as? String ?: "切换"
        val statePill = PillUtil.Pill(state, false, "state", true)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onProgress(ProgressUpdate("您的安卓版本需要手动操作, 正在打开设置面板..."))
            val intent = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        } else {
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val targetState = when (state) {
                "开启" -> true
                "关闭" -> false
                "切换" -> !wifiManager.isWifiEnabled
                else -> return ExecutionResult.Failure("参数错误", "无效的状态: $state")
            }
            val success = wifiManager.setWifiEnabled(targetState)
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(success)))
        }
    }
}