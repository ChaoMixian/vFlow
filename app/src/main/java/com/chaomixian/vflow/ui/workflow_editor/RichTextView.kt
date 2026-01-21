// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextView.kt
// 描述: 支持变量药丸的编辑器（重构后 - 职责简化，仅负责编辑逻辑）
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 支持变量药丸的编辑器
 *
 * 职责（重构后）：
 * - 仅处理编辑逻辑（光标管理、文本输入）
 * - 渲染委托给 PillRenderer
 *
 * 不再负责：
 * - 变量解析（由 PillVariableResolver 处理）
 * - Pill 视觉渲染（由 PillRenderer 处理）
 */
class RichTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    // 保存 allSteps 引用，用于 insertVariable 方法
    private var currentAllSteps: List<ActionStep> = emptyList()

    // 可选的变量类型过滤器（例如只允许图片类型）
    private var variableTypeFilter: Set<String>? = null

    /**
     * 设置富文本内容
     *
     * @param rawText 包含变量引用的原始文本
     * @param allSteps 工作流中的所有步骤，用于解析变量
     */
    fun setRichText(rawText: String, allSteps: List<ActionStep> = emptyList()) {
        currentAllSteps = allSteps
        // 使用 PillRenderer 的统一渲染方法，EDIT 模式用于编辑器
        val spannable = PillRenderer.renderToSpannable(
            rawText,
            PillRenderer.RenderMode.EDIT,
            allSteps,
            context
        )
        setText(spannable)
    }

    /**
     * 获取纯文本（原始变量引用）
     */
    fun getRawText(): String {
        return text.toString()
    }

    /**
     * 在当前光标位置插入一个变量"药丸"
     *
     * @param variableReference 变量引用（如 "{{step1.output}}"）
     */
    fun insertVariablePill(variableReference: String) {
        // 使用 PillRenderer 渲染单个 Pill，EDIT 模式用于编辑器
        val pillSpannable = PillRenderer.renderSinglePill(
            variableReference,
            PillRenderer.RenderMode.EDIT,
            currentAllSteps,
            context
        )

        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val spannable = text as SpannableStringBuilder

        // 插入到文本中
        spannable.replace(start, end, pillSpannable)

        // 将光标移动到插入内容的末尾
        setSelection(start + pillSpannable.length)
    }

    /**
     * 设置变量类型过滤器
     * 设置后，只有符合类型的变量才能被插入（用于变量选择器过滤）
     *
     * @param types 允许的变量类型集合（例如 setOf("vflow.type.image")）
     */
    fun setVariableTypeFilter(types: Set<String>?) {
        variableTypeFilter = types
    }

    /**
     * 获取变量类型过滤器
     */
    fun getVariableTypeFilter(): Set<String>? = variableTypeFilter
}
