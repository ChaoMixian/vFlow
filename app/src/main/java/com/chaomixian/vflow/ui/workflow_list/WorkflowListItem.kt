package com.chaomixian.vflow.ui.workflow_list

import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder

/**
 * 密封类用于统一 WorkflowListAdapter 中的工作流和文件夹项
 */
sealed class WorkflowListItem {
    abstract val id: String

    data class WorkflowItem(val workflow: Workflow) : WorkflowListItem() {
        override val id: String = workflow.id
    }

    data class FolderItem(
        val folder: WorkflowFolder,
        val workflowCount: Int = 0
    ) : WorkflowListItem() {
        override val id: String = folder.id
    }
}
