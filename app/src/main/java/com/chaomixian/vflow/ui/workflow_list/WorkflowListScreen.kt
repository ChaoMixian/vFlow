package com.chaomixian.vflow.ui.workflow_list

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.WorkflowVisuals
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.SearchBarCard
import com.chaomixian.vflow.ui.common.SearchEmptyStateCard
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.chaomixian.vflow.ui.common.matchesSearch
import com.chaomixian.vflow.ui.common.normalizeSearchQuery
import com.chaomixian.vflow.ui.viewmodel.WorkflowListUiState
import com.chaomixian.vflow.ui.workflow_list.WorkflowListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class WorkflowListScreenActions(
    val onCreateWorkflow: () -> Unit,
    val onOpenWorkflow: (Workflow) -> Unit,
    val onToggleFavorite: (Workflow) -> Unit,
    val onToggleEnabled: (Workflow, Boolean) -> Unit,
    val onDeleteWorkflow: (Workflow) -> Unit,
    val onDuplicateWorkflow: (Workflow) -> Unit,
    val onExportWorkflow: (Workflow) -> Unit,
    val onExecuteWorkflow: (Workflow) -> Unit,
    val onExecuteWorkflowDelayed: (Workflow, Long) -> Unit,
    val onAddShortcut: (Workflow) -> Unit,
    val onAddToTile: (Workflow) -> Unit,
    val onCopyWorkflowId: (Workflow) -> Unit,
    val onMoveWorkflowToFolder: (Workflow) -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onCloseFolder: () -> Unit,
    val onMoveWorkflowOutOfFolder: (Workflow) -> Unit,
    val onRenameFolder: (String) -> Unit,
    val onDeleteFolder: (String) -> Unit,
    val onExportFolder: (String) -> Unit,
    val onPersistWorkflowOrder: (List<Workflow>) -> Unit,
    val onMoveWorkflowToFolderByDrop: (Workflow, String) -> Unit,
)

