// 文件: BreakLoopModule.kt
// 描述: 定义了跳出当前循环的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

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

        // 我们需要找到当前步骤所属的循环块的配对ID，并发出一个信号
        // 但目前的代码结构下，无法在运行时直接获取当前步骤所在的块。
        // 一个简单的实现是直接跳到最近的结束块，但这可能有嵌套问题。
        // 为了安全和简单，我们假设它只跳出最内层循环。
        // 这需要执行器来处理，我们只需发出信号即可。

        return ExecutionResult.Signal(ExecutionSignal.Break)
    }
}