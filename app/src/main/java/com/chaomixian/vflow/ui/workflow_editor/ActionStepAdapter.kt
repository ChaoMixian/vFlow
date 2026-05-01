// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan
import com.chaomixian.vflow.ui.workflow_editor.pill.PillTheme
import com.google.android.material.color.MaterialColors
import java.util.Collections

class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val visiblePositionProvider: (List<ActionStep>) -> List<Int> = { steps -> steps.indices.toList() },
    private val displayIndexProvider: (adapterPosition: Int, actualPosition: Int) -> Int = { _, actualPosition -> actualPosition },
    private val getAllSteps: () -> List<ActionStep> = { actionSteps },
    private val getTriggerSteps: () -> List<ActionStep> = { emptyList() },
    private val onAddTriggerClick: () -> Unit = {},
    private val onEditTriggerClick: (position: Int, inputId: String?) -> Unit = { _, _ -> },
    private val onDeleteTriggerClick: (position: Int) -> Unit = {},
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onDuplicateClick: (position: Int) -> Unit,
    private val onInsertBelowClick: (position: Int) -> Unit,
    private val onTriggerParameterPillClick: (position: Int, parameterId: String) -> Unit = { _, _ -> },
    private val onParameterPillClick: (position: Int, parameterId: String) -> Unit,
    private val onStartActivityForResult: (position: Int, Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TRIGGER_GROUP = 0
        private const val VIEW_TYPE_ACTION_STEP = 1
    }

    private fun getVisiblePositions(): List<Int> = visiblePositionProvider(actionSteps)

    fun getActualPosition(adapterPosition: Int): Int? {
        if (adapterPosition <= 0) return null
        return getVisiblePositions().getOrNull(adapterPosition - 1)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val actualFrom = getActualPosition(fromPosition) ?: return
        val actualTo = getActualPosition(toPosition) ?: return
        if (actualFrom in actionSteps.indices && actualTo in actionSteps.indices) {
            Collections.swap(actionSteps, actualFrom, actualTo)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_TRIGGER_GROUP else VIEW_TYPE_ACTION_STEP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TRIGGER_GROUP -> TriggerGroupViewHolder(
                inflater.inflate(R.layout.item_trigger_group, parent, false)
            )
            else -> ActionStepViewHolder(
                inflater.inflate(R.layout.item_action_step, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TriggerGroupViewHolder -> holder.bind(getTriggerSteps(), getAllSteps())
            is ActionStepViewHolder -> {
                val actualPosition = getActualPosition(position) ?: return
                val step = actionSteps[actualPosition]
                val displayIndex = displayIndexProvider(position, actualPosition)
                holder.bind(step, actualPosition, displayIndex, getAllSteps())
            }
        }
    }

    override fun getItemCount(): Int = getVisiblePositions().size + 1

    private fun buildStepHeader(
        context: Context,
        summary: CharSequence,
        prefixText: String?
    ): CharSequence {
        if (prefixText.isNullOrBlank()) return summary
        val spannablePrefix = SpannableStringBuilder(prefixText).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val prefixColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.GRAY
            )
            setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return SpannableStringBuilder().append(spannablePrefix).append(summary)
    }

    private fun bindEmbeddedStepCard(
        cardView: View,
        step: ActionStep,
        actualPosition: Int,
        rawSummary: CharSequence?,
        prefixText: String?,
        allSteps: List<ActionStep>,
        indentLevel: Int,
        isDeletable: Boolean,
        onParameterPillClick: (parameterId: String) -> Unit,
        onClick: () -> Unit,
        onDelete: (() -> Unit)? = null,
        onLongPress: (() -> Unit)? = null
    ) {
        val context = cardView.context
        val module = ModuleRegistry.getModule(step.moduleId) ?: return
        val indentSpace: Space = cardView.findViewById(R.id.indent_space)
        val contentContainer: LinearLayout = cardView.findViewById(R.id.content_container)
        val categoryColorBarContainer: View = cardView.findViewById(R.id.category_color_bar_container)
        val categoryColorBar: View = cardView.findViewById(R.id.category_color_bar)
        val deleteButton: ImageButton = cardView.findViewById(R.id.button_delete_action)

        indentSpace.layoutParams.width = (indentLevel * 24 * context.resources.displayMetrics.density).toInt()
        val categoryColor = ContextCompat.getColor(context, PillTheme.getCategoryColor(module.metadata.getResolvedCategoryId()))
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (4 * context.resources.displayMetrics.density)
            setColor(categoryColor)
        }
        categoryColorBar.background = drawable

        contentContainer.removeAllViews()
        val summarySegments = buildSummarySegments(
            context = context,
            rawSummary = rawSummary,
            allSteps = allSteps
        ).ifEmpty {
            listOf(module.metadata.getLocalizedName(context) as Any)
        }

        var prefixApplied = false
        summarySegments.forEach { segment ->
            when (segment) {
                is CharSequence -> {
                    val summary = if (!prefixApplied) {
                        prefixApplied = true
                        buildStepHeader(context, segment, prefixText)
                    } else {
                        segment
                    }
                    val headerView = createHeaderRow(
                        context = context,
                        summary = summary,
                        clickTarget = cardView,
                        onParameterPillClick = onParameterPillClick,
                        onFallbackClick = onClick
                    )
                    contentContainer.addView(headerView)
                }
                is PillUtil.RichTextPill -> {
                    if (segment.onlyWhenComplex &&
                        !com.chaomixian.vflow.core.execution.VariableResolver.isComplex(segment.rawText)
                    ) {
                        return@forEach
                    }
                    val previewView = PillRenderer.createPreviewTextView(
                        context = context,
                        parent = contentContainer,
                        content = segment.rawText,
                        allSteps = allSteps,
                        style = PillRenderer.DisplayStyle.RICH_TEXT
                    )
                    contentContainer.addView(previewView)
                }
            }
        }

        val customPreview = module.uiProvider?.createPreview(context, contentContainer, step, allSteps) { intent, callback ->
            onStartActivityForResult(actualPosition, intent, callback)
        }
        if (customPreview != null) {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = (8 * context.resources.displayMetrics.density).toInt()
            customPreview.layoutParams = layoutParams
            contentContainer.addView(customPreview)
        }

        deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        deleteButton.setOnClickListener { onDelete?.invoke() }

        cardView.setOnClickListener { onClick() }
        categoryColorBarContainer.setOnLongClickListener {
            onLongPress?.invoke()
            onLongPress != null
        }
    }

    private fun buildSummarySegments(
        context: Context,
        rawSummary: CharSequence?,
        allSteps: List<ActionStep>
    ): List<Any> {
        return PillUtil.splitSummaryContent(rawSummary).mapNotNull { part ->
            when (part) {
                is CharSequence -> PillRenderer.renderDisplayText(
                    context = context,
                    content = part,
                    allSteps = allSteps,
                    style = PillRenderer.DisplayStyle.SUMMARY
                )
                is PillUtil.RichTextPill -> part
                else -> null
            }
        }
    }

    private fun createHeaderRow(
        context: Context,
        summary: CharSequence,
        clickTarget: View,
        onParameterPillClick: (parameterId: String) -> Unit,
        onFallbackClick: () -> Unit
    ): View {
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
                    clickTarget.performClick()
                    return@setOnTouchListener true
                }
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                val links = text.getSpans(offset, offset, ParameterPillSpan::class.java)
                if (links.isNotEmpty()) {
                    onParameterPillClick(links[0].parameterId)
                    true
                } else {
                    onFallbackClick()
                    true
                }
            } else {
                false
            }
        }
        return textView
    }

    inner class TriggerGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.text_trigger_group_title)
        private val triggerContainer: LinearLayout = itemView.findViewById(R.id.layout_trigger_steps)
        private val addButton: Button = itemView.findViewById(R.id.button_add_trigger)

        fun bind(triggerSteps: List<ActionStep>, allSteps: List<ActionStep>) {
            titleView.text = itemView.context.getString(R.string.workflow_editor_trigger_group_title)

            triggerContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            triggerSteps.forEachIndexed { index, step ->
                val module = ModuleRegistry.getModule(step.moduleId) ?: return@forEachIndexed
                val rawSummary = module.getSummary(itemView.context, step)
                val embeddedCard = inflater.inflate(R.layout.item_action_step, triggerContainer, false)
                bindEmbeddedStepCard(
                    cardView = embeddedCard,
                    step = step,
                    actualPosition = index,
                    rawSummary = rawSummary,
                    prefixText = null,
                    allSteps = allSteps,
                    indentLevel = 0,
                    isDeletable = triggerSteps.size > 1,
                    onParameterPillClick = { parameterId ->
                        onTriggerParameterPillClick(index, parameterId)
                    },
                    onClick = { onEditTriggerClick(index, null) },
                    onDelete = { onDeleteTriggerClick(index) }
                )
                triggerContainer.addView(embeddedCard)
            }

            addButton.setOnClickListener { onAddTriggerClick() }
        }
    }

    inner class ActionStepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val handler = Handler(Looper.getMainLooper())
        private var clickCount = 0

        fun bind(step: ActionStep, actualPosition: Int, displayIndex: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return
            val rawSummary = module.getSummary(context, step)

            bindEmbeddedStepCard(
                cardView = itemView,
                step = step,
                actualPosition = actualPosition,
                rawSummary = rawSummary,
                prefixText = "#$displayIndex ",
                allSteps = allSteps,
                indentLevel = step.indentationLevel,
                isDeletable = module.blockBehavior.isIndividuallyDeletable ||
                    module.blockBehavior.type == BlockType.BLOCK_START ||
                    module.blockBehavior.type == BlockType.NONE,
                onParameterPillClick = { parameterId ->
                    onParameterPillClick(actualPosition, parameterId)
                },
                onClick = {
                    clickCount++
                    if (clickCount == 1) {
                        handler.postDelayed({
                            if (clickCount == 1 && adapterPosition != RecyclerView.NO_POSITION) {
                                onEditClick(actualPosition, null)
                            }
                            clickCount = 0
                        }, 250)
                    } else if (clickCount == 2) {
                        clickCount = 0
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onDuplicateClick(actualPosition)
                        }
                    }
                },
                onDelete = {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onDeleteClick(actualPosition)
                    }
                },
                onLongPress = {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        showPopupMenu(itemView.findViewById(R.id.category_color_bar_container), actualPosition)
                    }
                }
            )
        }

        private fun showPopupMenu(anchor: View, actualPosition: Int) {
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, 1, 0, R.string.insert_below)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        onInsertBelowClick(actualPosition)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
