package com.chaomixian.vflow.modules.triggers

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.modules.variable.BooleanVariable

class ManualTriggerModule : ActionModule {
    override val id = "vflow.trigger.manual"
    override val metadata = ActionMetadata(
        name = "手动触发",
        description = "通过点击按钮手动启动此工作流",
        iconRes = R.drawable.ic_play_arrow,
        category = "触发器"
    )

    // --- 新增：实现接口 ---
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 触发器模块也应该有输出，表示触发成功
    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable::class.java)
    )

    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // 手动触发总是成功的，并输出 true
        return ActionResult(success = true, outputs = mapOf("success" to BooleanVariable(true)))
    }
}