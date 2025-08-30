package com.chaomixian.vflow.modules.triggers

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

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable::class.java)
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        // 手动触发总是成功的
        onProgress(ProgressUpdate("工作流被手动触发"))
        return ActionResult(success = true, outputs = mapOf("success" to BooleanVariable(true)))
    }
}