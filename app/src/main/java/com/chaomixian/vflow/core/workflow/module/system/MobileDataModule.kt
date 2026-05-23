package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 移动数据控制模块
 * 通过 shell 命令控制移动数据开关
 */
class MobileDataModule : BaseModule() {
    companion object {
        private const val ACTION_ENABLE = "enable"
        private const val ACTION_DISABLE = "disable"
        private val ACTION_LEGACY_MAP = mapOf(
            "开启" to ACTION_ENABLE,
            "关闭" to ACTION_DISABLE
        )
    }

    override val id = "vflow.system.mobile_data"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_mobile_data_name,
        descriptionStringRes = R.string.module_vflow_system_mobile_data_desc,
        name = "移动数据",
        description = "开启或关闭移动数据",
        iconRes = R.drawable.rounded_signal_cellular_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.HIGH,
        directToolDescription = "Enable or disable mobile data.",
        workflowStepDescription = "Change mobile data state.",
        requiredInputIds = setOf("action"),
    )

    // 动态声明权限：需要 Root 或 Shizuku 权限执行 shell 命令
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    private val actionOptions = listOf(ACTION_ENABLE, ACTION_DISABLE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_ENABLE,
            options = actionOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_system_mobile_data_enable,
                R.string.option_vflow_system_mobile_data_disable
            ),
            legacyValueMap = ACTION_LEGACY_MAP,
            nameStringRes = R.string.param_vflow_system_mobile_data_action_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_mobile_data_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_system_mobile_data),
            actionPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val actionInput = getInputs().first { it.id == "action" }
        val rawAction = context.getVariableAsString("action", ACTION_ENABLE)
        val action = actionInput.normalizeEnumValue(rawAction) ?: rawAction

        val success = when (action) {
            ACTION_ENABLE -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_mobile_data_enabling)))
                execMobileDataCommand(context, true)
            }
            ACTION_DISABLE -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_mobile_data_disabling)))
                execMobileDataCommand(context, false)
            }
            else -> {
                return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_mobile_data_param_error),
                    appContext.getString(R.string.error_vflow_system_mobile_data_unknown_action, action)
                )
            }
        }

        return if (success) {
            val actionLabel = when (action) {
                ACTION_DISABLE -> appContext.getString(R.string.option_vflow_system_mobile_data_disable)
                else -> appContext.getString(R.string.option_vflow_system_mobile_data_enable)
            }
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_mobile_data_done, actionLabel)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_mobile_data_execution_failed),
                appContext.getString(R.string.error_vflow_system_mobile_data_failed)
            )
        }
    }

    /**
     * 执行移动数据控制命令
     * @return true=成功，false=失败
     */
    private suspend fun execMobileDataCommand(context: ExecutionContext, enable: Boolean): Boolean {
        val cmd = if (enable) "svc data enable" else "svc data disable"
        val result = ShellManager.execShellCommand(context.applicationContext, cmd)
        return !result.startsWith("Error:")
    }
}
