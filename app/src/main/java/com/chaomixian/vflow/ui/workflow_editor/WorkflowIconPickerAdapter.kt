package com.chaomixian.vflow.ui.workflow_editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowVisuals
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class WorkflowIconPickerAdapter(
    private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<WorkflowIconPickerAdapter.IconViewHolder>() {

    private val icons = WorkflowVisuals.availableIconResNames
    private var selectedIconRes: String = WorkflowVisuals.defaultIconResName()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_selector, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        holder.bind(icons[position], icons[position] == selectedIconRes)
    }

    override fun getItemCount(): Int = icons.size

    fun setSelectedIcon(iconRes: String?) {
        val normalized = WorkflowVisuals.normalizeIconResName(iconRes)
        val oldIndex = icons.indexOf(selectedIconRes)
        selectedIconRes = normalized
        val newIndex = icons.indexOf(selectedIconRes)
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val iconContainer: View = itemView.findViewById(R.id.icon_container)
        private val imageView: ImageView = itemView.findViewById(R.id.image_view_icon)

        fun bind(iconRes: String, isSelected: Boolean) {
            val resolvedId = WorkflowVisuals.resolveIconDrawableRes(iconRes)
            imageView.setImageResource(resolvedId)
            updateSelectionStyle(isSelected)

            cardView.setOnClickListener {
                val oldIndex = icons.indexOf(selectedIconRes)
                selectedIconRes = iconRes
                if (oldIndex >= 0) notifyItemChanged(oldIndex)
                notifyItemChanged(bindingAdapterPosition)
                onIconSelected(iconRes)
            }
        }

        private fun updateSelectionStyle(isSelected: Boolean) {
            val context = itemView.context
            val strokeColor = MaterialColors.getColor(
                context,
                if (isSelected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant,
                0
            )
            val containerColor = MaterialColors.getColor(
                context,
                if (isSelected) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceContainerHighest,
                0
            )
            val iconTint = MaterialColors.getColor(
                context,
                if (isSelected) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurface,
                0
            )
            cardView.strokeColor = strokeColor
            iconContainer.setBackgroundColor(containerColor)
            imageView.imageTintList = ColorStateList.valueOf(iconTint)
        }
    }
}
