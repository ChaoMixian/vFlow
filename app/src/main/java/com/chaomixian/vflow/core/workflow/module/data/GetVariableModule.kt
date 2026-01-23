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
            // 变量不存在时返回 Failure，让用户通过"异常处理策略"选择行为
            // 用户可以选择：重试（变量可能稍后被设置）、忽略错误继续（输出 VNull）、停止工作流
            val sourceRef = context.variables["source"] as? String ?: "未知"
            return ExecutionResult.Failure(
                "变量不存在",
                "找不到变量 '$sourceRef' 的值",
                // GetVariableModule 只有一个输出 value，不需要特殊的 partialOutputs
                // 使用默认的 VNull 即可
            )
        }

        onProgress(ProgressUpdate("已读取变量的值"))
        return ExecutionResult.Success(mapOf("value" to variableValue))
    }
}