data class WorkflowMenuItemAction(
    val textRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    uiState: WorkflowListUiState,
    actions: WorkflowListScreenActions,
    extraBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(searchQuery) { normalizeSearchQuery(searchQuery) }
    val focusManager = LocalFocusManager.current
    val displayItems = remember { mutableStateListOf<WorkflowListItem>() }
    val lazyListState = rememberLazyListState()
    val folderSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isSearching = normalizedQuery.isNotBlank()
    var showLoadingCard by remember { mutableStateOf(false) }
    val filteredItems = if (isSearching) {
        buildList {
            displayItems.forEach { item ->
                when (item) {
                    is WorkflowListItem.WorkflowItem -> {
                        if (matchesSearch(normalizedQuery, item.workflow.name, item.workflow.description)) {
                            add(item)
                        }
                    }

                    is WorkflowListItem.FolderItem -> {
                        if (matchesSearch(normalizedQuery, item.folder.name)) {
                            add(item)
                        }
                        item.childWorkflows.forEach { workflow ->
                            if (matchesSearch(normalizedQuery, workflow.name, workflow.description)) {
                                add(WorkflowListItem.WorkflowItem(workflow))
                            }
                        }
                    }
                }
            }
        }
    } else {
        displayItems
    }

    LaunchedEffect(uiState.items) {
        displayItems.clear()
        displayItems.addAll(uiState.items)
    }

    LaunchedEffect(uiState.isLoading, displayItems.size, isSearching) {
        if (uiState.isLoading && displayItems.isEmpty() && !isSearching) {
            delay(180)
            showLoadingCard = uiState.isLoading && displayItems.isEmpty() && !isSearching
        } else {
            showLoadingCard = false
        }
    }

    LaunchedEffect(uiState.openFolder?.id) {
        if (uiState.openFolder != null) {
            folderSheetState.show()
        } else if (folderSheetState.isVisible) {
            folderSheetState.hide()
        }
    }

    val reorderableContentStartIndex = 1
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = PaddingValues(
            bottom = extraBottomPadding + 88.dp
        )
    ) { from, to ->
        val fromDataIndex = from.index - reorderableContentStartIndex
        val toDataIndex = to.index - reorderableContentStartIndex
        if (fromDataIndex !in displayItems.indices || toDataIndex !in displayItems.indices) {
            return@rememberReorderableLazyListState
        }

        val fromItem = displayItems[fromDataIndex]
        val toItem = displayItems[toDataIndex]
        if (fromItem is WorkflowListItem.WorkflowItem && toItem is WorkflowListItem.WorkflowItem) {
            displayItems.removeAt(fromDataIndex)
            displayItems.add(toDataIndex, fromItem)
        }
    }

    fun persistOrder() {
        actions.onPersistWorkflowOrder(
            displayItems
                .filterIsInstance<WorkflowListItem.WorkflowItem>()
                .map { it.workflow }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                },
            contentPadding = PaddingValues(
                start = 0.dp,
                top = 12.dp,
                end = 0.dp,
                bottom = extraBottomPadding + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SearchBarCard(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholderRes = R.string.workflow_search_placeholder,
                    clearContentDescriptionRes = R.string.workflow_search_clear,
                    onClearFocus = { focusManager.clearFocus() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (showLoadingCard) {
                item {
                    WorkflowLoadingState(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    if (isSearching) {
                        SearchEmptyStateCard(
                            titleRes = R.string.workflow_search_no_results,
                            hintRes = R.string.workflow_search_no_results_hint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        EmptyWorkflowState(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredItems,
                    key = { _, item -> item.id }
                ) { _, item ->
                    when (item) {
                        is WorkflowListItem.WorkflowItem -> {
                            val workflow = item.workflow
                            var suppressOpenUntil by remember(item.id) { mutableLongStateOf(0L) }
                            val topMenuActions = listOf(
                                WorkflowMenuItemAction(
                                    textRes = R.string.dialog_move_to_folder_title,
                                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                                    onClick = { actions.onMoveWorkflowToFolder(workflow) }
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
                            val regularMenuActions = buildList {
                                if (workflow.hasManualTrigger()) {
                                    add(
                                        WorkflowMenuItemAction(
                                            textRes = R.string.workflow_item_menu_add_shortcut,
                                            icon = Icons.AutoMirrored.Outlined.AddToHomeScreen,
                                            onClick = { actions.onAddShortcut(workflow) }
                                        )
                                    )
                                }
                                add(
                                    WorkflowMenuItemAction(
                                        textRes = R.string.workflow_item_menu_export_single,
                                        icon = Icons.Outlined.Download,
                                        onClick = { actions.onExportWorkflow(workflow) }
                                    )
                                )
                                add(
                                    WorkflowMenuItemAction(
                                        textRes = R.string.workflow_item_menu_copy_id,
                                        icon = Icons.Outlined.Badge,
                                        onClick = { actions.onCopyWorkflowId(workflow) }
                                    )
                                )
                                if (workflow.hasManualTrigger()) {
                                    add(
                                        WorkflowMenuItemAction(
                                            textRes = R.string.workflow_item_menu_add_to_tile,
                                            icon = Icons.Outlined.DashboardCustomize,
                                            onClick = { actions.onAddToTile(workflow) }
                                        )
                                    )
                                }
                            }
                            if (isSearching) {
                                WorkflowCard(
                                    workflow = workflow,
                                    executionStateVersion = uiState.executionStateVersion,
                                    isDragging = false,
                                    topMenuActions = topMenuActions,
                                    regularMenuActions = regularMenuActions,
                                    modifier = Modifier.fillMaxWidth(),
                                    onOpenWorkflow = { actions.onOpenWorkflow(workflow) },
                                    onToggleFavorite = { actions.onToggleFavorite(workflow) },
                                    onToggleEnabled = { enabled -> actions.onToggleEnabled(workflow, enabled) },
                                    onExecuteWorkflow = { actions.onExecuteWorkflow(workflow) },
                                    onExecuteWorkflowDelayed = { delayMs ->
                                        actions.onExecuteWorkflowDelayed(workflow, delayMs)
                                    }
                                )
                            } else {
                                ReorderableItem(
                                    state = reorderableState,
                                    key = item.id
                                ) { isDragging ->
                                    WorkflowCard(
                                        workflow = workflow,
                                        executionStateVersion = uiState.executionStateVersion,
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
                                                    persistOrder()
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

                        is WorkflowListItem.FolderItem -> FolderCard(
                            folder = item.folder,
                            workflowCount = item.workflowCount,
                            modifier = Modifier.fillMaxWidth(),
                            actions = actions
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = actions.onCreateWorkflow,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = extraBottomPadding + 16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_add_24),
                contentDescription = stringResource(R.string.add_workflow)
            )
        }

        uiState.openFolder?.let { folder ->
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { actions.onCloseFolder() },
                sheetState = folderSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = null,
            ) {
                FolderContentSheet(
                    folderName = folder.name,
                    workflows = uiState.folderWorkflows,
                    executionStateVersion = uiState.executionStateVersion,
                    actions = FolderContentSheetActions(
                        onDismiss = {
                            scope.launch {
                                folderSheetState.hide()
                                actions.onCloseFolder()
                            }
                        },
                        onOpenWorkflow = actions.onOpenWorkflow,
                        onToggleFavorite = actions.onToggleFavorite,
                        onToggleEnabled = actions.onToggleEnabled,
                        onDeleteWorkflow = actions.onDeleteWorkflow,
                        onDuplicateWorkflow = actions.onDuplicateWorkflow,
                        onExportWorkflow = actions.onExportWorkflow,
                        onMoveOutFolder = actions.onMoveWorkflowOutOfFolder,
                        onCopyWorkflowId = actions.onCopyWorkflowId,
                        onExecuteWorkflow = actions.onExecuteWorkflow,
                        onExecuteWorkflowDelayed = actions.onExecuteWorkflowDelayed,
                        onPersistWorkflowOrder = actions.onPersistWorkflowOrder,
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkflowState(modifier: Modifier = Modifier) {
    SearchEmptyStateCard(
        titleRes = R.string.text_no_workflows,
        hintRes = R.string.workflow_empty_hint,
        modifier = modifier
    )
}

@Composable
private fun WorkflowLoadingState(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(28.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun WorkflowCard(
    workflow: Workflow,
    executionStateVersion: Int,
    isDragging: Boolean,
    topMenuActions: List<WorkflowMenuItemAction>,
    regularMenuActions: List<WorkflowMenuItemAction>,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onOpenWorkflow: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onExecuteWorkflow: () -> Unit,
    onExecuteWorkflowDelayed: (Long) -> Unit,
) {
    val context = LocalContext.current
    val colorfulCardsEnabled = remember { ThemeUtils.isColorfulWorkflowCardsEnabled(context) }
    val visualColors = remember(workflow.cardThemeColor) {
        WorkflowVisuals.resolveCardColors(context, workflow.cardThemeColor)
    }
    val missingPermissions = PermissionManager.getMissingPermissions(context, workflow)
    val requiredPermissions = workflow.allSteps
        .mapNotNull { step -> ModuleRegistry.getModule(step.moduleId)?.getRequiredPermissions(step) }
        .flatten()
        .distinct()
        .map { it.getLocalizedName(context) }
    val isManualTrigger = workflow.hasManualTrigger()
    val hasAutoTriggers = workflow.hasAutoTriggers()
    val isRunning = remember(workflow.id, executionStateVersion) {
        WorkflowExecutor.isRunning(workflow.id)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var delayedMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp)
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (colorfulCardsEnabled) {
                Color(visualColors.cardBackground)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpenWorkflow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.then(dragHandleModifier)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (colorfulCardsEnabled) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = Color(visualColors.iconBackground),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    WorkflowVisuals.resolveIconDrawableRes(workflow.cardIconRes)
                                ),
                                contentDescription = null,
                                tint = Color(visualColors.iconTint),
                                modifier = Modifier.padding(9.dp)
                            )
                        }
                        SpacerWidth(12.dp)
                    }

                    Text(
                        text = workflow.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2
                    )

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.workflow_item_more_options)
                            )
                        }
                        DropdownMenuPopup(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .widthIn(min = 156.dp, max = 236.dp),
                                containerColor = MenuDefaults.groupStandardContainerColor,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        topMenuActions.forEach { item ->
                                            WorkflowQuickActionButton(
                                                icon = item.icon,
                                                contentDescription = stringResource(item.textRes),
                                                onClick = {
                                                    menuExpanded = false
                                                    item.onClick()
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    regularMenuActions.forEach { item ->
                                        WorkflowFlatMenuItem(
                                            text = stringResource(item.textRes),
                                            icon = item.icon,
                                            onClick = {
                                                menuExpanded = false
                                                item.onClick()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (missingPermissions.isNotEmpty()) {
                        WorkflowChip(
                            label = stringResource(R.string.workflow_chip_missing_permissions),
                            iconRes = R.drawable.rounded_security_24,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    WorkflowChip(
                        label = stringResource(R.string.workflow_chip_steps, workflow.steps.size),
                        iconRes = R.drawable.rounded_dashboard_fill_24,
                        containerColor = if (colorfulCardsEnabled) {
                            Color(visualColors.chipBackground)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (colorfulCardsEnabled) {
                            Color(visualColors.iconTint)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    requiredPermissions.forEach { permission ->
                        WorkflowChip(
                            label = permission,
                            iconRes = R.drawable.rounded_security_24,
                            containerColor = if (colorfulCardsEnabled) {
                                Color(visualColors.chipBackground)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (colorfulCardsEnabled) {
                                Color(visualColors.iconTint)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        painter = painterResource(
                            if (workflow.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                        ),
                        contentDescription = stringResource(R.string.workflow_item_favorite),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (hasAutoTriggers) {
                    Switch(
                        checked = workflow.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }

                if (isManualTrigger && !hasAutoTriggers) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = onExecuteWorkflow,
                                    onLongClick = { delayedMenuExpanded = true }
                                ),
                            color = if (colorfulCardsEnabled) {
                                Color(visualColors.accentBackground)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isRunning) R.drawable.rounded_pause_fill_24
                                    else R.drawable.rounded_play_arrow_fill_24
                                ),
                                contentDescription = stringResource(R.string.workflow_item_execute),
                                tint = if (colorfulCardsEnabled) {
                                    Color(visualColors.iconTint)
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        DropdownMenuPopup(
                            expanded = delayedMenuExpanded,
                            onDismissRequest = { delayedMenuExpanded = false }
                        ) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .widthIn(min = 132.dp, max = 180.dp),
                                containerColor = MenuDefaults.groupStandardContainerColor,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    WorkflowPlainMenuItem(
                                        text = stringResource(R.string.workflow_execute_delay_5s),
                                        onClick = {
                                            delayedMenuExpanded = false
                                            onExecuteWorkflowDelayed(5_000L)
                                        }
                                    )
                                    WorkflowPlainMenuItem(
                                        text = stringResource(R.string.workflow_execute_delay_15s),
                                        onClick = {
                                            delayedMenuExpanded = false
                                            onExecuteWorkflowDelayed(15_000L)
                                        }
                                    )
                                    WorkflowPlainMenuItem(
                                        text = stringResource(R.string.workflow_execute_delay_1min),
                                        onClick = {
                                            delayedMenuExpanded = false
                                            onExecuteWorkflowDelayed(60_000L)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderCard(
    folder: WorkflowFolder,
    workflowCount: Int,
    modifier: Modifier = Modifier,
    actions: WorkflowListScreenActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { actions.onOpenFolder(folder.id) })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = stringResource(R.string.folder_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            SpacerWidth(12.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    text = stringResource(R.string.folder_workflow_count, workflowCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.workflow_item_more_options)
                    )
                }
                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(index = 0, count = 1),
                        containerColor = MenuDefaults.groupStandardContainerColor,
                    ) {
                        WorkflowActionMenuItem(
                            text = stringResource(R.string.folder_rename),
                            icon = Icons.Outlined.DriveFileRenameOutline,
                            index = 0,
                            count = 3,
                            onClick = {
                                menuExpanded = false
                                actions.onRenameFolder(folder.id)
                            }
                        )
                        WorkflowActionMenuItem(
                            text = stringResource(R.string.folder_export),
                            icon = Icons.Outlined.Download,
                            index = 1,
                            count = 3,
                            onClick = {
                                menuExpanded = false
                                actions.onExportFolder(folder.id)
                            }
                        )
                        WorkflowActionMenuItem(
                            text = stringResource(R.string.folder_delete),
                            icon = Icons.Outlined.DeleteOutline,
                            index = 2,
                            count = 3,
                            onClick = {
                                menuExpanded = false
                                actions.onDeleteFolder(folder.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkflowActionMenuItem(
    text: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        shape = MenuDefaults.itemShape(index = index, count = count).shape,
    )
}

@Composable
private fun RowScope.WorkflowQuickActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WorkflowFlatMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WorkflowPlainMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WorkflowChip(
    label: String,
    iconRes: Int,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SpacerWidth(width: Dp) {
    Spacer(modifier = Modifier.width(width))
}
