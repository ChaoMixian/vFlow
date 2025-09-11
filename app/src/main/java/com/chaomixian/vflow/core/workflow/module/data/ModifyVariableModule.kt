// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/ModifyVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ModifyVariableModule : BaseModule() {
    override val id = "vflow.variable.modify"
    override val metadata = ActionMetadata(
        name = "修改变量",
        description = "修改一个已存在的命名变量的值。",
        iconRes = R.drawable.ic_variable_type,
        category = "数据"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "variable", // ID 从 "variableName" 改为 "variable"
            name = "变量",     // 名称从 "变量名称" 改为 "变量"
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false, // 不接受步骤输出作为变量名
            acceptsNamedVariable = true  // 只接受命名变量作为输入
        ),
        InputDefinition(
            id = "newValue",
            name = "新值",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true // 新值可以接受两种变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val namePill = PillUtil.createPillFromParam(
            step.parameters["variable"], // 使用新的 ID "variable"
            getInputs().find { it.id == "variable" }
        )
        val valuePill = PillUtil.createPillFromParam(
            step.parameters["newValue"],
            getInputs().find { it.id == "newValue" }
        )
        return PillUtil.buildSpannable(context, "修改变量 ", namePill, " 的值为 ", valuePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 执行逻辑现在需要解析命名变量的引用格式 "[[...]]"
        val variableRef = context.variables["variable"] as? String
        if (variableRef.isNullOrBlank() || !variableRef.isNamedVariable()) {
            return ExecutionResult.Failure("参数错误", "请选择一个有效的命名变量。")
        }

        // 从 "[[...]]" 中提取变量名
        val variableName = variableRef.removeSurrounding("[[", "]]")

        if (!context.namedVariables.containsKey(variableName)) {
            return ExecutionResult.Failure("执行错误", "找不到名为 '$variableName' 的变量。请先使用“创建变量”模块创建它。")
        }

        // 新值统一从 magicVariables 中获取
        val newValue = context.magicVariables["newValue"] ?: context.variables["newValue"]
        context.namedVariables[variableName] = newValue
        onProgress(ProgressUpdate("已修改变量 '$variableName' 的值"))
        return ExecutionResult.Success()
    }
}