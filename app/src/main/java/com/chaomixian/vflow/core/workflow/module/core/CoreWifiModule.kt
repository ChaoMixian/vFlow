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
 * WiFi控制模块（Beta）。
 * 使用 vFlow Core 控制WiFi开关状态（开启/关闭/切换）。
 */
class CoreWifiModule : BaseModule() {
    companion object {
        private const val ACTION_ENABLE = "enable"
        private const val ACTION_DISABLE = "disable"
        private const val ACTION_TOGGLE = "toggle"
    }

    override val id = "vflow.core.wifi"
    override val metadata = ActionMetadata(
        name = "WiFi控制",  // Fallback
        nameStringRes = R.string.module_vflow_core_wifi_name,
        description = "使用 vFlow Core 控制WiFi开关状态（开启/关闭/切换）。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_wifi_desc,
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    private val actionOptions = listOf(ACTION_ENABLE, ACTION_DISABLE, ACTION_TOGGLE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_TOGGLE,
            options = actionOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_core_wifi_enable,
                R.string.option_vflow_core_wifi_disable,
                R.string.option_vflow_core_wifi_toggle
            ),
            legacyValueMap = mapOf(
                "开启" to ACTION_ENABLE,
                "Enable" to ACTION_ENABLE,
                "关闭" to ACTION_DISABLE,
                "Disable" to ACTION_DISABLE,
                "切换" to ACTION_TOGGLE,
                "Toggle" to ACTION_TOGGLE
            ),
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_core_wifi_action_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_set_wifi_success_name),
        OutputDefinition("enabled", "切换后的状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_wifi_state_enabled_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_set_wifi), actionPill)
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
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        // 2. 获取参数
        val actionInput = getInputs().first { it.id == "action" }
        val rawAction = context.getVariableAsString("action", ACTION_TOGGLE)
        val action = actionInput.normalizeEnumValue(rawAction) ?: rawAction

        // 3. 执行操作
        val (success, newState) = when (action) {
            ACTION_ENABLE -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_wifi_enabling)))
                val result = VFlowCoreBridge.setWifiEnabled(true)
                Pair(result, result)
            }
            ACTION_DISABLE -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_wifi_disabling)))
                val result = VFlowCoreBridge.setWifiEnabled(false)
                Pair(result, !result)
            }
            ACTION_TOGGLE -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_wifi_toggling)))
                val newState = VFlowCoreBridge.toggleWifi()
                Pair(true, newState)
            }
            else -> {
                return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                    appContext.getString(R.string.error_vflow_core_wifi_invalid_action, action)
                )
            }
        }

        return if (success) {
            val stateText = if (newState) {
                appContext.getString(R.string.msg_vflow_core_bluetooth_state_enabled)
            } else {
                appContext.getString(R.string.msg_vflow_core_bluetooth_state_disabled)
            }
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_wifi_state_changed, stateText)))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "enabled" to VBoolean(newState)
            ))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(R.string.error_vflow_core_wifi_failed)
            )
        }
    }
}
