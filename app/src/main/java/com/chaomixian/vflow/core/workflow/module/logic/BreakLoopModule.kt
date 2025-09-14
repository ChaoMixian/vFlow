// 文件: BreakLoopModule.kt
// 描述: 定义了跳出当前循环的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * "跳出循环" 模块。
 * 立即终止当前循环的执行，并跳转到循环体之后的步骤。
 */
class BreakLoopModule : BaseModule() {
    override val id = "vflow.logic.break_loop"
    override val metadata = ActionMetadata(
        name = "跳出循环",
        description = "立即终止当前循环的执行，并跳转到循环体之后的步骤。",
        iconRes = R.drawable.rounded_logout_24, // 复用图标
        category = "逻辑控制"
    )

    // 跳出循环模块没有输入和输出，它只影响控制流
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "跳出当前循环"
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("准备跳出循环..."))

        // 使用 BlockNavigator
        val currentLoopPairingId = BlockNavigator.findCurrentLoopPairingId(context.allSteps, context.currentStepIndex)
        if (currentLoopPairingId == null) {
            onProgress(ProgressUpdate("无法跳出：当前不在循环中。"))
            return ExecutionResult.Failure("无效操作", "‘跳出循环’模块必须放置在循环块内。")
        }

        return ExecutionResult.Signal(ExecutionSignal.Break)
    }
}