// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/ShellCommandModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "执行Shell命令" 模块。
 * 支持通过 自动/Shizuku/Root 模式执行。
 */
class ShellCommandModule : BaseModule() {
    override val id = "vflow.shizuku.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_shell_command_name,
        description = "通过 Shell 执行命令 (支持 Root/Shizuku)。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_shell_command_desc,
        iconRes = R.drawable.rounded_terminal_24,
        category = "Shizuku"
    )

    private val modeOptions = listOf("自动", "Shizuku", "Root")

    // 动态权限声明
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: "自动"
        return when (mode) {
            "Root" -> listOf(PermissionManager.ROOT)
            "Shizuku" -> listOf(PermissionManager.SHIZUKU)
            // 自动模式下，根据全局设置返回
            else -> ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = modeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "command",
            name = "命令",
            staticType = ParameterType.STRING,
            defaultValue = "echo 'Hello'",
            acceptsMagicVariable = true,
            supportsRichText = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: "自动"
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_shell, mode), commandPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val modeStr = context.getVariableAsString("mode", "自动")
        val rawCommand = context.getVariableAsString("command", "")
        val command = VariableResolver.resolve(rawCommand, context)

        if (command.isBlank()) {
            return ExecutionResult.Failure("参数错误", "要执行的命令不能为空。")
        }

        val mode = when (modeStr) {
            "Root" -> ShellManager.ShellMode.ROOT
            "Shizuku" -> ShellManager.ShellMode.SHIZUKU
            else -> ShellManager.ShellMode.AUTO
        }

        onProgress(ProgressUpdate("正在通过 $modeStr 执行: $command"))

        val result = ShellManager.execShellCommand(context.applicationContext, command, mode)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure("执行失败", result)
        } else {
            ExecutionResult.Success(mapOf(
                "result" to VString(result),
                "success" to VBoolean(true)
            ))
        }
    }
}