package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 强制停止应用模块（Beta）。
 * 使用 vFlow Core 强制停止应用。
 */
class CoreForceStopAppModule : BaseModule() {

    override val id = "vflow.core.force_stop_app"
    override val metadata = ActionMetadata(
        name = "强制停止应用",  // Fallback
        nameStringRes = R.string.module_vflow_core_force_stop_app_name,
        description = "使用 vFlow Core 强制停止指定应用。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_force_stop_app_desc,
        iconRes = R.drawable.rounded_stop_circle_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "package_name",
            name = "应用包名",  // Fallback
            nameStringRes = R.string.param_vflow_core_force_stop_app_package_name_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packagePill = PillUtil.createPillFromParam(
            step.parameters["package_name"],
            getInputs().find { it.id == "package_name" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_force_stop_app), packagePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 确保 Core 连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已授予 Shizuku 或 Root 权限。"
            )
        }

        // 2. 获取参数
        val packageName = context.getVariableAsRawString("package_name")

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "应用包名不能为空")
        }

        onProgress(ProgressUpdate("正在使用 vFlow Core 强制停止应用: $packageName..."))

        // 3. 执行操作
        val success = VFlowCoreBridge.forceStopPackage(packageName)

        return if (success) {
            onProgress(ProgressUpdate("应用已强制停止"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 强制停止应用失败")
        }
    }
}
