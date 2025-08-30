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
import androidx.core.graphics.drawable.DrawableCompat
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

    // ... (其他方法不变)
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition > 0 && toPosition > 0) {
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
        // ... (UI 控件引用)
        private val context: Context = itemView.context
        private val nameTextView: TextView = itemView.findViewById(R.id.text_view_action_name)
        private val actionIcon: ImageView = itemView.findViewById(R.id.icon_action_type)
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_action_item)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val inputsContainer: LinearLayout = itemView.findViewById(R.id.container_inputs)
        private val outputsContainer: LinearLayout = itemView.findViewById(R.id.container_outputs)


        fun bind(step: ActionStep, position: Int) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            // 1. 设置基础信息
            nameTextView.text = module.metadata.name
            actionIcon.setImageResource(module.metadata.iconRes)
            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()
            val categoryColor = ContextCompat.getColor(context, getCategoryColor(module.metadata.category))
            cardView.setCardBackgroundColor(categoryColor)

            // 2. 动态填充输入区域
            inputsContainer.removeAllViews()
            module.getInputs().forEach { inputDef ->
                val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, inputsContainer, false)

                val inputNode = row.findViewById<ImageView>(R.id.input_node)
                inputNode.visibility = View.VISIBLE
                inputNode.tag = "input_${step.id}_${inputDef.id}"

                val valueContainer = row.findViewById<FrameLayout>(R.id.parameter_value_container)
                val paramValue = step.parameters[inputDef.id]
                val valueStr = paramValue?.toString()

                // --- 使用 “药丸” UI ---
                val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
                val pillText = pill.findViewById<TextView>(R.id.pill_text)
                val pillBackground = pill.background.mutate() as android.graphics.drawable.GradientDrawable

                if (!valueStr.isNullOrBlank()) {
                    if (valueStr.startsWith("{{")) {
                        pillText.text = "已连接变量"
                        pillBackground.setColor(ContextCompat.getColor(context, R.color.variable_pill_color))
                    } else {
                        pillText.text = "${inputDef.name} ${valueStr}"
                        pillBackground.setColor(ContextCompat.getColor(context, R.color.static_pill_color))
                    }
                } else {
                    pillText.text = inputDef.name
                    pillBackground.setColor(ContextCompat.getColor(context, R.color.static_pill_color))
                }

                valueContainer.addView(pill)
                valueContainer.setOnClickListener { onEditClick(adapterPosition, inputDef.id) }
                inputsContainer.addView(row)
            }

            // 3. 动态填充输出区域
            outputsContainer.removeAllViews()
            module.getOutputs().forEach { outputDef ->
                val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, outputsContainer, false)
                // --- 核心修复：查找并设置 parameter_name 的文本 ---
                row.findViewById<TextView>(R.id.parameter_name).text = outputDef.name
                val outputNode = row.findViewById<ImageView>(R.id.output_node)
                outputNode.visibility = View.VISIBLE
                outputNode.tag = "output_${step.id}_${outputDef.id}"
                outputsContainer.addView(row)
            }

            // --- 核心修复：使用新的低耦合逻辑判断删除按钮可见性 ---
            val behavior = module.blockBehavior
            val isDeletable = when {
                position == 0 -> false // 触发器不能删
                behavior.isIndividuallyDeletable -> true // 模块自己声明可以删 (例如 Else)
                behavior.type == BlockType.BLOCK_START -> true // 块的开始可以删 (会删除整个块)
                behavior.type == BlockType.NONE -> true // 普通原子模块可以删
                else -> false // 其他中间/结束块默认不能删
            }
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        // ... (getCategoryColor 方法不变)
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