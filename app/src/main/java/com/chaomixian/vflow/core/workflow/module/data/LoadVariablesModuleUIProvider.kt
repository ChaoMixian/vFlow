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
        val mode = currentParameters["mode"] as? String ?: "share"

        if (workflowId != null) {
            val workflowManager = WorkflowManager(context)
            val workflow = workflowManager.getWorkflow(workflowId)
            holder.selectedWorkflowText.text = workflow?.name ?: context.getString(R.string.label_workflow_not_selected)
        }

        // 设置模式
        when (mode) {
            "share" -> holder.shareRadio.isChecked = true
            "copy" -> holder.copyRadio.isChecked = true
        }

        // 模式切换监听
        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radio_share -> "share"
                R.id.radio_copy -> "copy"
                else -> "share"
            }
            (currentParameters as MutableMap<String, Any?>)["mode"] = newMode
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

                    // 更新参数
                    (currentParameters as MutableMap<String, Any?>)["workflow_id"] = selectedId

                    // 获取所有变量名
                    val varNames = getNamedVariableNames(workflow)
                    (currentParameters as MutableMap<String, Any?>)["variable_names"] = varNames.joinToString(",")

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
        return emptyMap()
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
