// main/java/com/chaomixian/vflow/core/workflow/module/logic/CallWorkflowModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class CallWorkflowModule : BaseModule() {
    override val id = "vflow.logic.call_workflow"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_call_workflow_name,
        descriptionStringRes = R.string.module_vflow_logic_call_workflow_desc,
        name = "调用工作流",
        description = "执行另一个工作流作为子程序。",
        iconRes = R.drawable.rounded_swap_calls_24,
        category = "逻辑控制"
    )

    override val uiProvider: ModuleUIProvider = CallWorkflowModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "workflow_id",
            nameStringRes = R.string.param_vflow_logic_call_workflow_workflow_id_name,
            name = "工作流",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
        // 输入参数 'inputs' 将由 UIProvider 动态处理
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", nameStringRes = R.string.output_vflow_logic_call_workflow_result_name, name = "子工作流返回值", typeName = "vflow.type.any")
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val workflowId = step.parameters["workflow_id"] as? String
        val workflowName = if (workflowId != null) {
            WorkflowManager(context).getWorkflow(workflowId)?.name ?: context.getString(R.string.summary_unknown_workflow)
        } else {
            context.getString(R.string.summary_no_workflow_selected)
        }
        val workflowPill = PillUtil.Pill(workflowName, "workflow_id")
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_call_workflow_prefix), workflowPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val workflowId = context.getVariableAsString("workflow_id", "")
            ?: return ExecutionResult.Failure("参数错误", "未选择要调用的工作流。")

        val workflowToCall = WorkflowManager(context.applicationContext).getWorkflow(workflowId)
            ?: return ExecutionResult.Failure("执行错误", "找不到ID为 '$workflowId' 的工作流。")

        // 防止无限递归
        if (context.workflowStack.contains(workflowId)) {
            return ExecutionResult.Failure("递归错误", "检测到循环调用: ${context.workflowStack.joinToString(" -> ")} -> $workflowId")
        }

        onProgress(ProgressUpdate("正在调用: ${workflowToCall.name}"))

        val result = WorkflowExecutor.executeSubWorkflow(
            workflowToCall,
            context
        )

        onProgress(ProgressUpdate("子工作流 '${workflowToCall.name}' 执行完毕。"))
        return ExecutionResult.Success(mapOf("result" to result))
    }
}