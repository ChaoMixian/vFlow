package com.chaomixian.vflow.ui.workflow_editor

import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType

internal object ActionEditorViewStateReader {
    fun readParameterValue(
        view: View,
        inputDefinition: InputDefinition,
        currentValue: Any?
    ): Any? {
        if (inputDefinition.supportsRichText == false && StandardControlFactory.isVariableReference(currentValue)) {
            return currentValue
        }

        val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container) ?: return null
        if (valueContainer.childCount == 0) return null

        val rawValue = StandardControlFactory.readValueFromInputRow(view, inputDefinition) ?: return null
        return convertValue(rawValue, inputDefinition)
    }

    private fun convertValue(
        rawValue: Any,
        inputDefinition: InputDefinition
    ): Any? {
        return when (inputDefinition.staticType) {
            ParameterType.NUMBER -> {
                if (inputDefinition.supportsRichText) {
                    rawValue
                } else {
                    val stringValue = rawValue.toString()
                    stringValue.toLongOrNull() ?: stringValue.toDoubleOrNull()
                }
            }
            else -> rawValue
        }
    }
}
