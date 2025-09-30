
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider

class BatteryTriggerUIProvider : ModuleUIProvider {

    private class EditorViewHolder(view: View) : CustomEditorViewHolder(view) {
        val slider: Slider = view.findViewById(R.id.slider_battery_level)
        val levelText: TextView = view.findViewById(R.id.text_battery_level)
        val chipGroup: ChipGroup = view.findViewById(R.id.chip_group_condition)
        val chipBelow: Chip = view.findViewById(R.id.chip_below)
        val chipAbove: Chip = view.findViewById(R.id.chip_above)
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return null
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_battery_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)

        val level = (currentParameters["level"] as? Number)?.toInt() ?: 50
        val aboveOrBelow = currentParameters["above_or_below"] as? String ?: "below"

        holder.slider.value = level.toFloat()
        holder.levelText.text = "$level%"

        if (aboveOrBelow == "below") {
            holder.chipBelow.isChecked = true
        } else {
            holder.chipAbove.isChecked = true
        }

        holder.slider.addOnChangeListener { _, value, _ ->
            holder.levelText.text = "${value.toInt()}%"
            onParametersChanged()
        }

        holder.chipGroup.setOnCheckedChangeListener { _, _ ->
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val editorHolder = holder as EditorViewHolder
        val level = editorHolder.slider.value.toInt()
        val aboveOrBelow = if (editorHolder.chipBelow.isChecked) "below" else "above"
        return mapOf("level" to level, "above_or_below" to aboveOrBelow)
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("level", "above_or_below")
    }
}
