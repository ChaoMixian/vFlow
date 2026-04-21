package com.chaomixian.vflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.ui.workflow_list.WorkflowListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkflowListUiState(
    val items: List<WorkflowListItem> = emptyList(),
    val isLoading: Boolean = true,
    val executionStateVersion: Int = 0,
    val openFolder: WorkflowFolder? = null,
    val folderWorkflows: List<Workflow> = emptyList(),
)

class WorkflowListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkflowListUiState())
    val uiState: StateFlow<WorkflowListUiState> = _uiState.asStateFlow()

    fun setItems(items: List<WorkflowListItem>) {
        _uiState.update { it.copy(items = items, isLoading = false) }
    }

    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    fun bumpExecutionStateVersion() {
        _uiState.update { state ->
            state.copy(executionStateVersion = state.executionStateVersion + 1)
        }
    }

    fun openFolder(folder: WorkflowFolder, workflows: List<Workflow>) {
        _uiState.update { state ->
            state.copy(openFolder = folder, folderWorkflows = workflows)
        }
    }

    fun updateFolderWorkflows(workflows: List<Workflow>) {
        _uiState.update { state ->
            if (state.openFolder == null) state else state.copy(folderWorkflows = workflows)
        }
    }

    fun closeFolder() {
        _uiState.update { state ->
            state.copy(openFolder = null, folderWorkflows = emptyList())
        }
    }
}
