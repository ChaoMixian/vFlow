// main/java/com/chaomixian/vflow/core/workflow/module/logic/StopWorkflowModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StopWorkflowModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val targetModeChipGroup: ChipGroup = view.findViewById(R.id.cg_target_mode)
        val chipTargetCurrent: Chip = view.findViewById(R.id.chip_target_current)
        val chipTargetOther: Chip = view.findViewById(R.id.chip_target_other)

        val workflowSelectorContainer: LinearLayout = view.findViewById(R.id.container_workflow_selector)
        val selectedWorkflowText: TextView = view.findViewById(R.id.text_selected_workflow)
        val selectButton: Button = view.findViewById(R.id.button_select_workflow)
    }

    override fun getHandledInputIds(): Set<String> = setOf("target", "workflow_id")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_stop_workflow_editor, parent, false)
        val holder = ViewHolder(view)
        val workflowManager = WorkflowManager(context)

        // 恢复状态：目标模式
        val target = currentParameters["target"] as? String ?: StopWorkflowModule.TARGET_CURRENT
        when (target) {
            StopWorkflowModule.TARGET_CURRENT -> holder.chipTargetCurrent.isChecked = true
            StopWorkflowModule.TARGET_OTHER -> holder.chipTargetOther.isChecked = true
        }

        // 恢复状态：选中的工作流
        val workflowId = currentParameters["workflow_id"] as? String
        fun updateSelectedWorkflowText(id: String?) {
            holder.selectedWorkflowText.text = if (id != null) {
                workflowManager.getWorkflow(id)?.name ?: "未知/已删除的工作流"
            } else {
                "未选择"
            }
        }
        updateSelectedWorkflowText(workflowId)

        // UI 逻辑：根据选择的模式显示/隐藏工作流选择器
        fun updateVisibility() {
            val isOtherMode = holder.chipTargetOther.isChecked
            holder.workflowSelectorContainer.isVisible = isOtherMode
        }

        updateVisibility()

        // 监听器：模式切换
        holder.targetModeChipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateVisibility()
            onParametersChanged()
        }

        // 监听器：选择工作流
        holder.selectButton.setOnClickListener {
            val allWorkflows = workflowManager.getAllWorkflows()
            val workflowNames = allWorkflows.map { it.name }.toTypedArray()
            val workflowIds = allWorkflows.map { it.id }.toTypedArray()

            MaterialAlertDialogBuilder(context)
                .setTitle("选择要停止的工作流")
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
        val h = holder as ViewHolder
        val target = if (h.chipTargetCurrent.isChecked) {
            StopWorkflowModule.TARGET_CURRENT
        } else {
            StopWorkflowModule.TARGET_OTHER
        }

        // workflow_id 在点击对话框时就已经被设置，这里不需要额外读取
        // 只需要返回 target 参数即可
        return mapOf("target" to target)
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): View? = null
}
