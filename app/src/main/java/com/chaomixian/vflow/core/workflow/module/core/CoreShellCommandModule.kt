package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.pill.PillFormatter
import com.chaomixian.vflow.core.pill.PillType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.services.VFlowCoreBridge.ExecMode
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 执行Shell命令模块（Core版本）。
 * 完全基于vFlow Core实现，支持Shell和Root权限级别。
 */
class CoreShellCommandModule : BaseModule() {

    override val id = "vflow.core.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",  // Fallback
        nameStringRes = R.string.module_vflow_core_shell_command_name,
        description = "通过 vFlow Core 执行Shell命令（支持Shell/Root权限）。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_shell_command_desc,
        iconRes = R.drawable.rounded_terminal_24,
        category = "Core (Beta)"
    )

    private val modeOptions = listOf("Shell权限", "Root权限", "自动")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: "自动"
        return when (mode) {
            "Root权限" -> listOf(PermissionManager.CORE_ROOT)
            "Shell权限" -> listOf(PermissionManager.CORE)
            // 自动模式下，根据用户的默认 shell 偏好设置决定
            else -> {
                val prefs = com.chaomixian.vflow.core.logging.LogManager.applicationContext.getSharedPreferences("vFlowPrefs", android.content.Context.MODE_PRIVATE)
                val defaultShellMode = prefs.getString("default_shell_mode", "shizuku")
                if (defaultShellMode == "root") {
                    listOf(PermissionManager.CORE_ROOT)
                } else {
                    listOf(PermissionManager.CORE)
                }
            }
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = modeOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_core_shell_command_mode_name
        ),
        InputDefinition(
            id = "command",
            name = "命令",
            staticType = ParameterType.STRING,
            defaultValue = "echo 'Hello from Core'",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_core_shell_command_command_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_shell_command_result_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_shell_command_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val modePill = PillFormatter.createPillFromParam(
            step.parameters["mode"],
            getInputs().find { it.id == "mode" },
            PillType.PARAMETER
        )
        val commandPill = PillFormatter.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" },
            PillType.PARAMETER
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_shell_command), modePill, "执行", commandPill)
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

        // 1. 确保 Core 已连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }

        if (!connected) {
            return ExecutionResult.Failure(
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已启动 vFlow Core。"
            )
        }

        // 2. 确定执行模式
        val execMode = when (modeStr) {
            "Root权限" -> {
                // Root 权限：需要检查 Core 是否以 Root 运行
                if (VFlowCoreBridge.privilegeMode != VFlowCoreBridge.PrivilegeMode.ROOT) {
                    return ExecutionResult.Failure(
                        "权限不足",
                        "此操作需要 Root 权限，但当前 Core 以 Shell 权限运行。"
                    )
                }
                ExecMode.ROOT
            }
            "Shell权限" -> ExecMode.SHELL
            "自动" -> ExecMode.AUTO
            else -> ExecMode.AUTO
        }

        // 3. 执行命令
        onProgress(ProgressUpdate("正在通过 vFlow Core ($modeStr) 执行: $command"))

        val result = VFlowCoreBridge.exec(command, execMode, context.applicationContext)

        // 4. 返回结果
        return if (result.isBlank()) {
            ExecutionResult.Success(mapOf(
                "result" to VString(""),
                "success" to VBoolean(true)
            ))
        } else {
            ExecutionResult.Success(mapOf(
                "result" to VString(result),
                "success" to VBoolean(true)
            ))
        }
    }
}
