// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/GetVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class GetVariableModule : BaseModule() {
    override val id = "vflow.variable.get"
    override val metadata = ActionMetadata(
        name = "读取变量",
        description = "读取一个命名变量或魔法变量的值，使其可用于后续步骤。",
        iconRes = R.drawable.rounded_dataset_24,
        category = "数据"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "source",
            name = "来源变量",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true // 允许选择魔法变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("value", "变量值", "vflow.type.any") // 输出类型未知
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val sourcePill = PillUtil.createPillFromParam(
            step.parameters["source"],
            getInputs().find { it.id == "source" }
        )
        return PillUtil.buildSpannable(context, "读取变量 ", sourcePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val source = context.variables["source"] as? String
        if (source.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "来源变量不能为空。")
        }

        val variableValue: Any?

        if (source.isMagicVariable()) {
            // 1. 如果输入是魔法变量，则“取值”
            variableValue = context.magicVariables["source"]
            onProgress(ProgressUpdate("已读取魔法变量的值"))
        } else {
            // 2. 如果输入是普通文本，则视为命名变量，进行“引用存储”
            variableValue = context.namedVariables[source]
            if (variableValue == null) {
                return ExecutionResult.Failure("执行错误", "找不到名为 '$source' 的变量。")
            }
            onProgress(ProgressUpdate("已读取命名变量 '$source'"))
        }

        return ExecutionResult.Success(mapOf("value" to variableValue))
    }
}