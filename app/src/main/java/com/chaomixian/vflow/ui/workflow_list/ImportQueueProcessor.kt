// 文件: main/java/com/chaomixian/vflow/ui/workflow_list/ImportQueueProcessor.kt
package com.chaomixian.vflow.ui.workflow_list

import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.LinkedList
import java.util.UUID

/**
 * 导入队列处理器
 * 处理工作流导入的队列和冲突解决
 */
class ImportQueueProcessor(
    private val context: Context,
    private val workflowManager: WorkflowManager,
    private val onImportCompleted: () -> Unit
) {
    private val importQueue = LinkedList<Workflow>()
    private var conflictChoice = ConflictChoice.ASK

    fun startImport(workflows: List<Workflow>) {
        importQueue.clear()
        importQueue.addAll(workflows)
        conflictChoice = ConflictChoice.ASK
        processNextInImportQueue()
    }

    private fun processNextInImportQueue() {
        if (importQueue.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_import_completed), Toast.LENGTH_SHORT).show()
            onImportCompleted()
            return
        }

        val workflowToImport = importQueue.poll() ?: return
        val existingWorkflow = workflowManager.getWorkflow(workflowToImport.id)

        if (existingWorkflow == null) {
            workflowManager.saveWorkflow(workflowToImport)
            processNextInImportQueue()
        } else {
            when (conflictChoice) {
                ConflictChoice.REPLACE_ALL -> {
                    handleReplace(workflowToImport)
                    processNextInImportQueue()
                }
                ConflictChoice.KEEP_ALL -> {
                    handleKeepBoth(workflowToImport)
                    processNextInImportQueue()
                }
                ConflictChoice.ASK -> showConflictDialog(workflowToImport, existingWorkflow)
            }
        }
    }

    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_import_conflict, null)
        val rememberChoiceCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_remember_choice)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dialog_import_conflict_title))
            .setMessage(context.getString(R.string.dialog_import_conflict_message, existing.name, existing.id.substring(0, 8), toImport.name))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.dialog_button_keep_both)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.KEEP_ALL
                handleKeepBoth(toImport)
                processNextInImportQueue()
            }
            .setNegativeButton(context.getString(R.string.dialog_button_replace)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.REPLACE_ALL
                handleReplace(toImport)
                processNextInImportQueue()
            }
            .setNeutralButton(context.getString(R.string.dialog_button_skip)) { _, _ -> processNextInImportQueue() }
            .setCancelable(false)
            .show()
    }

    private fun handleReplace(toImport: Workflow) {
        workflowManager.saveWorkflow(toImport)
    }

    private fun handleKeepBoth(toImport: Workflow) {
        val newWorkflow = toImport.copy(
            id = UUID.randomUUID().toString(),
            name = "${toImport.name} (副本)"
        )
        workflowManager.saveWorkflow(newWorkflow)
    }

    private enum class ConflictChoice {
        ASK, REPLACE_ALL, KEEP_ALL
    }
}
