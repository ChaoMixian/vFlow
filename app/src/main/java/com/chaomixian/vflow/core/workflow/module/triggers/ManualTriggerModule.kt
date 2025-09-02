package com.chaomixian.vflow.modules.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.BooleanVariable

class ManualTriggerModule : BaseModule() {
    override val id = "vflow.trigger.manual"
    override val metadata = ActionMetadata(
        name = "手动触发",
        description = "通过点击按钮手动启动此工作流",
        iconRes = R.drawable.ic_play_arrow,
        category = "触发器"
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return metadata.name
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("工作流被手动触发"))
        return ExecutionResult.Success(outputs = mapOf("success" to BooleanVariable(true)))
    }
}