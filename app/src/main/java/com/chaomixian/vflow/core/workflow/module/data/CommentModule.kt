package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer

class CommentModule : BaseModule() {
    override val id = "vflow.data.comment"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_comment_name,
        descriptionStringRes = R.string.module_vflow_data_comment_desc,
        name = "注释",
        description = "添加注释说明，用于记录工作流的设计思路和目的。执行时会被跳过。",
        iconRes = R.drawable.rounded_add_comment_24,
        category = "数据"
    )

    override val uiProvider: ModuleUIProvider? = CommentUIProvider()

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
        return context.getString(R.string.module_vflow_data_comment_name)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 注释模块不执行任何操作，直接跳过
        return ExecutionResult.Success(emptyMap())
    }
}

/**
 * 注释模块的UI提供器，使用默认的富文本输入框
 * 同时重写createPreview以始终显示预览（无论内容是否复杂）
 */
class CommentUIProvider : ModuleUIProvider {

    private val innerProvider = com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider("content")

    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?, onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        // 返回一个空的 ViewHolder，让系统使用默认的富文本输入框
        return object : CustomEditorViewHolder(View(context)) {}
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> = emptyMap()

    /**
     * 始终创建预览视图，无论内容是否复杂
     */
    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val rawText = step.parameters["content"]?.toString() ?: ""

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val spannable = PillRenderer.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }
}
