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
    companion object {
        private const val MODE_AUTO = "auto"
        private const val MODE_SHIZUKU = "shizuku"
        private const val MODE_ROOT = "root"
    }

    override val id = "vflow.shizuku.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_shell_command_name,
        description = "通过 Shell 执行命令 (支持 Root/Shizuku)。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_shell_command_desc,
        iconRes = R.drawable.rounded_terminal_24,
        category = "Shizuku",
        categoryId = "shizuku"
    )

    private val modeOptions = listOf(MODE_AUTO, MODE_SHIZUKU, MODE_ROOT)

    // 动态权限声明
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: MODE_AUTO
        return when (mode) {
            MODE_ROOT -> listOf(PermissionManager.ROOT)
            MODE_SHIZUKU -> listOf(PermissionManager.SHIZUKU)
            // 自动模式下，根据全局设置返回
            else -> ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_AUTO,
            options = modeOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_shizuku_shell_command_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_shell_command_mode_auto,
                R.string.option_vflow_shizuku_shell_command_mode_shizuku,
                R.string.option_vflow_shizuku_shell_command_mode_root
            ),
            legacyValueMap = mapOf(
                "自动" to MODE_AUTO,
                "Auto" to MODE_AUTO,
                "Shizuku" to MODE_SHIZUKU,
                "Root" to MODE_ROOT
            )
        ),
        InputDefinition(
            id = "command",
            name = "命令",
            staticType = ParameterType.STRING,
            defaultValue = "echo 'Hello'",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_shizuku_shell_command_command_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_shizuku_shell_command_result_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_shizuku_shell_command_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_AUTO
        val modeDisplay = getInputs()
            .find { it.id == "mode" }
            ?.let { input ->
                val index = input.options.indexOf(mode)
                if (index >= 0 && index < input.optionsStringRes.size) {
                    context.getString(input.optionsStringRes[index])
                } else {
                    mode
                }
            } ?: mode
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_shell, modeDisplay), commandPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val modeStr = context.getVariableAsString("mode", MODE_AUTO)
        val rawCommand = context.getVariableAsString("command", "")
        val command = VariableResolver.resolve(rawCommand, context)

        if (command.isBlank()) {
            return ExecutionResult.Failure("参数错误", "要执行的命令不能为空。")
        }

        val mode = when (modeStr) {
            MODE_ROOT -> ShellManager.ShellMode.ROOT
            MODE_SHIZUKU -> ShellManager.ShellMode.SHIZUKU
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
