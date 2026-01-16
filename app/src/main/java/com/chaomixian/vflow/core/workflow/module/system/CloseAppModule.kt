// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/CloseAppModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “关闭应用”模块。
 * 强制停止指定的应用程序 (需要 Shizuku 或 Root)。
 */
class CloseAppModule : BaseModule() {

    override val id = "vflow.system.close_app"
    override val metadata = ActionMetadata(
        name = "关闭应用",
        description = "强制停止指定的应用程序 (需要 Shizuku 或 Root)。",
        iconRes = R.drawable.rounded_close_small_24,
        category = "应用与系统"
    )

    // 复用应用选择器的 UI 逻辑 (AppStartTriggerUIProvider 会自动隐藏不需要的"事件"选项)
    override val uiProvider: ModuleUIProvider = com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerUIProvider()

    // 动态声明权限：需要 Shell 权限
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "packageName",
            name = "应用包名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true
        ),
        // 定义 activityName 只是为了兼容 AppPicker 的返回结果，设为隐藏
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            isHidden = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packageName = step.parameters["packageName"] as? String

        if (packageName.isNullOrEmpty()) {
            return "选择要关闭的应用"
        }

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // 如果找不到应用，显示包名
        }

        return PillUtil.buildSpannable(context,
            "强制停止 ",
            PillUtil.Pill(appName, "packageName")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 支持魔法变量 (例如从之前的步骤获取包名)
        val packageName = (context.magicVariables["packageName"] as? TextVariable)?.value
            ?: context.variables["packageName"] as? String

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "应用包名不能为空。")
        }

        onProgress(ProgressUpdate("正在停止应用: $packageName"))

        // 执行 Shell 命令
        val command = "am force-stop $packageName"
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.AUTO)

        if (result.startsWith("Error")) {
            return ExecutionResult.Failure("执行失败", "无法停止应用: $result")
        }

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}