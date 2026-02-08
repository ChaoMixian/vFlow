// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextSplitModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 文本分割模块
 * 将文本按分隔符分割成列表。如果分隔符为空，则逐字符分割。
 */
class TextSplitModule : BaseModule() {

    override val id = "vflow.data.text_split"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_text_split_name,
        descriptionStringRes = R.string.module_vflow_data_text_split_desc,
        name = "文本分割",
        description = "将文本按分隔符分割成列表。如果分隔符为空，则逐字符分割。",
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据"
    )

    // 使用默认 UI，无需自定义 UIProvider

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            nameStringRes = R.string.param_vflow_data_text_split_text_name,
            name = "源文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        ),
        InputDefinition(
            id = "delimiter",
            nameStringRes = R.string.param_vflow_data_text_split_delimiter_name,
            name = "分隔符",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            nameStringRes = R.string.output_vflow_data_text_split_result_name,
            name = "结果列表",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.STRING.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            inputs.find { it.id == "text" }
        )
        val delimiterPill = PillUtil.createPillFromParam(
            step.parameters["delimiter"],
            inputs.find { it.id == "delimiter" }
        )
        return PillUtil.buildSpannable(
            context,
            "分割: ",
            textPill,
            " 用 ",
            delimiterPill,
            " 分隔"
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val text = context.getVariableAsString("text", "")
        val delimiter = context.getVariableAsString("delimiter", "")

        if (text.isEmpty()) {
            return ExecutionResult.Failure(
                "参数错误",
                "源文本不能为空"
            )
        }

        val resultList = if (delimiter.isEmpty()) {
            // 分隔符为空，逐字符分割
            text.map { VString(it.toString()) }
        } else {
            text.split(delimiter).map { VString(it) }
        }

        onProgress(ProgressUpdate("已将文本分割为 ${resultList.size} 项"))
        return ExecutionResult.Success(mapOf("result" to VList(resultList)))
    }
}
