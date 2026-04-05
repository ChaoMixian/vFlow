package com.chaomixian.vflow.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ModuleDetailDialog {
    fun show(context: Context, module: ActionModule) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_module_detail, null)
        val titleView = dialogView.findViewById<TextView>(R.id.tv_detail_title)
        val idView = dialogView.findViewById<TextView>(R.id.tv_detail_id)
        val descriptionView = dialogView.findViewById<TextView>(R.id.tv_detail_description)
        val inputsView = dialogView.findViewById<TextView>(R.id.tv_detail_inputs)
        val outputsView = dialogView.findViewById<TextView>(R.id.tv_detail_outputs)
        val closeButton = dialogView.findViewById<Button>(R.id.btn_close_dialog)

        val localizedName = module.metadata.getLocalizedName(context)
        titleView.text = "$localizedName - ${context.getString(R.string.label_module_details)}"
        idView.text = "${context.getString(R.string.label_module_id)}: ${module.id}"
        descriptionView.text = module.metadata.getLocalizedDescription(context)

        val inputs = module.getInputs()
        inputsView.text = if (inputs.isEmpty()) {
            context.getString(R.string.label_no_input_params)
        } else {
            buildString {
                inputs.forEachIndexed { index, input ->
                    append("${index + 1}. ${input.getLocalizedName(context)} (${input.id})\n")
                    append("   ${context.getString(R.string.label_param_type)}: ${input.staticType.name}")
                    if (index < inputs.lastIndex) append("\n")
                }
            }
        }

        val outputs = try {
            module.getOutputs(null)
        } catch (_: Exception) {
            emptyList()
        }
        outputsView.text = if (outputs.isEmpty()) {
            context.getString(R.string.label_no_output_vars)
        } else {
            buildString {
                outputs.forEachIndexed { index, output ->
                    append("${index + 1}. ${output.getLocalizedName(context)} (${output.id})\n")
                    append("   ${context.getString(R.string.label_param_type)}: ${output.typeName}")
                    if (index < outputs.lastIndex) append("\n")
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
