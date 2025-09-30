// 文件: ActionStepAdapter.kt
// 描述: [已修复] 1. 解决了设置类别颜色条时导致的崩溃。 2. 实现了当富文本预览存在时，自动切换到简洁版标题，避免信息重复。

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.color.MaterialColors
import java.util.*

class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onParameterPillClick: (position: Int, parameterId: String) -> Unit,
    private val onStartActivityForResult: (position: Int, Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit
) : RecyclerView.Adapter<ActionStepAdapter.ActionStepViewHolder>() {

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition > 0 && toPosition > 0 && fromPosition < actionSteps.size && toPosition < actionSteps.size) {
            Collections.swap(actionSteps, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionStepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_step, parent, false)
        return ActionStepViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionStepViewHolder, position: Int) {
        val step = actionSteps[position]
        holder.bind(step, position, actionSteps)
    }

    override fun getItemCount() = actionSteps.size

    inner class ActionStepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container)
        private val categoryColorBar: View = itemView.findViewById(R.id.category_color_bar)

        fun bind(step: ActionStep, position: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            // [崩溃修复] 创建一个新的Drawable并设置颜色
            val categoryColor = ContextCompat.getColor(context, PillUtil.getCategoryColor(module.metadata.category))
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * context.resources.displayMetrics.density)
                setColor(categoryColor)
            }
            categoryColorBar.background = drawable

            contentContainer.removeAllViews()

            // [核心逻辑修改]
            // 1. 尝试创建自定义预览
            val customPreview = module.uiProvider?.createPreview(
                context, contentContainer, step, allSteps
            ) { intent, callback ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onStartActivityForResult(adapterPosition, intent, callback)
                }
            }

            // 2. 根据是否存在富文本预览来决定标题内容
            val headerSummary: CharSequence
            if (customPreview != null && module.uiProvider is RichTextUIProvider) {
                // 如果有富文本预览，标题只显示模块名，避免信息重复
                headerSummary = module.metadata.name
            } else {
                // 否则，显示完整的、带“药丸”的摘要
                val rawSummary = module.getSummary(context, step)
                headerSummary = PillRenderer.renderPills(context, rawSummary, allSteps, step) ?: module.metadata.name
            }

            // 3. 总是创建并添加标题行
            val prefix = "#$position "
            val spannablePrefix = SpannableStringBuilder(prefix).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val prefixColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val finalTitle = SpannableStringBuilder().append(spannablePrefix).append(headerSummary)
            val headerView = createHeaderRow(finalTitle)
            contentContainer.addView(headerView)

            // 4. 如果有自定义预览，将其添加到标题行下方
            if (customPreview != null) {
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // 如果是富文本预览，给它一个上边距与标题分开
                if (module.uiProvider is RichTextUIProvider) {
                    layoutParams.topMargin = (8 * context.resources.displayMetrics.density).toInt()
                }
                customPreview.layoutParams = layoutParams
                contentContainer.addView(customPreview)
            }

            deleteButton.setOnClickListener {
                if(adapterPosition != RecyclerView.NO_POSITION) onDeleteClick(adapterPosition)
            }
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }

            val behavior = module.blockBehavior
            val isDeletable = position != 0 && (behavior.isIndividuallyDeletable || behavior.type == BlockType.BLOCK_START || behavior.type == BlockType.NONE)
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        private fun createHeaderRow(summary: CharSequence): View {
            val textView = TextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = Color.TRANSPARENT
                includeFontPadding = false
                setLineSpacing(0f, 1.4f)
            }

            textView.setOnTouchListener { v, event ->
                val widget = v as TextView
                val text = widget.text
                if (text is Spanned && event.action == MotionEvent.ACTION_UP) {
                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                    val layout = widget.layout ?: return@setOnTouchListener false
                    val line = layout.getLineForVertical(y)
                    if (x < 0 || x > layout.getLineWidth(line)) {
                        itemView.performClick()
                        return@setOnTouchListener true
                    }
                    val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                    val links = text.getSpans(offset, offset, PillUtil.ParameterPillSpan::class.java)
                    if (links.isNotEmpty()) {
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onParameterPillClick(adapterPosition, links[0].parameterId)
                        }
                        true
                    } else {
                        itemView.performClick()
                        true
                    }
                } else {
                    false
                }
            }
            return textView
        }
    }
}