package com.chaomixian.vflow.ui.workflow_editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.card.MaterialCardView
import java.util.Collections

class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ActionStepAdapter.ActionStepViewHolder>() {

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition > 0 && toPosition > 0) {
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
        holder.bind(step, position)
        holder.deleteButton.setOnClickListener { onDeleteClick(position) }
        holder.itemView.setOnClickListener { onEditClick(position) }
    }

    override fun getItemCount(): Int = actionSteps.size

    class ActionStepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.text_view_action_description)
        private val actionIcon: ImageView = itemView.findViewById(R.id.icon_action_type)
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_action_item)

        fun bind(step: ActionStep, position: Int) {
            val module = ModuleRegistry.getModule(step.moduleId)
            if (module == null) {
                descriptionTextView.text = "未知模块: ${step.moduleId}"
                actionIcon.setImageResource(R.drawable.ic_close)
                return
            }

            val indentMargin = (step.indentationLevel * 24 * itemView.context.resources.displayMetrics.density).toInt()
            (cardView.layoutParams as ViewGroup.MarginLayoutParams).leftMargin = indentMargin
            cardView.requestLayout()

            // 核心修复：根据模块行为决定是否显示删除按钮
            val behavior = module.blockBehavior.type
            val isDeletable = when {
                position == 0 -> false // 触发器
                behavior == BlockType.BLOCK_END -> false // 结束块
                behavior == BlockType.BLOCK_MIDDLE && module.id.contains(".if.") -> true // 允许删除 Else
                behavior == BlockType.BLOCK_MIDDLE -> false // 其他中间块
                else -> true
            }
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE

            actionIcon.setImageResource(module.metadata.iconRes)
            val paramsString = step.parameters.entries
                .filter { it.value != null && it.value.toString().isNotEmpty() }
                .joinToString(", ") { "${module.getParameters().find{p -> p.id == it.key}?.name ?: it.key}: ${it.value}" }

            val desc = module.metadata.description
            descriptionTextView.text = if (paramsString.isNotEmpty()) {
                "${module.metadata.name} ($paramsString)"
            } else if (desc.isNotEmpty()) {
                "${module.metadata.name}: ${desc}"
            } else {
                module.metadata.name
            }
        }
    }
}