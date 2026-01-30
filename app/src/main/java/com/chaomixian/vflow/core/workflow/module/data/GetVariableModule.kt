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
        nameStringRes = R.string.module_vflow_variable_get_name,
        descriptionStringRes = R.string.module_vflow_variable_get_desc,
        name = "读取变量",  // Fallback
        description = "读取一个命名变量或魔法变量的值，使其可用于后续步骤",  // Fallback
        iconRes = R.drawable.rounded_dataset_24,
        category = "数据"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "source",
            name = "来源变量",
            nameStringRes = R.string.param_vflow_variable_get_source_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "value",
            "变量值",
            VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_variable_get_value_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val sourcePill = PillUtil.createPillFromParam(
            step.parameters["source"],
            getInputs().find { it.id == "source" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_get_variable), sourcePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val variableValue = context.magicVariables["source"]

        if (variableValue == null) {
            val sourceRef = context.variables["source"] as? String ?: "未知"
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_variable_get_not_exist),
                "找不到变量 '$sourceRef' 的值"
            )
        }

        onProgress(ProgressUpdate("已读取变量的值"))
        return ExecutionResult.Success(mapOf("value" to variableValue))
    }
}