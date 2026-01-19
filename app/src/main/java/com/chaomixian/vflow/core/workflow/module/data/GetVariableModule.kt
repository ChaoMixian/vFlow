// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/GetVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
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
            acceptsMagicVariable = true,
            acceptsNamedVariable = true // 明确表示接受命名变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("value", "变量值", VTypeRegistry.ANY.id) // 输出类型未知
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
        // 现在的逻辑变得非常简单，因为所有变量解析都由执行器完成了
        val variableValue = context.magicVariables["source"]

        if (variableValue == null) {
            val sourceRef = context.variables["source"] as? String ?: "未知"
            return ExecutionResult.Failure("执行错误", "找不到变量 '$sourceRef' 的值。")
        }

        onProgress(ProgressUpdate("已读取变量的值"))
        return ExecutionResult.Success(mapOf("value" to variableValue))
    }
}