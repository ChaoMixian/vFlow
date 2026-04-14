// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/CloseAppModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.app_picker.AppUserSupport
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “关闭应用”模块。
 * 强制停止指定的应用程序 (需要 Shizuku 或 Root)。
 */
class CloseAppModule : BaseModule() {

    override val id = "vflow.system.close_app"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_close_app_name,
        descriptionStringRes = R.string.module_vflow_system_close_app_desc,
        name = "关闭应用",  // Fallback
        description = "强制停止指定的应用程序 (需要 Shizuku 或 Root)",  // Fallback
        iconRes = R.drawable.rounded_close_small_24,
        category = "应用与系统",
        categoryId = "device"
    )

    // 使用专门的 LaunchAppUIProvider（只需要选择单个应用）
    override val uiProvider: ModuleUIProvider = LaunchAppUIProvider()

    // 动态声明权限：需要 Shell 权限
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "packageName",
            name = "应用包名",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true
        ),
        // 定义 activityName 只是为了兼容 AppPicker 的返回结果，设为隐藏
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            isHidden = true
        ),
        InputDefinition(
            id = "userId",
            name = "用户 ID",
            staticType = ParameterType.NUMBER,
            defaultValue = null,
            acceptsMagicVariable = false,
            isHidden = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packageName = step.parameters["packageName"] as? String
        val userId = (step.parameters["userId"] as? Number)?.toInt()

        if (packageName.isNullOrEmpty()) {
            return context.getString(R.string.summary_close_app_select)
        }

        val appName = AppUserSupport.loadAppLabel(context, packageName, userId) ?: packageName
        val displayName = if (userId != null && userId != AppUserSupport.getCurrentUserId()) {
            "$appName (${AppUserSupport.getUserLabel(context, userId)})"
        } else {
            appName
        }

        val prefixResId = R.string.summary_close_app_prefix
        val prefix = if (prefixResId != 0) context.getString(prefixResId) else "强制停止"

        return PillUtil.buildSpannable(context,
            "$prefix ",
            PillUtil.Pill(displayName, "packageName")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 现在 variables 是 Map<String, VObject>，统一使用 getVariableAsString 获取
        val packageName = context.getVariableAsString("packageName", "")
        val userId = context.getVariableAsInt("userId")

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_close_app_empty_package),
                appContext.getString(R.string.error_vflow_system_close_app_empty_package_detail)
            )
        }

        onProgress(ProgressUpdate("正在停止应用: $packageName"))

        // 执行 Shell 命令
        val userArg = if (userId != null) " --user $userId" else ""
        val command = "am force-stop$userArg $packageName"
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.AUTO)

        if (result.startsWith("Error")) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_close_app_execution_failed),
                "无法停止应用: $result"
            )
        }

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
