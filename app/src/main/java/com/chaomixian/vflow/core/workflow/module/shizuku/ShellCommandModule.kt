// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/ShellCommandModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShizukuManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * "执行Shell命令" 模块。
 * 支持选择通过 Shizuku 或 Root 执行。
 */
class ShellCommandModule : BaseModule() {
    override val id = "vflow.shizuku.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",
        description = "通过 Shizuku 或 Root 执行 Shell 命令。",
        iconRes = R.drawable.rounded_terminal_24,
        category = "Shizuku"
    )

    private val modeOptions = listOf("Shizuku", "Root")

    // 动态权限声明
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        // 如果 step 为 null (如在模块管理器中)，返回所有可能的权限
        if (step == null) {
            return listOf(PermissionManager.SHIZUKU, PermissionManager.ROOT)
        }

        val mode = step.parameters["mode"] as? String ?: "Shizuku"
        return when (mode) {
            "Root" -> listOf(PermissionManager.ROOT)
            else -> listOf(PermissionManager.SHIZUKU)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = "Shizuku",
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
        OutputDefinition("result", "命令输出", TextVariable.TYPE_NAME),
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: "Shizuku"
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" }
        )
        // 在摘要中显示当前的执行模式
        return PillUtil.buildSpannable(context, "使用 $mode 执行命令 ", commandPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val mode = context.variables["mode"] as? String ?: "Shizuku"
        val rawCommand = context.variables["command"]?.toString() ?: ""
        val command = VariableResolver.resolve(rawCommand, context)

        if (command.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "要执行的命令不能为空。")
        }

        onProgress(ProgressUpdate("正在通过 $mode 执行: $command"))

        return if (mode == "Root") {
            executeRootCommand(command)
        } else {
            executeShizukuCommand(context.applicationContext, command)
        }
    }

    private suspend fun executeShizukuCommand(context: Context, command: String): ExecutionResult {
        val result = ShizukuManager.execShellCommand(context, command)
        if (result.startsWith("Error:")) {
            return ExecutionResult.Failure("Shizuku 执行失败", result)
        }
        return ExecutionResult.Success(mapOf(
            "result" to TextVariable(result),
            "success" to BooleanVariable(true)
        ))
    }

    private fun executeRootCommand(command: String): ExecutionResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val errorGobbler = StreamGobbler(process.errorStream)
            val outputGobbler = StreamGobbler(process.inputStream)

            errorGobbler.start()
            outputGobbler.start()

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val exitCode = process.waitFor()
            errorGobbler.join()
            outputGobbler.join()

            val output = outputGobbler.output
            val error = errorGobbler.output

            if (exitCode == 0) {
                ExecutionResult.Success(mapOf(
                    "result" to TextVariable(output),
                    "success" to BooleanVariable(true)
                ))
            } else {
                ExecutionResult.Failure("Root 执行失败 (Code $exitCode)", error.ifBlank { output })
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("Root 异常", e.message ?: "未知错误")
        }
    }

    // 流读取器辅助类
    private class StreamGobbler(private val inputStream: java.io.InputStream) : Thread() {
        var output = ""
        override fun run() {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream))
                val builder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    builder.append(line).append("\n")
                }
                output = builder.toString().trim()
            } catch (e: Exception) { /* ignore */ }
        }
    }
}