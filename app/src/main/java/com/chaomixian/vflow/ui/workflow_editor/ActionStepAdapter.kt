// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.card.MaterialCardView
import java.util.*

class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (Int) -> Unit
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
        return ActionStepViewHolder(view, onEditClick)
    }

    override fun onBindViewHolder(holder: ActionStepViewHolder, position: Int) {
        val step = actionSteps[position]
        holder.itemView.tag = step
        holder.bind(step, position)
        holder.deleteButton.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount(): Int = actionSteps.size

    class ActionStepViewHolder(
        itemView: View,
        private val onEditClick: (position: Int, inputId: String?) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val nameTextView: TextView = itemView.findViewById(R.id.text_view_action_name)
        private val actionIcon: ImageView = itemView.findViewById(R.id.icon_action_type)
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_action_item)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container)
        private val headerContainer: View = itemView.findViewById(R.id.header_container)

        fun bind(step: ActionStep, position: Int) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            nameTextView.text = module.metadata.name
            actionIcon.setImageResource(module.metadata.iconRes)
            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()
            val categoryColor = ContextCompat.getColor(context, getCategoryColor(module.metadata.category))
            cardView.setCardBackgroundColor(categoryColor)

            cardView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }

            contentContainer.removeAllViews()

            val uiProvider = module.uiProvider
            var hasCustomPreview = false
            if (uiProvider != null) {
                val previewView = uiProvider.createPreview(context, contentContainer, step)
                if (previewView != null) {
                    contentContainer.addView(previewView)
                    hasCustomPreview = true
                }
            }

            headerContainer.isVisible = !hasCustomPreview
            (contentContainer.layoutParams as ViewGroup.MarginLayoutParams).topMargin = if(hasCustomPreview) (12 * context.resources.displayMetrics.density).toInt() else 0

            // --- 核心修复：改进药丸显示逻辑 ---
            module.getInputs().forEach { inputDef ->
                val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, contentContainer, false)

                val paramName = row.findViewById<TextView>(R.id.parameter_name)
                val inputNode = row.findViewById<ImageView>(R.id.input_node)
                val valueContainer = row.findViewById<FrameLayout>(R.id.parameter_value_container)

                inputNode.visibility = View.VISIBLE
                inputNode.tag = "input_${step.id}_${inputDef.id}"
                paramName.text = inputDef.name

                val paramValue = step.parameters[inputDef.id]
                val valueStr = paramValue?.toString()

                if (!valueStr.isNullOrBlank()) {
                    valueContainer.isVisible = true
                    val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
                    val pillText = pill.findViewById<TextView>(R.id.pill_text)
                    val pillBackground = pill.background.mutate() as android.graphics.drawable.GradientDrawable

                    if (valueStr.startsWith("{{")) {
                        pillText.text = "已连接变量"
                        pillBackground.setColor(ContextCompat.getColor(context, R.color.variable_pill_color))
                    } else {
                        // 静态值显示为灰色药丸
                        pillText.text = valueStr
                        pillBackground.setColor(ContextCompat.getColor(context, R.color.static_pill_color))
                    }
                    valueContainer.addView(pill)
                } else {
                    valueContainer.isVisible = false
                }

                row.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onEditClick(adapterPosition, inputDef.id)
                    }
                }
                contentContainer.addView(row)
            }


            module.getDynamicOutputs(step).forEach { outputDef ->
                val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, contentContainer, false)
                row.findViewById<TextView>(R.id.parameter_name).text = outputDef.name
                val outputNode = row.findViewById<ImageView>(R.id.output_node)
                outputNode.visibility = View.VISIBLE
                outputNode.tag = "output_${step.id}_${outputDef.id}"
                contentContainer.addView(row)
            }

            val behavior = module.blockBehavior
            val isDeletable = when {
                position == 0 -> false
                behavior.isIndividuallyDeletable -> true
                behavior.type == BlockType.BLOCK_START -> true
                behavior.type == BlockType.NONE -> true
                else -> false
            }
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        private fun getCategoryColor(category: String): Int {
            return when (category) {
                "设备" -> R.color.category_device
                "逻辑控制" -> R.color.category_logic
                "变量" -> R.color.category_variable
                "触发器" -> R.color.category_trigger
                else -> com.google.android.material.R.color.material_dynamic_neutral30
            }
        }
    }
}