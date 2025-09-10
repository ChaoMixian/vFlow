// 文件: ContinueLoopModule.kt
// 描述: 定义了在循环中继续下一次迭代的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * "继续循环" 模块。
 * 跳过当前循环的剩余步骤，直接开始下一次迭代。
 */
class ContinueLoopModule : BaseModule() {
    override val id = "vflow.logic.continue_loop"
    override val metadata = ActionMetadata(
        name = "继续循环",
        description = "跳过当前循环的剩余步骤，直接进入下一次迭代。",
        iconRes = R.drawable.rounded_skip_next_24, // 使用一个合适的图标
        category = "逻辑控制"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "继续下一次循环"
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("正在继续下一次循环..."))
        // 发出 Continue 信号，由执行器处理
        return ExecutionResult.Signal(ExecutionSignal.Continue)
    }
}