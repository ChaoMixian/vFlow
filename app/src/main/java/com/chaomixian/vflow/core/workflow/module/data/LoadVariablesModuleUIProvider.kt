package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoadVariablesModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val selectedWorkflowText: TextView = view.findViewById(R.id.text_selected_workflow)
        val selectButton: Button = view.findViewById(R.id.button_select_workflow)
        val radioGroup: RadioGroup = view.findViewById(R.id.radio_mode)
        val shareRadio: RadioButton = view.findViewById(R.id.radio_share)
        val copyRadio: RadioButton = view.findViewById(R.id.radio_copy)
        val helpText: TextView = view.findViewById(R.id.text_help)

        var selectedWorkflowId: String? = null
        var selectedVariableNames: List<String> = emptyList()
    }

    override fun getHandledInputIds(): Set<String> = setOf("workflow_id", "variable_names", "mode")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_load_variables_editor, parent, false)
        val holder = ViewHolder(view)

        // 恢复状态
        val workflowId = currentParameters["workflow_id"] as? String
        val mode = currentParameters["mode"] as? String ?: LoadVariablesModule.MODE_SHARE
        holder.selectedWorkflowId = workflowId
        holder.selectedVariableNames = (currentParameters["variable_names"] as? String)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (workflowId != null) {
            val workflowManager = WorkflowManager(context)
            val workflow = workflowManager.getWorkflow(workflowId)
            holder.selectedWorkflowText.text = workflow?.name ?: context.getString(R.string.label_workflow_not_selected)
        } else {
            holder.selectedWorkflowText.text = context.getString(R.string.label_workflow_not_selected)
        }

        // 设置模式
        when (mode) {
            LoadVariablesModule.MODE_COPY -> holder.copyRadio.isChecked = true
            else -> holder.shareRadio.isChecked = true
        }

        // 模式切换监听
        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_copy -> holder.copyRadio.isChecked = true
                else -> holder.shareRadio.isChecked = true
            }
            onParametersChanged()
        }

        holder.selectButton.setOnClickListener {
            val workflowManager = WorkflowManager(context)
            val allWorkflows = workflowManager.getAllWorkflows()
            val workflowNames = allWorkflows.map { it.name }.toTypedArray()
            val workflowIds = allWorkflows.map { it.id }.toTypedArray()

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.label_load_variables_select_workflow)
                .setItems(workflowNames) { _, which ->
                    val selectedId = workflowIds[which]
                    val workflow = workflowManager.getWorkflow(selectedId)

                    holder.selectedWorkflowText.text = workflow?.name ?: context.getString(R.string.label_workflow_not_selected)

                    holder.selectedWorkflowId = selectedId

                    // 获取所有变量名
                    val varNames = getNamedVariableNames(workflow)
                    holder.selectedVariableNames = varNames

                    onParametersChanged()
                }
                .show()
        }

        // 复制模式帮助文本切换
        holder.copyRadio.setOnCheckedChangeListener { _, isChecked ->
            holder.helpText.text = if (isChecked) {
                context.getString(R.string.help_load_variables_copy)
            } else {
                context.getString(R.string.help_load_variables_share)
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "workflow_id" to h.selectedWorkflowId,
            "variable_names" to h.selectedVariableNames.joinToString(","),
            "mode" to if (h.copyRadio.isChecked) LoadVariablesModule.MODE_COPY else LoadVariablesModule.MODE_SHARE
        )
    }

    private fun getNamedVariableNames(workflow: Workflow?): List<String> {
        if (workflow == null) return emptyList()
        return workflow.steps
            .filter { it.moduleId == "vflow.variable.create" }
            .mapNotNull { step ->
                step.parameters["variableName"] as? String
            }
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
