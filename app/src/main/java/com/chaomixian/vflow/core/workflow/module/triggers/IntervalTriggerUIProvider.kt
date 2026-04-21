package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.StandardControlFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class IntervalTriggerViewHolder(
    view: View,
    val intervalInput: TextInputEditText,
    val unitDropdown: TextInputLayout
) : CustomEditorViewHolder(view)

class IntervalTriggerUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("interval", "unit")

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_interval_trigger_editor, parent, false)
        val intervalInput = view.findViewById<TextInputEditText>(R.id.et_interval_value)
        val unitDropdown = view.findViewById<TextInputLayout>(R.id.layout_interval_unit)
        val holder = IntervalTriggerViewHolder(view, intervalInput, unitDropdown)

        val module = IntervalTriggerModule()
        val unitInput = module.getInputs().first { it.id == "unit" }
        val rawInterval = currentParameters["interval"] as? Number
        intervalInput.setText((rawInterval?.toLong() ?: 1L).toString())
        intervalInput.inputType = InputType.TYPE_CLASS_NUMBER
        intervalInput.addTextChangedListener(SimpleTextWatcher { onParametersChanged() })

        val rawUnit = currentParameters["unit"] as? String
        val currentUnit = unitInput.normalizeEnumValue(rawUnit) ?: IntervalTriggerModule.UNIT_MINUTE
        StandardControlFactory.bindDropdown(
            textInputLayout = unitDropdown,
            options = unitInput.options,
            selectedValue = currentUnit,
            onItemSelectedCallback = {
                if (unitDropdown.tag != it) {
                    unitDropdown.tag = it
                    onParametersChanged()
                }
            },
            optionsStringRes = unitInput.optionsStringRes
        )
        unitDropdown.tag = currentUnit

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as IntervalTriggerViewHolder
        val interval = h.intervalInput.text?.toString()?.toLongOrNull() ?: 1L
        return mapOf(
            "interval" to interval,
            "unit" to (StandardControlFactory.getDropdownValue(h.unitDropdown) ?: IntervalTriggerModule.UNIT_MINUTE)
        )
    }
}
