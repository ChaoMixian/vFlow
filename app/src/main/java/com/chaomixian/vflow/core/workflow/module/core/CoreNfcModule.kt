package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFC控制模块（Beta）。
 * 使用 vFlow Core 控制NFC开关状态（开启/关闭/切换）。
 */
class CoreNfcModule : BaseModule() {

    override val id = "vflow.core.nfc"
    override val metadata = ActionMetadata(
        name = "NFC控制",  // Fallback
        nameStringRes = R.string.module_vflow_core_nfc_name,
        description = "使用 vFlow Core 控制NFC开关状态（开启/关闭/切换）。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_nfc_desc,
        iconRes = R.drawable.rounded_nfc_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    private val actionOptions = listOf("开启", "关闭", "切换")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = "切换",
            options = actionOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_core_nfc_action_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("enabled", "切换后的状态", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, actionPill, context.getString(R.string.summary_vflow_core_set_nfc))
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
        val step = context.allSteps[context.currentStepIndex]
        val action = step.parameters["action"]?.toString() ?: "切换"

        // 3. 执行操作
        val (success, newState) = when (action) {
            "开启" -> {
                onProgress(ProgressUpdate("正在使用 vFlow Core 开启NFC..."))
                val result = VFlowCoreBridge.setNfcEnabled(true)
                Pair(result, result)
            }
            "关闭" -> {
                onProgress(ProgressUpdate("正在使用 vFlow Core 关闭NFC..."))
                val result = VFlowCoreBridge.setNfcEnabled(false)
                Pair(result, !result)
            }
            "切换" -> {
                onProgress(ProgressUpdate("正在使用 vFlow Core 切换NFC..."))
                val newState = VFlowCoreBridge.toggleNfc()
                Pair(true, newState)
            }
            else -> {
                return ExecutionResult.Failure("参数错误", "未知的操作类型: $action")
            }
        }

        return if (success) {
            val stateText = if (newState) "已开启" else "已关闭"
            onProgress(ProgressUpdate("NFC$stateText"))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "enabled" to VBoolean(newState)
            ))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core NFC操作失败")
        }
    }
}
