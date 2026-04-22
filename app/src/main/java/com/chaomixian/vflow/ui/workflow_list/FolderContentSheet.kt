package com.chaomixian.vflow.ui.workflow_list

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.model.Workflow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class FolderContentSheetActions(
    val onDismiss: () -> Unit,
    val onOpenWorkflow: (Workflow) -> Unit,
    val onToggleFavorite: (Workflow) -> Unit,
    val onToggleEnabled: (Workflow, Boolean) -> Unit,
    val onDeleteWorkflow: (Workflow) -> Unit,
    val onDuplicateWorkflow: (Workflow) -> Unit,
    val onExportWorkflow: (Workflow) -> Unit,
    val onMoveOutFolder: (Workflow) -> Unit,
    val onCopyWorkflowId: (Workflow) -> Unit,
    val onExecuteWorkflow: (Workflow) -> Unit,
    val onExecuteWorkflowDelayed: (Workflow, Long) -> Unit,
    val onPersistWorkflowOrder: (List<Workflow>) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderContentSheet(
    folderName: String,
    workflows: List<Workflow>,
    executionStateVersion: Int,
    actions: FolderContentSheetActions,
) {
    val displayWorkflows = remember { mutableStateListOf<Workflow>() }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(workflows) {
        displayWorkflows.clear()
        displayWorkflows.addAll(workflows)
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = PaddingValues(bottom = 64.dp)
    ) { from, to ->
        val fromWorkflow = displayWorkflows.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        displayWorkflows.removeAt(from.index)
        displayWorkflows.add(to.index, fromWorkflow)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = actions.onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            if (displayWorkflows.isEmpty()) {
                Text(
                    text = stringResource(R.string.folder_empty_workflows),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp)
                ) {
                itemsIndexed(
                    items = displayWorkflows,
                    key = { _, workflow -> workflow.id }
                ) { _, workflow ->
                    var suppressOpenUntil by remember(workflow.id) { mutableLongStateOf(0L) }
                        val topMenuActions = listOf(
                            WorkflowMenuItemAction(
                                textRes = R.string.workflow_item_menu_move_out_folder,
                                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                                onClick = { actions.onMoveOutFolder(workflow) }
                            ),
                            WorkflowMenuItemAction(
                                textRes = R.string.workflow_item_menu_duplicate,
                                icon = Icons.Outlined.ContentCopy,
                                onClick = { actions.onDuplicateWorkflow(workflow) }
                            ),
                            WorkflowMenuItemAction(
                                textRes = R.string.workflow_item_menu_delete,
                                icon = Icons.Outlined.DeleteOutline,
                                onClick = { actions.onDeleteWorkflow(workflow) }
                            ),
                        )
                        val regularMenuActions = listOf(
                            WorkflowMenuItemAction(
                                textRes = R.string.workflow_item_menu_export_single,
                                icon = Icons.Outlined.Download,
                                onClick = { actions.onExportWorkflow(workflow) }
                            ),
                            WorkflowMenuItemAction(
                                textRes = R.string.workflow_item_menu_copy_id,
                                icon = Icons.Outlined.Badge,
                                onClick = { actions.onCopyWorkflowId(workflow) }
                            ),
                        )

                        ReorderableItem(
                            state = reorderableState,
                            key = workflow.id
                        ) { isDragging ->
                            WorkflowCard(
                                workflow = workflow,
                                executionStateVersion = executionStateVersion,
                                isDragging = isDragging,
                                topMenuActions = topMenuActions,
                                regularMenuActions = regularMenuActions,
                            modifier = Modifier.fillMaxWidth(),
                            dragHandleModifier = with(this) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        suppressOpenUntil = SystemClock.uptimeMillis() + 250L
                                    },
                                    onDragStopped = {
                                        suppressOpenUntil = SystemClock.uptimeMillis() + 250L
                                        actions.onPersistWorkflowOrder(
                                            displayWorkflows.mapIndexed { index, item ->
                                                item.copy(order = index)
                                                }
                                            )
                                        }
                                    )
                                },
                            onOpenWorkflow = {
                                if (SystemClock.uptimeMillis() < suppressOpenUntil) return@WorkflowCard
                                actions.onOpenWorkflow(workflow)
                            },
                                onToggleFavorite = { actions.onToggleFavorite(workflow) },
                                onToggleEnabled = { enabled -> actions.onToggleEnabled(workflow, enabled) },
                                onExecuteWorkflow = { actions.onExecuteWorkflow(workflow) },
                                onExecuteWorkflowDelayed = { delayMs ->
                                    actions.onExecuteWorkflowDelayed(workflow, delayMs)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
