package com.chaomixian.vflow.core.workflow.module.triggers // Corrected package

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
// Assuming BooleanVariable is now in core.workflow.module.variable based on VariableModule.kt's new package
import com.chaomixian.vflow.core.workflow.module.data.BooleanVariable

class ManualTriggerModule : BaseModule() { // BaseModule is from com.chaomixian.vflow.core.module
    override val id = "vflow.trigger.manual"
    override val metadata = ActionMetadata(
        name = "手动触发",
        description = "通过点击按钮手动启动此工作流",
        iconRes = R.drawable.rounded_play_arrow_24,
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