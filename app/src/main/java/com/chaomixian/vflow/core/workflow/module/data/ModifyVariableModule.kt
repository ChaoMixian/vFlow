// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/ModifyVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider

class ModifyVariableModule : BaseModule() {
    override val id = "vflow.variable.modify"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_variable_modify_name,
        descriptionStringRes = R.string.module_vflow_variable_modify_desc,
        name = "修改变量",  // Fallback
        description = "修改一个已存在的命名变量的值",  // Fallback
        iconRes = R.drawable.ic_variable_type,
        category = "数据"
    )

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("newValue")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "variable", // ID 从 "variableName" 改为 "variable"
            name = "变量",
            nameStringRes = R.string.param_vflow_variable_modify_variable_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false, // 不接受步骤输出作为变量名
            acceptsNamedVariable = true  // 只接受命名变量作为输入
        ),
        InputDefinition(
            id = "newValue",
            name = "新值",
            nameStringRes = R.string.param_vflow_variable_modify_newValue_name,
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true, // 新值可以接受两种变量
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val namePill = PillUtil.createPillFromParam(
            step.parameters["variable"], // 使用新的 ID "variable"
            getInputs().find { it.id == "variable" }
        )

        // 如果新值是复杂内容，RichTextUIProvider 会显示预览，这里只显示变量名
        val rawValue = step.parameters["newValue"]?.toString() ?: ""
        if (VariableResolver.isComplex(rawValue)) {
            val prefix = context.getString(R.string.summary_vflow_variable_modify_prefix)
            val middle = context.getString(R.string.summary_vflow_variable_modify_middle)
            return PillUtil.buildSpannable(context, prefix, namePill, middle)
        }

        val valuePill = PillUtil.createPillFromParam(
            step.parameters["newValue"],
            getInputs().find { it.id == "newValue" }
        )
        val prefix = context.getString(R.string.summary_vflow_variable_modify_prefix)
        val middle = context.getString(R.string.summary_vflow_variable_modify_middle)
        return PillUtil.buildSpannable(context, prefix, namePill, middle, valuePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 使用 getParameterRaw 获取原始参数值，保留命名变量格式 [[varName]]
        val rawVariableRef = context.getParameterRaw("variable") ?: ""
        val variableRef = rawVariableRef.ifBlank { context.getVariableAsString("variable", "") }

        if (variableRef.isNullOrBlank() || !variableRef.isNamedVariable()) {
            val title = appContext.getString(R.string.error_vflow_variable_modify_param_error)
            val message = appContext.getString(R.string.error_vflow_variable_modify_invalid)
            return ExecutionResult.Failure(title, message)
        }

        // 从 "[[...]]" 中提取变量名
        val variableName = variableRef.removeSurrounding("[[", "]]")

        // 检查变量是否存在
        val existingVar = context.getVariable(variableName)
        if (existingVar is VNull) {
            val title = appContext.getString(R.string.error_vflow_variable_modify_param_error)
            val message = String.format(appContext.getString(R.string.error_vflow_variable_modify_not_found), variableName)
            return ExecutionResult.Failure(title, message)
        }

        // 新值统一从 magicVariables 中获取
        val newValue = context.getVariable("newValue")
        context.setVariable(variableName, newValue)
        onProgress(ProgressUpdate("已修改变量 '$variableName' 的值"))
        return ExecutionResult.Success()
    }
}