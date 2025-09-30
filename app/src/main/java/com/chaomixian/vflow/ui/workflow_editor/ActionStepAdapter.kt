// 文件: ActionStepAdapter.kt
// 描述: 工作流编辑器中步骤列表 (RecyclerView) 的适配器。
//      [已修复] 解决了设置类别颜色条时因背景为空导致的崩溃问题。

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

    /** 移动列表项 (用于拖拽排序)。 */
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

            // 设置缩进
            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            // [崩溃修复] 创建一个新的Drawable并设置颜色，而不是在null背景上调用setTint
            val categoryColor = ContextCompat.getColor(context, PillUtil.getCategoryColor(module.metadata.category))
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * context.resources.displayMetrics.density) // 保持圆角
                setColor(categoryColor)
            }
            categoryColorBar.background = drawable


            contentContainer.removeAllViews()

            val rawSummary = module.getSummary(context, step)
            val finalSummary = PillRenderer.renderPills(context, rawSummary, allSteps, step)

            val prefix = "#$position "
            val spannablePrefix = SpannableStringBuilder(prefix).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val prefixColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val finalTitle = SpannableStringBuilder().append(spannablePrefix).append(finalSummary ?: module.metadata.name)
            val headerView = createHeaderRow(finalTitle) // 创建包含摘要的头部视图
            contentContainer.addView(headerView)

            // 如果模块提供了UIProvider，则创建并添加自定义预览视图
            val customPreview = module.uiProvider?.createPreview(
                context,
                contentContainer,
                step,
                allSteps // 传递allSteps给预览
            ) { intent, callback ->
                // 当自定义预览视图请求启动Activity时，调用Adapter的回调
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onStartActivityForResult(adapterPosition, intent, callback)
                }
            }

            if (customPreview != null) {
                // 为自定义预览添加上边距，以和标题分开
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.topMargin = (8 * context.resources.displayMetrics.density).toInt()
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

            // 控制删除按钮的可见性 (触发器不可删，块结束/中间步骤根据配置)
            val behavior = module.blockBehavior
            val isDeletable = position != 0 && (behavior.isIndividuallyDeletable || behavior.type == BlockType.BLOCK_START || behavior.type == BlockType.NONE)
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        /** 创建步骤摘要的 TextView，并处理参数药丸的点击。 */
        private fun createHeaderRow(summary: CharSequence): View {
            val textView = TextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = Color.TRANSPARENT
                includeFontPadding = false
                setLineSpacing(0f, 1.4f)
            }

            // 自定义 TouchListener 以区分药丸点击和列表项点击
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