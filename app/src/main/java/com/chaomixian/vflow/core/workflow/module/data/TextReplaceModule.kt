// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextReplaceModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 文本替换模块
 * 将文本中的指定内容替换为新内容
 */
class TextReplaceModule : BaseModule() {

    override val id = "vflow.data.text_replace"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_text_replace_name,
        descriptionStringRes = R.string.module_vflow_data_text_replace_desc,
        name = "文本替换",
        description = "将文本中的指定内容替换为新内容",
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据"
    )

    // 使用默认 UI，无需自定义 UIProvider

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            nameStringRes = R.string.param_vflow_data_text_replace_text_name,
            name = "源文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        ),
        InputDefinition(
            id = "find",
            nameStringRes = R.string.param_vflow_data_text_replace_find_name,
            name = "查找",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        ),
        InputDefinition(
            id = "replace",
            nameStringRes = R.string.param_vflow_data_text_replace_replace_name,
            name = "替换为",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            nameStringRes = R.string.output_vflow_data_text_replace_result_name,
            name = "结果文本",
            typeName = VTypeRegistry.STRING.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            inputs.find { it.id == "text" }
        )
        val findPill = PillUtil.createPillFromParam(
            step.parameters["find"],
            inputs.find { it.id == "find" }
        )
        val replacePill = PillUtil.createPillFromParam(
            step.parameters["replace"],
            inputs.find { it.id == "replace" }
        )
        return PillUtil.buildSpannable(
            context,
            "替换: ",
            textPill,
            " 中的 ",
            findPill,
            " 为 ",
            replacePill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val text = context.getVariableAsString("text", "")
        val find = context.getVariableAsString("find", "")
        val replace = context.getVariableAsString("replace", "")

        if (text.isEmpty()) {
            return ExecutionResult.Failure(
                "参数错误",
                "源文本不能为空"
            )
        }

        if (find.isEmpty()) {
            return ExecutionResult.Failure(
                "参数错误",
                "查找内容不能为空"
            )
        }

        val result = text.replace(find, replace)
        return ExecutionResult.Success(mapOf("result" to VString(result)))
    }
}
