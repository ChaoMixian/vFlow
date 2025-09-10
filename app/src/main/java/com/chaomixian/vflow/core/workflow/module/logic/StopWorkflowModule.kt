// 文件: StopWorkflowModule.kt
// 描述: 定义了正常停止工作流执行的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * "停止工作流" 模块。
 * 正常地、无条件地终止当前工作流的执行。
 */
class StopWorkflowModule : BaseModule() {
    override val id = "vflow.logic.stop_workflow"
    override val metadata = ActionMetadata(
        name = "停止工作流",
        description = "正常地、无条件地终止当前工作流的执行。",
        iconRes = R.drawable.rounded_stop_circle_24, // 使用一个合适的图标
        category = "逻辑控制"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "停止工作流"
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在停止工作流..."))
        // 发出 Stop 信号，由执行器处理
        return ExecutionResult.Signal(ExecutionSignal.Stop)
    }
}