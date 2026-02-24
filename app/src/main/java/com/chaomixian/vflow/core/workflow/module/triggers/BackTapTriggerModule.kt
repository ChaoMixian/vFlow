// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/BackTapTriggerModule.kt
// 描述: 轻敲背面触发器模块，支持双击和三击触发
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BackTapTriggerModule : BaseModule() {

    override val id = "vflow.trigger.backtap"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_backtap_name,
        descriptionStringRes = R.string.module_vflow_trigger_backtap_desc,
        name = "轻敲背面触发",  // Fallback
        description = "通过敲击手机背面触发工作流（双击/三击）",  // Fallback
        iconRes = R.drawable.rounded_horizontal_align_bottom_24,
        category = "触发器"
    )

    companion object {
        const val MODE_DOUBLE_TAP = "双击"
        const val MODE_TRIPLE_TAP = "三击"
    }

    // 自定义 UIProvider，用于显示模块配置按钮
    override val uiProvider: ModuleUIProvider = BackTapTriggerModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("mode", "模式", ParameterType.ENUM, MODE_DOUBLE_TAP, options = listOf(MODE_DOUBLE_TAP, MODE_TRIPLE_TAP), nameStringRes = R.string.param_vflow_trigger_backtap_mode_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_DOUBLE_TAP
        val modePill = PillUtil.Pill(
            mode,
            "mode",
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, "轻敲背面 ", modePill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("轻敲背面触发器已触发"))
        return ExecutionResult.Success()
    }
}
