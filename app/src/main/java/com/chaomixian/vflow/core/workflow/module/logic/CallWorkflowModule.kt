// main/java/com/chaomixian/vflow/core/workflow/module/logic/CallWorkflowModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.SubWorkflowResult
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

    /**
     * 获取子工作流的动态输出。
     * 收集子工作流中所有命名变量，暴露给主工作流。
     */
    override fun getDynamicOutputs(step: ActionStep?, allSteps: List<ActionStep>?): List<OutputDefinition> {
        if (step == null) return getOutputs(step)

        val workflowId = step.parameters["workflow_id"] as? String ?: return getOutputs(step)

        // 获取子工作流
        val subWorkflow = WorkflowManager(appContext).getWorkflow(workflowId) ?: return getOutputs(step)

        val outputs = mutableListOf<OutputDefinition>()

        // 1. 添加子工作流返回值
        outputs.add(OutputDefinition(
            id = "result",
            name = "子工作流返回值",
            typeName = "vflow.type.any"
        ))

        // 2. 收集子工作流中的命名变量作为输出
        val seenNamedVars = mutableSetOf<String>()
        for (subStep in subWorkflow.steps) {
            if (subStep.moduleId == "vflow.variable.create") {
                val varName = subStep.parameters["variableName"] as? String
                if (!varName.isNullOrBlank() && !seenNamedVars.contains(varName)) {
                    seenNamedVars.add(varName)
                    val type = subStep.parameters["type"] as? String ?: "文本"
                    val typeName = when (type) {
                        "文本" -> "vflow.type.string"
                        "数字" -> "vflow.type.number"
                        "布尔" -> "vflow.type.boolean"
                        "字典" -> "vflow.type.dictionary"
                        "列表" -> "vflow.type.list"
                        "图像" -> "vflow.type.image"
                        "坐标" -> "vflow.type.coordinate"
                        else -> "vflow.type.any"
                    }
                    outputs.add(OutputDefinition(
                        id = "var_$varName",
                        name = "变量: $varName",
                        typeName = typeName
                    ))
                }
            }
        }

        return outputs
    }

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

        val subResult = WorkflowExecutor.executeSubWorkflow(
            workflowToCall,
            context
        )

        onProgress(ProgressUpdate("子工作流 '${workflowToCall.name}' 执行完毕。"))

        // 构建输出 map
        val outputs = mutableMapOf<String, Any?>()

        // 1. 返回值
        outputs["result"] = subResult.returnValue

        // 2. 命名变量
        for ((varName, value) in subResult.namedVariables) {
            outputs["var_$varName"] = value
        }

        return ExecutionResult.Success(outputs)
    }
}