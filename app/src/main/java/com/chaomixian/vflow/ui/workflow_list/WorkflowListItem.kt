package com.chaomixian.vflow.ui.workflow_list

import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder

/**
 * 工作流列表页使用的列表项模型
 */
sealed class WorkflowListItem {
    abstract val id: String

    data class WorkflowItem(val workflow: Workflow) : WorkflowListItem() {
        override val id: String = workflow.id
    }

    data class FolderItem(
        val folder: WorkflowFolder,
        val workflowCount: Int = 0,
        val searchableContent: String = "",
        val childWorkflows: List<Workflow> = emptyList()
    ) : WorkflowListItem() {
        override val id: String = folder.id
    }
}
