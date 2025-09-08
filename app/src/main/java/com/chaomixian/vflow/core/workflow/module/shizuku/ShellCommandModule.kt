// 文件路径: src/main/java/com/chaomixian/vflow/core/workflow/module/shizuku/ShellCommandModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShizukuManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "执行Shell命令" 模块 (最终版)。
 * 通过 ShizukuManager 执行 Shell 命令。
 */
class ShellCommandModule : BaseModule() {
    override val id = "vflow.shizuku.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",
        description = "通过 Shizuku 执行 Shell 命令。",
        iconRes = R.drawable.rounded_terminal_24,
        category = "Shizuku"
    )

    override val requiredPermissions = listOf(PermissionManager.SHIZUKU)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "command",
            name = "命令",
            staticType = ParameterType.STRING,
            defaultValue = "echo 'Hello from Shizuku'",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", TextVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" }
        )
        return PillUtil.buildSpannable(context, "执行命令 ", commandPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val command = (context.magicVariables["command"] as? TextVariable)?.value
            ?: context.variables["command"] as? String

        if (command.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "要执行的命令不能为空。")
        }

        onProgress(ProgressUpdate("正在通过 Shizuku 执行: $command"))

        val result = ShizukuManager.execShellCommand(context.applicationContext, command)

        if (result.startsWith("Error:")) {
            return ExecutionResult.Failure("Shizuku 执行失败", result)
        }

        onProgress(ProgressUpdate("命令执行完成"))
        return ExecutionResult.Success(mapOf("result" to TextVariable(result)))
    }
}