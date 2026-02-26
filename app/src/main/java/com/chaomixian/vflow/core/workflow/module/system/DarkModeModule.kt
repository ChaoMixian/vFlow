// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/DarkModeModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 深色模式切换模块
 * 支持切换系统深色模式：自动/深色/浅色
 * 需要 Shizuku 或 Root 权限
 */
class DarkModeModule : BaseModule() {

    override val id = "vflow.system.darkmode"

    override val metadata = ActionMetadata(
        name = "深色模式",
        nameStringRes = R.string.module_vflow_system_darkmode_name,
        description = "切换系统的深色/浅色模式（自动/深色/浅色）",
        descriptionStringRes = R.string.module_vflow_system_darkmode_desc,
        iconRes = R.drawable.rounded_contrast_24,
        category = "应用与系统"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(appContext)
    }

    // 序列化值使用与语言无关的标识符
    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_DARK = "dark"
        const val MODE_LIGHT = "light"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            nameStringRes = R.string.param_vflow_system_darkmode_mode_name,
            staticType = ParameterType.ENUM,
            defaultValue = MODE_AUTO,
            options = listOf(MODE_AUTO, MODE_DARK, MODE_LIGHT),
            optionsStringRes = listOf(
                R.string.option_vflow_system_darkmode_auto,
                R.string.option_vflow_system_darkmode_dark,
                R.string.option_vflow_system_darkmode_light
            ),
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_darkmode_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_AUTO

        // 序列化值转换为本地化显示文本
        val displayText = when (mode) {
            MODE_DARK -> context.getString(R.string.option_vflow_system_darkmode_dark)
            MODE_LIGHT -> context.getString(R.string.option_vflow_system_darkmode_light)
            else -> context.getString(R.string.option_vflow_system_darkmode_auto)
        }

        val modePill = PillUtil.Pill(displayText, "mode", isModuleOption = true)
        return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", modePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val mode = context.getVariable("mode").asString() ?: MODE_AUTO

        val command = when (mode) {
            MODE_DARK -> "cmd uimode night yes"
            MODE_LIGHT -> "cmd uimode night no"
            MODE_AUTO -> "cmd uimode night auto"
            else -> null
        }

        if (command == null) {
            return ExecutionResult.Failure(
                "参数错误",
                "不支持的模式: $mode"
            )
        }

        // 获取本地化的模式名称用于提示
        val modeDisplayName = when (mode) {
            MODE_DARK -> context.applicationContext.getString(R.string.option_vflow_system_darkmode_dark)
            MODE_LIGHT -> context.applicationContext.getString(R.string.option_vflow_system_darkmode_light)
            else -> context.applicationContext.getString(R.string.option_vflow_system_darkmode_auto)
        }

        onProgress(ProgressUpdate("正在切换到${modeDisplayName}模式..."))

        val result = ShellManager.execShellCommand(context.applicationContext, command)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure("切换失败", result)
        } else {
            onProgress(ProgressUpdate("已切换到${modeDisplayName}模式"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }
    }
}
