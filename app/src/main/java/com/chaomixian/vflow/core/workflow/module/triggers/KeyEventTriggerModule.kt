// 文件: KeyEventTriggerModule.kt
// 描述: 定义了当指定物理按键被按下时触发工作流的模块。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class KeyEventTriggerModule : BaseModule() {

    override val id = "vflow.trigger.key_event"
    override val metadata = ActionMetadata(
        name = "按键触发",
        description = "当指定的物理按键被按下时，触发此工作流（需要Shizuku）。",
        iconRes = R.drawable.rounded_keyboard_24,
        category = "触发器"
    )

    // 需要Shizuku权限来监听输入设备
    override val requiredPermissions = listOf(PermissionManager.SHIZUKU)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "device",
            name = "输入设备",
            staticType = ParameterType.STRING,
            defaultValue = "/dev/input/event0",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "key_code",
            name = "按键码",
            staticType = ParameterType.STRING,
            defaultValue = "BTN_TRIGGER_HAPPY32",
            acceptsMagicVariable = false
        ),
        InputDefinition( // [修改] 更新操作类型
            id = "action_type",
            name = "操作类型",
            staticType = ParameterType.ENUM,
            defaultValue = "单击", // 默认使用更安全的“单击”
            options = listOf("单击", "双击", "长按", "短按 (立即触发)"),
            acceptsMagicVariable = false
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val device = step.parameters["device"] as? String
        val keyCode = step.parameters["key_code"] as? String
        val actionType = step.parameters["action_type"] as? String ?: "单击"

        if (device.isNullOrEmpty() || keyCode.isNullOrEmpty()) {
            return "配置按键触发器"
        }
        val keyCodePill = PillUtil.Pill(keyCode, false, "key_code")
        val actionTypePill = PillUtil.Pill(actionType.replace(" (立即触发)", ""), false, "action_type", isModuleOption = true)

        return PillUtil.buildSpannable(context, "当 ", actionTypePill, " ", keyCodePill, " 键时")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 触发器模块的执行逻辑由系统服务处理，此处仅需成功返回即可
        onProgress(ProgressUpdate("按键事件已触发"))
        return ExecutionResult.Success()
    }
}