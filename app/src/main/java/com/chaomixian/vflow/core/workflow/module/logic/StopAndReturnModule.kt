// main/java/com/chaomixian/vflow/core/workflow/module/logic/StopAndReturnModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "停止并返回" 模块。
 * 立即终止当前工作流（或子工作流）的执行，并返回一个值。
 */
class StopAndReturnModule : BaseModule() {
    override val id = "vflow.logic.return"
    override val metadata = ActionMetadata(
        name = "停止并返回",
        description = "停止当前工作流的执行并返回一个值。",
        iconRes = R.drawable.rounded_output_24,
        category = "逻辑控制"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "value",
            name = "返回值",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val valuePill = PillUtil.createPillFromParam(
            step.parameters["value"],
            getInputs().find { it.id == "value" }
        )
        return PillUtil.buildSpannable(context, "返回 ", valuePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("准备返回值..."))
        val returnValue = context.magicVariables["value"] ?: context.variables["value"]
        return ExecutionResult.Signal(ExecutionSignal.Return(returnValue))
    }
}