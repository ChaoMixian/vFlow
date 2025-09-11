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

    // 重写 getDynamicInputs 方法
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val staticInputs = getInputs()
        if (allSteps == null || step == null) return staticInputs

        // 1. 扫描当前步骤之前的所有步骤，找出所有已定义的命名变量
        val currentStepIndex = allSteps.indexOf(step).let { if (it == -1) allSteps.size else it }
        val availableNamedVariables = allSteps.subList(0, currentStepIndex)
            .filter { it.moduleId == CreateVariableModule().id }
            .mapNotNull { it.parameters["variableName"] as? String }
            .filter { it.isNotBlank() }
            .distinct()

        // 2. 如果找到了命名变量，就将"变量名称"输入框变为一个下拉菜单
        if (availableNamedVariables.isNotEmpty()) {
            val dynamicInputs = staticInputs.toMutableList()
            val nameInputIndex = dynamicInputs.indexOfFirst { it.id == "variableName" }
            if (nameInputIndex != -1) {
                dynamicInputs[nameInputIndex] = dynamicInputs[nameInputIndex].copy(
                    staticType = ParameterType.ENUM, // 类型变为枚举
                    options = availableNamedVariables // 选项为找到的变量名
                )
            }
            return dynamicInputs
        }

        // 3. 如果没找到，则保持原来的文本输入框
        return staticInputs
    }


    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "variableName",
            name = "变量名称",
            staticType = ParameterType.STRING, // 默认是文本输入
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "newValue",
            name = "新值",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val namePill = PillUtil.createPillFromParam(
            step.parameters["variableName"],
            getInputs().find { it.id == "variableName" }
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
        val variableName = context.variables["variableName"] as? String
        if (variableName.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "变量名称不能为空。")
        }

        if (!context.namedVariables.containsKey(variableName)) {
            return ExecutionResult.Failure("执行错误", "找不到名为 '$variableName' 的变量。请先使用“创建变量”模块创建它。")
        }

        val newValue = context.magicVariables["newValue"] ?: context.variables["newValue"]
        context.namedVariables[variableName] = newValue
        onProgress(ProgressUpdate("已修改变量 '$variableName' 的值"))
        return ExecutionResult.Success()
    }
}