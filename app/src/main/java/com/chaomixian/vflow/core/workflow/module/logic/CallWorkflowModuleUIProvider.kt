// main/java/com/chaomixian/vflow/core/workflow/module/logic/CallWorkflowModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.common.SearchableWorkflowDialog
import com.chaomixian.vflow.ui.common.WorkflowDialogItem

class CallWorkflowModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val selectedWorkflowText: TextView = view.findViewById(R.id.text_selected_workflow)
        val selectButton: Button = view.findViewById(R.id.button_select_workflow)
    }

    override fun getHandledInputIds(): Set<String> = setOf("workflow_id")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_call_workflow_editor, parent, false)
        val holder = ViewHolder(view)
        val workflowManager = WorkflowManager(context)

        fun updateSelectedWorkflowText(workflowId: String?) {
            holder.selectedWorkflowText.text = if (workflowId != null) {
                workflowManager.getWorkflow(workflowId)?.name ?: context.getString(R.string.summary_unknown_workflow)
            } else {
                context.getString(R.string.summary_no_workflow_selected)
            }
        }

        updateSelectedWorkflowText(currentParameters["workflow_id"] as? String)

        holder.selectButton.setOnClickListener {
            val allWorkflows = workflowManager.getAllWorkflows()
            SearchableWorkflowDialog.show(
                context = context,
                titleResId = R.string.dialog_call_workflow_select_title,
                items = allWorkflows.map { WorkflowDialogItem(id = it.id, name = it.name) },
                onSelected = {
                    val selectedId = it.id
                    updateSelectedWorkflowText(selectedId)
                    (currentParameters as MutableMap<String, Any?>)["workflow_id"] = selectedId
                    onParametersChanged()
                }
            )
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        // workflow_id 在点击对话框时就已经被设置，这里不需要额外读取
        return emptyMap()
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
