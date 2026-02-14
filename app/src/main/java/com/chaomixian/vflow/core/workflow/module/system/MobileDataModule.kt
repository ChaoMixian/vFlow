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

    override val id = "vflow.system.mobile_data"
    override val metadata = ActionMetadata(
        name = "移动数据",
        description = "开启或关闭移动数据",
        iconRes = R.drawable.rounded_signal_cellular_24,
        category = "应用与系统"
    )

    // 动态声明权限：需要 Root 或 Shizuku 权限执行 shell 命令
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    private val actionOptions = listOf("开启", "关闭")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = "开启",
            options = actionOptions
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
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
        val step = context.allSteps[context.currentStepIndex]
        val action = step.parameters["action"]?.toString() ?: "开启"

        val success = when (action) {
            "开启" -> {
                onProgress(ProgressUpdate("正在开启移动数据..."))
                execMobileDataCommand(context, true)
            }
            "关闭" -> {
                onProgress(ProgressUpdate("正在关闭移动数据..."))
                execMobileDataCommand(context, false)
            }
            else -> {
                return ExecutionResult.Failure("参数错误", "未知的操作类型: $action")
            }
        }

        return if (success) {
            onProgress(ProgressUpdate("移动数据${action}完成"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "移动数据操作失败")
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
