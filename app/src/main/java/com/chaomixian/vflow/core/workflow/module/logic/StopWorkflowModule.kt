// 文件: StopWorkflowModule.kt
// 描述: 定义了正常停止工作流执行的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "停止工作流" 模块。
 * 可以停止当前工作流或其他指定工作流的执行。
 */
class StopWorkflowModule : BaseModule() {
    override val id = "vflow.logic.stop_workflow"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_stop_workflow_name,
        descriptionStringRes = R.string.module_vflow_logic_stop_workflow_desc,
        name = "停止工作流",  // Fallback
        description = "停止当前工作流或其他指定工作流的执行",  // Fallback
        iconRes = R.drawable.rounded_stop_circle_24,
        category = "逻辑控制"
    )

    override val uiProvider: ModuleUIProvider = StopWorkflowModuleUIProvider()

    companion object {
        const val TARGET_CURRENT = "current"
        const val TARGET_OTHER = "other"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            nameStringRes = R.string.param_vflow_logic_stop_workflow_target_name,
            name = "停止目标",
            staticType = ParameterType.ENUM,
            defaultValue = TARGET_CURRENT,
            options = listOf(TARGET_CURRENT, TARGET_OTHER),
            optionsStringRes = listOf(
                R.string.option_vflow_logic_stop_workflow_target_current,
                R.string.option_vflow_logic_stop_workflow_target_other
            ),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "workflow_id",
            nameStringRes = R.string.param_vflow_logic_stop_workflow_workflow_id_name,
            name = "工作流",
            staticType = ParameterType.STRING,
            defaultValue = "",
            visibility = InputVisibility.whenEquals("target", TARGET_OTHER),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val target = step.parameters["target"] as? String ?: TARGET_CURRENT
        return when (target) {
            TARGET_CURRENT -> context.getString(R.string.summary_vflow_logic_stop_workflow_current)
            TARGET_OTHER -> {
                val workflowId = step.parameters["workflow_id"] as? String
                if (workflowId.isNullOrBlank()) {
                    context.getString(R.string.summary_vflow_logic_stop_workflow_other_invalid)
                } else {
                    val workflowName = WorkflowManager(context).getWorkflow(workflowId)?.name
                        ?: context.getString(R.string.summary_unknown_workflow)
                    val workflowPill = PillUtil.Pill(workflowName, "workflow_id")
                    PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_stop_workflow_other_prefix), workflowPill)
                }
            }
            else -> context.getString(R.string.summary_vflow_logic_stop_workflow)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = context.getVariableAsString("target") ?: TARGET_CURRENT

        return when (target) {
            TARGET_CURRENT -> {
                onProgress(ProgressUpdate("正在停止当前工作流..."))
                // 发出 Stop 信号，由执行器处理
                ExecutionResult.Signal(ExecutionSignal.Stop)
            }
            TARGET_OTHER -> {
                val workflowId = context.getVariableAsString("workflow_id")
                if (workflowId.isNullOrBlank()) {
                    return ExecutionResult.Failure("参数错误", "未指定要停止的工作流ID")
                }

                onProgress(ProgressUpdate("正在停止工作流: $workflowId"))

                // 检查目标工作流是否正在运行
                if (!WorkflowExecutor.isRunning(workflowId)) {
                    // 目标工作流未运行，但这可能不是错误，只是没有操作
                    onProgress(ProgressUpdate("工作流 '$workflowId' 未在运行"))
                    return ExecutionResult.Success()
                }

                // 停止指定的工作流
                WorkflowExecutor.stopExecution(workflowId)
                onProgress(ProgressUpdate("已停止工作流: $workflowId"))
                ExecutionResult.Success()
            }
            else -> {
                ExecutionResult.Failure("参数错误", "未知的目标类型: $target")
            }
        }
    }
}