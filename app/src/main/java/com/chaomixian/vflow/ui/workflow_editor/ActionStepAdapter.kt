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
import android.widget.PopupWindow
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
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
    private val isSelectionModeEnabled: () -> Boolean = { false },
    private val isStepSelected: (String) -> Boolean = { false },
    private val onStepSelectionClick: (Int) -> Unit = {},
    private val onAddTriggerClick: () -> Unit = {},
    private val onEditTriggerClick: (position: Int, inputId: String?) -> Unit = { _, _ -> },
    private val onDeleteTriggerClick: (position: Int) -> Unit = {},
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onDuplicateClick: (position: Int) -> Unit,
    private val onToggleEnabledClick: (position: Int) -> Unit,
    private val onRestoreBlockClick: (position: Int) -> Unit,
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
                holder.bind(
                    step = step,
                    actualPosition = actualPosition,
                    displayIndex = displayIndex,
                    allSteps = getAllSteps()
                )
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
        selectionModeEnabled: Boolean,
        isSelected: Boolean,
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
        val stepCardView: MaterialCardView = cardView.findViewById(R.id.step_card_view)
        val actionContainer: LinearLayout = cardView.findViewById(R.id.layout_step_actions)
        val deleteButton: ImageButton = cardView.findViewById(R.id.button_delete_action)
        val moreButton: ImageButton = cardView.findViewById(R.id.button_more_action)

        indentSpace.layoutParams.width = (indentLevel * 24 * context.resources.displayMetrics.density).toInt()
        val categoryColor = ContextCompat.getColor(context, PillTheme.getCategoryColor(module.metadata.getResolvedCategoryId()))
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (4 * context.resources.displayMetrics.density)
            setColor(categoryColor)
        }
        categoryColorBar.background = drawable

        val isActionStep = prefixText != null
        val isDisabled = if (isActionStep) BlockStructureHelper.isStepEffectivelyDisabled(actionSteps, actualPosition) else step.isDisabled
        if (selectionModeEnabled) {
            val selectionAlpha = if (isSelected) 1f else 0.42f
            stepCardView.alpha = selectionAlpha
            contentContainer.alpha = selectionAlpha
            categoryColorBar.alpha = selectionAlpha
        } else {
            stepCardView.alpha = if (isDisabled) 0.52f else 1f
            categoryColorBar.alpha = if (isDisabled) 0.45f else 1f
            contentContainer.alpha = if (isDisabled) 0.78f else 1f
        }
        if (selectionModeEnabled && isSelected) {
            stepCardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            stepCardView.strokeColor = MaterialColors.getColor(
                context,
                android.R.attr.colorPrimary,
                Color.WHITE
            )
        } else {
            stepCardView.strokeWidth = 0
            stepCardView.strokeColor = Color.TRANSPARENT
        }

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

        deleteButton.visibility = if (selectionModeEnabled) View.GONE else if (isActionStep && isDeletable) View.GONE else if (isDeletable) View.VISIBLE else View.GONE
        deleteButton.setOnClickListener { onDelete?.invoke() }

        if (isActionStep) {
            actionContainer.visibility = if (selectionModeEnabled) View.GONE else View.VISIBLE
            moreButton.visibility = View.VISIBLE
            deleteButton.visibility = View.GONE
        } else {
            actionContainer.visibility = if (selectionModeEnabled) View.GONE else if (isDeletable) View.VISIBLE else View.GONE
            moreButton.visibility = View.GONE
        }

        cardView.setOnClickListener { onClick() }
        categoryColorBarContainer.setOnLongClickListener {
            if (selectionModeEnabled) {
                false
            } else {
                onLongPress?.invoke()
                onLongPress != null
            }
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
            if (event.action == MotionEvent.ACTION_UP && isSelectionModeEnabled()) {
                onFallbackClick()
                true
            } else if (text is Spanned && event.action == MotionEvent.ACTION_UP) {
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
                    selectionModeEnabled = false,
                    isSelected = false,
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
        private var actionPopupWindow: PopupWindow? = null

        fun bind(step: ActionStep, actualPosition: Int, displayIndex: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return
            val canDelete = module.blockBehavior.isIndividuallyDeletable ||
                module.blockBehavior.type == BlockType.BLOCK_START ||
                module.blockBehavior.type == BlockType.NONE
            val canDuplicate = module.blockBehavior.type != BlockType.BLOCK_END
            val canRestoreBlock = BlockStructureHelper.getMissingMiddleModuleIds(actionSteps, actualPosition).isNotEmpty()
            val rawSummary = module.getSummary(context, step)
            actionPopupWindow?.dismiss()
            actionPopupWindow = null

            bindEmbeddedStepCard(
                cardView = itemView,
                step = step,
                actualPosition = actualPosition,
                rawSummary = rawSummary,
                prefixText = "#$displayIndex ",
                selectionModeEnabled = isSelectionModeEnabled(),
                isSelected = isStepSelected(step.id),
                allSteps = allSteps,
                indentLevel = step.indentationLevel,
                isDeletable = canDelete,
                onParameterPillClick = { parameterId ->
                    onParameterPillClick(actualPosition, parameterId)
                },
                onClick = {
                    if (isSelectionModeEnabled()) {
                        onStepSelectionClick(actualPosition)
                    } else {
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
                            if (canDuplicate && adapterPosition != RecyclerView.NO_POSITION) {
                                onDuplicateClick(actualPosition)
                            }
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
                        if (actionPopupWindow?.isShowing == true) {
                            actionPopupWindow?.dismiss()
                            actionPopupWindow = null
                        } else {
                            showFloatingActionMenu(
                                itemView.findViewById(R.id.category_color_bar_container),
                                actualPosition,
                                step,
                                canDuplicate,
                                canDelete,
                                canRestoreBlock
                            )
                        }
                    }
                }
            )

            val moreButton: ImageButton = itemView.findViewById(R.id.button_more_action)

            moreButton.setOnClickListener {
                if (actionPopupWindow?.isShowing == true) {
                    actionPopupWindow?.dismiss()
                    actionPopupWindow = null
                } else if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    showFloatingActionMenu(moreButton, actualPosition, step, canDuplicate, canDelete, canRestoreBlock)
                }
            }
        }

        private fun showFloatingActionMenu(
            anchor: View,
            actualPosition: Int,
            step: ActionStep,
            canDuplicate: Boolean,
            canDelete: Boolean,
            canRestoreBlock: Boolean
        ) {
            val popupView = LayoutInflater.from(context).inflate(R.layout.popup_step_actions, null, false)
            val toggleEnabledButton: ImageButton = popupView.findViewById(R.id.button_toggle_step_enabled)
            val restoreButton: ImageButton = popupView.findViewById(R.id.button_restore_block_action)
            val insertBelowButton: ImageButton = popupView.findViewById(R.id.button_insert_below_action)
            val duplicateButton: ImageButton = popupView.findViewById(R.id.button_duplicate_action)
            val deleteButton: ImageButton = popupView.findViewById(R.id.button_delete_action)

            val secondaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSecondaryContainer)
            val onSecondaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSecondaryContainer)
            val primaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorPrimaryContainer)
            val onPrimaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnPrimaryContainer)
            val tertiaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorTertiaryContainer)
            val onTertiaryContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnTertiaryContainer)
            val errorContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorErrorContainer)
            val onErrorContainer = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnErrorContainer)
            val isEffectivelyDisabled = BlockStructureHelper.isStepEffectivelyDisabled(actionSteps, actualPosition)

            tintPopupButton(
                button = toggleEnabledButton,
                backgroundColor = secondaryContainer,
                iconColor = onSecondaryContainer
            )
            toggleEnabledButton.setImageResource(
                if (isEffectivelyDisabled) R.drawable.rounded_play_arrow_24 else R.drawable.rounded_pause_24
            )
            toggleEnabledButton.contentDescription = context.getString(
                if (isEffectivelyDisabled) R.string.desc_enable_action else R.string.desc_disable_action
            )

            tintPopupButton(
                button = restoreButton,
                backgroundColor = primaryContainer,
                iconColor = onPrimaryContainer
            )
            restoreButton.visibility = if (canRestoreBlock) View.VISIBLE else View.GONE

            tintPopupButton(
                button = insertBelowButton,
                backgroundColor = primaryContainer,
                iconColor = onPrimaryContainer
            )
            insertBelowButton.contentDescription = context.getString(R.string.insert_below)

            tintPopupButton(
                button = duplicateButton,
                backgroundColor = tertiaryContainer,
                iconColor = onTertiaryContainer
            )
            duplicateButton.visibility = if (canDuplicate) View.VISIBLE else View.GONE

            tintPopupButton(
                button = deleteButton,
                backgroundColor = errorContainer,
                iconColor = onErrorContainer
            )
            deleteButton.visibility = if (canDelete) View.VISIBLE else View.GONE

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                elevation = 24f
                setOnDismissListener {
                    if (actionPopupWindow === this) {
                        actionPopupWindow = null
                    }
                }
            }

            toggleEnabledButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    popupWindow.dismiss()
                    onToggleEnabledClick(actualPosition)
                }
            }
            restoreButton.setOnClickListener {
                if (canRestoreBlock && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    popupWindow.dismiss()
                    onRestoreBlockClick(actualPosition)
                }
            }
            insertBelowButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    popupWindow.dismiss()
                    onInsertBelowClick(actualPosition)
                }
            }
            duplicateButton.setOnClickListener {
                if (canDuplicate && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    popupWindow.dismiss()
                    onDuplicateClick(actualPosition)
                }
            }
            deleteButton.setOnClickListener {
                if (canDelete && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    popupWindow.dismiss()
                    onDeleteClick(actualPosition)
                }
            }

            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val xOffset = anchor.width - popupView.measuredWidth
            val yOffset = -anchor.height - popupView.measuredHeight
            popupWindow.showAsDropDown(anchor, xOffset, yOffset)
            actionPopupWindow = popupWindow
        }

        private fun tintPopupButton(
            button: ImageButton,
            backgroundColor: Int,
            iconColor: Int
        ) {
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backgroundColor)
            }
            button.background = background
            button.setColorFilter(iconColor)
        }

    }

}
