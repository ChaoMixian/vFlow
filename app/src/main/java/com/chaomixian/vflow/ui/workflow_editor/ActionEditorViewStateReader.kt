package com.chaomixian.vflow.ui.workflow_editor

import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType

internal object ActionEditorViewStateReader {
    data class ReadResult(
        val shouldUpdate: Boolean,
        val value: Any?
    )

    fun readParameterValue(
        view: View,
        inputDefinition: InputDefinition,
        currentValue: Any?
    ): ReadResult {
        if (inputDefinition.supportsRichText == false && StandardControlFactory.isVariableReference(currentValue)) {
            return ReadResult(shouldUpdate = true, value = currentValue)
        }

        val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container)
            ?: return ReadResult(shouldUpdate = false, value = null)
        if (valueContainer.childCount == 0) {
            return ReadResult(shouldUpdate = false, value = null)
        }

        val rawValue = StandardControlFactory.readValueFromInputRow(view, inputDefinition)
        if (rawValue == null) {
            return when (inputDefinition.staticType) {
                ParameterType.NUMBER -> ReadResult(shouldUpdate = true, value = null)
                else -> ReadResult(shouldUpdate = false, value = null)
            }
        }
        return ReadResult(
            shouldUpdate = true,
            value = convertValue(rawValue, inputDefinition)
        )
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
