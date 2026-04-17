package com.chaomixian.vflow.ui.workflow_editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowVisuals
import com.google.android.material.card.MaterialCardView

class WorkflowThemeColorAdapter(
    private val onColorSelected: (String) -> Unit
) : RecyclerView.Adapter<WorkflowThemeColorAdapter.ColorViewHolder>() {

    private val colors = WorkflowVisuals.themePaletteHex
    private var selectedColorHex: String = WorkflowVisuals.defaultThemeColorHex()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workflow_theme_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position], colors[position] == selectedColorHex)
    }

    override fun getItemCount(): Int = colors.size

    fun setSelectedColor(colorHex: String?) {
        val normalized = WorkflowVisuals.normalizeThemeColorHex(colorHex)
        val oldIndex = colors.indexOf(selectedColorHex)
        selectedColorHex = normalized
        val newIndex = colors.indexOf(selectedColorHex)
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val swatchView: View = itemView.findViewById(R.id.view_theme_color)
        private val selectionDot: View = itemView.findViewById(R.id.view_selected_dot)

        fun bind(colorHex: String, isSelected: Boolean) {
            val color = Color.parseColor(colorHex)
            swatchView.backgroundTintList = ColorStateList.valueOf(color)
            selectionDot.backgroundTintList = ColorStateList.valueOf(
                if (ColorUtils.calculateLuminance(color) > 0.45) Color.parseColor("#111827") else Color.WHITE
            )
            cardView.strokeColor = if (isSelected) color else ColorUtils.setAlphaComponent(color, 70)
            cardView.strokeWidth = if (isSelected) 4 else 2
            selectionDot.visibility = if (isSelected) View.VISIBLE else View.GONE

            cardView.setOnClickListener {
                val oldIndex = colors.indexOf(selectedColorHex)
                selectedColorHex = colorHex
                if (oldIndex >= 0) notifyItemChanged(oldIndex)
                notifyItemChanged(bindingAdapterPosition)
                onColorSelected(colorHex)
            }
        }
    }
}
