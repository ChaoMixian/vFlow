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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
                workflowManager.getWorkflow(workflowId)?.name ?: "未知/已删除的工作流"
            } else {
                "未选择"
            }
        }

        updateSelectedWorkflowText(currentParameters["workflow_id"] as? String)

        holder.selectButton.setOnClickListener {
            val allWorkflows = workflowManager.getAllWorkflows()
            val workflowNames = allWorkflows.map { it.name }.toTypedArray()
            val workflowIds = allWorkflows.map { it.id }.toTypedArray()

            MaterialAlertDialogBuilder(context)
                .setTitle("选择要调用的工作流")
                .setItems(workflowNames) { _, which ->
                    val selectedId = workflowIds[which]
                    updateSelectedWorkflowText(selectedId)
                    // 手动更新参数并通知变更
                    (currentParameters as MutableMap<String, Any?>)["workflow_id"] = selectedId
                    onParametersChanged()
                }
                .show()
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