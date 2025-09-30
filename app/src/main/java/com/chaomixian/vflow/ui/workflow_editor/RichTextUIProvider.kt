// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextUIProvider.kt
// 描述: [已修改] createPreview现在会判断内容复杂度，只在必要时显示富文本预览。

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import java.util.regex.Pattern
/**
 * 一个可复用的 ModuleUIProvider，专门用于处理富文本输入。
 * 它的核心功能是 createPreview，用于在步骤摘要中显示一个包含富文本内容的自定义视图。
 * 它不处理 createEditor，因为富文本编辑器的创建由 ActionEditorSheet 的通用逻辑完成。
 * @param richTextInputId 该模块中支持富文本的输入框的ID。
 */
class RichTextUIProvider(private val richTextInputId: String) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?, onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        throw NotImplementedError("RichTextUIProvider does not create a custom editor.")
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        throw NotImplementedError("RichTextUIProvider does not read from a custom editor.")
    }

    /**
     * 创建预览视图的逻辑已更新。
     * 只有当文本内容复杂（混合文本和变量，或多于一个变量）时，才会创建富文本预览。
     * 否则返回 null，以便适配器回退到使用模块的 getSummary() 方法。
     */
    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val rawText = step.parameters[richTextInputId]?.toString() ?: ""

        // 如果内容不复杂，则不创建自定义预览，回退到 getSummary
        if (!isComplex(rawText)) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val spannable = PillRenderer.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }

    /**
     * 判断一个字符串是否为“复杂”内容。
     * 复杂定义为：
     * 1. 包含至少一个变量，并且还包含非空格的纯文本。
     * 2. 包含两个或更多个变量。
     * @param rawText 待检查的原始文本。
     * @return 如果内容复杂则返回 true，否则返回 false。
     */
    private fun isComplex(rawText: String): Boolean {
        val variablePattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")
        val matcher = variablePattern.matcher(rawText)

        var variableCount = 0
        while (matcher.find()) {
            variableCount++
        }

        if (variableCount == 0) {
            // 没有变量，不复杂
            return false
        }

        if (variableCount > 1) {
            // 超过一个变量，就算复杂
            return true
        }

        // 只有一个变量，检查是否还有其他非空格文本
        val textWithoutVariable = matcher.replaceAll("").trim()
        return textWithoutVariable.isNotEmpty()
    }
}