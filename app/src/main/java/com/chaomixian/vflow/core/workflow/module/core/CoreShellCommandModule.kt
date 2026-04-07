package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
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
    companion object {
        private const val MODE_AUTO = "auto"
        private const val MODE_SHELL = "shell"
        private const val MODE_ROOT = "root"

        private fun normalizeMode(value: String?): String {
            return when (value) {
                MODE_ROOT, "Root权限", "Root Permission", "Root" -> MODE_ROOT
                MODE_SHELL, "Shell权限", "Shell Permission", "Shell" -> MODE_SHELL
                else -> MODE_AUTO
            }
        }
    }

    override val id = "vflow.core.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",  // Fallback
        nameStringRes = R.string.module_vflow_core_shell_command_name,
        description = "通过 vFlow Core 执行Shell命令（支持Shell/Root权限）。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_shell_command_desc,
        iconRes = R.drawable.rounded_terminal_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    private val modeOptions = listOf(MODE_SHELL, MODE_ROOT, MODE_AUTO)

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = normalizeMode(step?.parameters?.get("mode") as? String)
        return when (mode) {
            MODE_ROOT -> listOf(PermissionManager.CORE_ROOT)
            MODE_SHELL -> listOf(PermissionManager.CORE)
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
            defaultValue = MODE_AUTO,
            options = modeOptions,
            acceptsMagicVariable = false,
            optionsStringRes = listOf(
                R.string.option_vflow_core_shell_command_mode_shell,
                R.string.option_vflow_core_shell_command_mode_root,
                R.string.option_vflow_core_shell_command_mode_auto
            ),
            legacyValueMap = mapOf(
                "Shell权限" to MODE_SHELL,
                "Shell Permission" to MODE_SHELL,
                "Shell" to MODE_SHELL,
                "Root权限" to MODE_ROOT,
                "Root Permission" to MODE_ROOT,
                "Root" to MODE_ROOT,
                "自动" to MODE_AUTO,
                "Auto" to MODE_AUTO
            ),
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
        val modePill = PillUtil.createPillFromParam(
            step.parameters["mode"],
            getInputs().find { it.id == "mode" },
        )
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" },
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_core_shell_command),
            modePill,
            context.getString(R.string.summary_vflow_core_shell_command_connector),
            commandPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val modeStr = normalizeMode(context.getVariableAsString("mode", MODE_AUTO))
        val rawCommand = context.getVariableAsString("command", "")
        val command = VariableResolver.resolve(rawCommand, context)
        val appContext = context.applicationContext
        val modeLabel = when (modeStr) {
            MODE_ROOT -> appContext.getString(R.string.option_vflow_core_shell_command_mode_root)
            MODE_SHELL -> appContext.getString(R.string.option_vflow_core_shell_command_mode_shell)
            else -> appContext.getString(R.string.option_vflow_core_shell_command_mode_auto)
        }

        if (command.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_shell_command_invalid_param_title),
                appContext.getString(R.string.error_vflow_core_shell_command_empty)
            )
        }

        // 1. 确保 Core 已连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(appContext)
        }

        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        // 2. 确定执行模式
        val execMode = when (modeStr) {
            MODE_ROOT -> {
                // Root 权限：需要检查 Core 是否以 Root 运行
                if (VFlowCoreBridge.privilegeMode != VFlowCoreBridge.PrivilegeMode.ROOT) {
                    return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_core_shell_command_permission_denied),
                        appContext.getString(R.string.error_vflow_core_shell_command_root_required)
                    )
                }
                ExecMode.ROOT
            }
            MODE_SHELL -> ExecMode.SHELL
            MODE_AUTO -> ExecMode.AUTO
            else -> ExecMode.AUTO
        }

        // 3. 执行命令
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_shell_command_executing, modeLabel, command)))

        val result = VFlowCoreBridge.exec(command, execMode, appContext)

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
