package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class CommentModule : BaseModule() {
    override val id = "vflow.data.comment"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_comment_name,
        descriptionStringRes = R.string.module_vflow_data_comment_desc,
        name = "注释",
        description = "添加注释说明，用于记录工作流的设计思路和目的。执行时会被跳过。",
        iconRes = R.drawable.rounded_add_comment_24,
        category = "数据",
        categoryId = "data"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            name = "注释内容",
            nameStringRes = R.string.param_vflow_data_comment_content_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.module_vflow_data_comment_name),
            PillUtil.richTextPreview(
                rawText = step.parameters["content"]?.toString(),
                onlyWhenComplex = false
            )
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 注释模块不执行任何操作，直接跳过
        return ExecutionResult.Success(emptyMap())
    }
}
