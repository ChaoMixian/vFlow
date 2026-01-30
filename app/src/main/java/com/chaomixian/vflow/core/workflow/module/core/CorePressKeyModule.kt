package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.view.KeyEvent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 按键模块（Beta）。
 * 使用 vFlow Core 发送按键事件，比无障碍服务更快速稳定。
 */
class CorePressKeyModule : BaseModule() {

    override val id = "vflow.core.press_key"
    override val metadata = ActionMetadata(
        name = "按键",  // Fallback
        nameStringRes = R.string.module_vflow_core_press_key_name,
        description = "使用 vFlow Core 发送按键事件，比无障碍服务更快速稳定。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_press_key_desc,
        iconRes = R.drawable.rounded_keyboard_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "key_code",
            name = "按键代码",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 4.0, // KEYCODE_BACK
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_core_press_key_code_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val keyPill = PillUtil.createPillFromParam(
            step.parameters["key_code"],
            getInputs().find { it.id == "key_code" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_press_key), keyPill)
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
        val keyCode = (context.magicVariables["key_code"] ?: context.variables["key_code"]) as? Number

        if (keyCode == null) {
            return ExecutionResult.Failure("参数错误", "按键代码必须为数字")
        }

        val keyCodeInt = keyCode.toInt()
        val keyName = KeyEvent.keyCodeToString(keyCodeInt)

        onProgress(ProgressUpdate("正在使用 vFlow Core 发送按键: $keyName..."))

        // 3. 执行操作
        val success = VFlowCoreBridge.pressKey(keyCodeInt)

        return if (success) {
            onProgress(ProgressUpdate("按键发送成功"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 按键操作失败")
        }
    }
}
