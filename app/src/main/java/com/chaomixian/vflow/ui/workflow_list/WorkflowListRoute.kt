package com.chaomixian.vflow.ui.workflow_list

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.TileManager
import com.chaomixian.vflow.core.workflow.TriggerExecutionCoordinator
import com.chaomixian.vflow.core.workflow.WorkflowBatchEnumMigrationPreview
import com.chaomixian.vflow.core.workflow.WorkflowEnumMigration
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.WorkflowPermissionRecovery
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.core.workflow.model.WorkflowTile
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.ShortcutHelper
import com.chaomixian.vflow.ui.float.WorkflowsFloatPanelService
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.ui.main.WorkflowSortMode
import com.chaomixian.vflow.ui.main.WorkflowTopBarAction
import com.chaomixian.vflow.ui.viewmodel.WorkflowListViewModel
import com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity
import com.chaomixian.vflow.ui.workflow_list.WorkflowImportHelper
import com.chaomixian.vflow.ui.workflow_list.WorkflowListItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.UUID

private const val PREF_WORKFLOW_SORT_MODE = "workflow_sort_mode"
private data class TileSelectionTarget(
    val workflowId: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorkflowListRoute(
    activity: MainActivity,
    isActive: Boolean,
    workflowSortMode: WorkflowSortMode,
    workflowAction: WorkflowTopBarAction?,
    workflowActionVersion: Int,
    extraBottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    workflowListViewModel: WorkflowListViewModel = viewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uiState by workflowListViewModel.uiState.collectAsStateWithLifecycle()
    val workflowManager = remember(context) { WorkflowManager(context) }
    val folderManager = remember(context) { FolderManager(context) }
    val tileManager = remember(context) { TileManager(context) }
    val chineseCollator = remember { Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY } }
    val gson = remember { Gson() }
    val delayedExecuteHandler = remember { Handler(Looper.getMainLooper()) }
    val importQueue = remember { LinkedList<Workflow>() }
    var conflictChoice by remember { mutableStateOf(ConflictChoice.ASK) }
    var pendingWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var pendingExportWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var pendingExportFolderId by remember { mutableStateOf<String?>(null) }
    var pendingEnumMigrationPreview by remember { mutableStateOf<WorkflowBatchEnumMigrationPreview?>(null) }
    var dismissedEnumMigrationSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var loadDataJob by remember { mutableStateOf<Job?>(null) }
    var requestBackup by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var tileSelectionTarget by remember { mutableStateOf<TileSelectionTarget?>(null) }
    var tileSelectionVersion by remember { mutableStateOf(0) }
    val tileSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)

    fun compareWithChineseCollator(selector: (WorkflowFolder) -> String): Comparator<WorkflowFolder> {
        return Comparator { a, b -> chineseCollator.compare(selector(a), selector(b)) }
    }

    fun compareWorkflowWithChineseCollator(selector: (Workflow) -> String): Comparator<Workflow> {
        return Comparator { a, b -> chineseCollator.compare(selector(a), selector(b)) }
    }

    fun persistWorkflowSortMode() {
        context.getSharedPreferences(MainActivity.PREFS_NAME, Activity.MODE_PRIVATE)
            .edit()
            .putString(PREF_WORKFLOW_SORT_MODE, workflowSortMode.name)
            .apply()
    }

    fun buildWorkflowItems(
        workflows: List<Workflow>,
        folders: List<WorkflowFolder>,
    ): MutableList<WorkflowListItem> {
        val items = mutableListOf<WorkflowListItem>()
        val sortedFolders = when (workflowSortMode) {
            WorkflowSortMode.Name -> folders.sortedWith(compareWithChineseCollator { it.name })
            else -> folders
        }
        sortedFolders.forEach { folder ->
            val folderWorkflows = workflows.filter { it.folderId == folder.id }
            val searchableContent = folderWorkflows.joinToString(separator = "\n") { workflow ->
                buildString {
                    append(workflow.name)
                    if (workflow.description.isNotBlank()) {
                        append('\n')
                        append(workflow.description)
                    }
                }
            }
            items.add(
                WorkflowListItem.FolderItem(
                    folder = folder,
                    workflowCount = folderWorkflows.size,
                    searchableContent = searchableContent,
                    childWorkflows = folderWorkflows
                )
            )
        }

        val rootWorkflows = workflows.filter { it.folderId == null }
        val sortedWorkflows = when (workflowSortMode) {
            WorkflowSortMode.Name -> rootWorkflows.sortedWith(compareWorkflowWithChineseCollator { it.name })
            WorkflowSortMode.RecentModified -> rootWorkflows.sortedByDescending { it.modifiedAt }
            WorkflowSortMode.FavoritesFirst -> rootWorkflows.sortedWith(
                compareByDescending<Workflow> { it.isFavorite }
            )
            WorkflowSortMode.Default -> rootWorkflows
        }
        sortedWorkflows.forEach { workflow ->
            items.add(WorkflowListItem.WorkflowItem(workflow))
        }
        return items
    }

    fun loadData(showMigrationPrompt: Boolean = false) {
        loadDataJob?.cancel()
        workflowListViewModel.setLoading(true)
        loadDataJob = scope.launch(Dispatchers.Default) {
            val workflows = workflowManager.getAllWorkflows()
            val folders = folderManager.getAllFolders()
            val items = buildWorkflowItems(workflows, folders)
            val migrationPreview = if (showMigrationPrompt) WorkflowEnumMigration.scan(workflows) else null

            withContext(Dispatchers.Main) {
                workflowListViewModel.setItems(items)
                workflowListViewModel.uiState.value.openFolder?.let { openFolder ->
                    workflowListViewModel.updateFolderWorkflows(
                        workflows
                            .filter { it.folderId == openFolder.id }
                            .sortedBy { it.order }
                    )
                }

                if (showMigrationPrompt) {
                    maybePromptWorkflowEnumMigration(
                        context = context,
                        preview = migrationPreview,
                        dismissedEnumMigrationSignature = dismissedEnumMigrationSignature,
                        setDismissedSignature = { dismissedEnumMigrationSignature = it },
                        setPendingPreview = { pendingEnumMigrationPreview = it },
                        onApplyMigration = { previewToApply ->
                            previewToApply.migratedWorkflows.forEach(workflowManager::saveWorkflow)
                            dismissedEnumMigrationSignature = null
                            loadData()
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.toast_workflow_enum_migration_success,
                                    previewToApply.affectedWorkflowCount,
                                    previewToApply.affectedFieldCount
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onRequestBackup = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                .format(Date())
                            requestBackup?.invoke("vflow_backup_before_enum_migration_${timestamp}.json")
                        }
                    )
                }
            }

            launch(Dispatchers.IO) {
                ShortcutHelper.updateShortcuts(context)
            }
        }
    }

    val importHelper = remember(context, workflowManager, folderManager) {
        WorkflowImportHelper(
            context,
            workflowManager,
            folderManager
        ) { loadData() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { workflow ->
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_starting_workflow, workflow.name),
                    Toast.LENGTH_SHORT
                ).show()
                WorkflowExecutor.execute(
                    workflow = workflow,
                    context = context,
                    triggerStepId = workflow.manualTrigger()?.id
                )
            }
        }
        pendingWorkflow = null
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission(context)) {
            context.startService(
                Intent(context, WorkflowsFloatPanelService::class.java).apply {
                    action = WorkflowsFloatPanelService.ACTION_SHOW
                }
            )
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.toast_overlay_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val exportSingleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportWorkflow?.let { workflow ->
                try {
                    val exportData = createWorkflowExportData(gson, workflow)
                    val jsonString = gson.toJson(exportData)
                    context.contentResolver.openOutputStream(fileUri)?.use {
                        it.write(jsonString.toByteArray())
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_export_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_export_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        pendingExportWorkflow = null
    }

    val exportFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportFolderId?.let { folderId ->
                try {
                    val folder = folderManager.getFolder(folderId)
                    val workflows = workflowManager.getAllWorkflows().filter { it.folderId == folderId }
                    if (folder != null) {
                        val workflowsWithMeta = workflows.map { createWorkflowExportData(gson, it) }
                        val exportData = mapOf("folder" to folder, "workflows" to workflowsWithMeta)
                        val jsonString = gson.toJson(exportData)
                        context.contentResolver.openOutputStream(fileUri)?.use {
                            it.write(jsonString.toByteArray())
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_folder_export_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_export_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        pendingExportFolderId = null
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val migrationPreview = pendingEnumMigrationPreview
        uri?.let { fileUri ->
            try {
                backupAllWorkflowsToUri(context, workflowManager, folderManager, gson, fileUri)
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_backup_success),
                    Toast.LENGTH_SHORT
                ).show()
                migrationPreview?.let { preview ->
                    preview.migratedWorkflows.forEach(workflowManager::saveWorkflow)
                    dismissedEnumMigrationSignature = null
                    loadData()
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.toast_workflow_enum_migration_success,
                            preview.affectedWorkflowCount,
                            preview.affectedFieldCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_backup_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        pendingEnumMigrationPreview = null
    }
    requestBackup = backupLauncher::launch

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        uri?.let { fileUri ->
            try {
                val jsonString = context.contentResolver.openInputStream(fileUri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: throw Exception(context.getString(R.string.error_cannot_read_file))
                importHelper.importFromJson(jsonString)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_import_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        ExecutionStateBus.stateFlow.collectLatest {
            workflowListViewModel.bumpExecutionStateVersion()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                workflowListViewModel.bumpExecutionStateVersion()
                scope.launch(Dispatchers.IO) {
                    WorkflowPermissionRecovery.recoverEligibleWorkflows(context)
                    withContext(Dispatchers.Main) {
                        loadData(showMigrationPrompt = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            delayedExecuteHandler.removeCallbacksAndMessages(null)
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            workflowListViewModel.bumpExecutionStateVersion()
            scope.launch(Dispatchers.IO) {
                WorkflowPermissionRecovery.recoverEligibleWorkflows(context)
                withContext(Dispatchers.Main) {
                    loadData(showMigrationPrompt = true)
                }
            }
        }
    }

    LaunchedEffect(workflowSortMode) {
        persistWorkflowSortMode()
        loadData()
    }

    LaunchedEffect(workflowActionVersion) {
        when (workflowAction) {
            WorkflowTopBarAction.FavoriteFloat -> {
                if (checkOverlayPermission(context)) {
                    context.startService(
                        Intent(context, WorkflowsFloatPanelService::class.java).apply {
                            action = WorkflowsFloatPanelService.ACTION_SHOW
                        }
                    )
                } else {
                    requestOverlayPermission(context, overlayPermissionLauncher::launch)
                }
            }

            WorkflowTopBarAction.CreateFolder -> {
                showCreateFolderDialog(context, folderManager) { loadData() }
            }

            WorkflowTopBarAction.BackupWorkflows -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                backupLauncher.launch("vflow_backup_${timestamp}.json")
            }

            WorkflowTopBarAction.ImportWorkflows -> {
                importLauncher.launch(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/json"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }

            WorkflowTopBarAction.SortDefault,
            WorkflowTopBarAction.SortByName,
            WorkflowTopBarAction.SortByRecentModified,
            WorkflowTopBarAction.SortFavoritesFirst,
            null -> Unit
        }
    }

    WorkflowListScreen(
        uiState = uiState,
        extraBottomPadding = extraBottomPadding,
        modifier = modifier,
        actions = WorkflowListScreenActions(
            onCreateWorkflow = {
                context.startActivity(Intent(context, WorkflowEditorActivity::class.java))
            },
            onToggleFavorite = { workflow ->
                workflowManager.saveWorkflow(workflow.copy(isFavorite = !workflow.isFavorite))
                ShortcutHelper.updateShortcuts(context)
                loadData()
            },
            onToggleEnabled = { workflow, enabled ->
                val appContext = context.applicationContext
                val updatedWorkflow = workflow.copy(
                    isEnabled = enabled,
                    wasEnabledBeforePermissionsLost = false
                )
                workflowManager.saveWorkflow(updatedWorkflow)
                loadData()
                if (!enabled || !workflow.hasAutoTriggers()) return@WorkflowListScreenActions

                scope.launch {
                    val latestWorkflow = workflowManager.getWorkflow(workflow.id) ?: return@launch
                    val remainingPermissions = withContext(Dispatchers.IO) {
                        TriggerExecutionCoordinator.recoverMissingPermissions(appContext, latestWorkflow)
                    }
                    if (remainingPermissions.isEmpty()) {
                        workflowListViewModel.bumpExecutionStateVersion()
                        return@launch
                    }
                    val currentWorkflow = workflowManager.getWorkflow(workflow.id) ?: return@launch
                    if (!currentWorkflow.isEnabled) return@launch
                    workflowManager.saveWorkflow(
                        currentWorkflow.copy(
                            isEnabled = false,
                            wasEnabledBeforePermissionsLost = true
                        )
                    )
                    loadData()
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_missing_permissions_cannot_enable_workflow),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onOpenWorkflow = { workflow ->
                context.startActivity(
                    Intent(context, WorkflowEditorActivity::class.java).apply {
                        putExtra(WorkflowEditorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                    }
                )
            },
            onDeleteWorkflow = { workflow ->
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(context.getString(R.string.dialog_delete_message, workflow.name))
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.common_delete) { _, _ ->
                        workflowManager.deleteWorkflow(workflow.id)
                        loadData()
                    }
                    .show()
            },
            onDuplicateWorkflow = { workflow ->
                workflowManager.duplicateWorkflow(workflow.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_copied_as, workflow.name),
                    Toast.LENGTH_SHORT
                ).show()
                loadData()
            },
            onExportWorkflow = { workflow ->
                pendingExportWorkflow = workflow
                exportSingleLauncher.launch("${workflow.name}.json")
            },
            onExecuteWorkflow = { workflow ->
                if (WorkflowExecutor.isRunning(workflow.id)) {
                    WorkflowExecutor.stopExecution(workflow.id)
                } else {
                    val missingPermissions = PermissionManager.getMissingPermissions(context, workflow)
                    if (missingPermissions.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_starting_workflow, workflow.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        WorkflowExecutor.execute(
                            workflow = workflow,
                            context = context,
                            triggerStepId = workflow.manualTrigger()?.id
                        )
                    } else {
                        pendingWorkflow = workflow
                        permissionLauncher.launch(
                            Intent(context, PermissionActivity::class.java).apply {
                                putParcelableArrayListExtra(
                                    PermissionActivity.EXTRA_PERMISSIONS,
                                    ArrayList(missingPermissions)
                                )
                                putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
                            }
                        )
                    }
                }
            },
            onExecuteWorkflowDelayed = { workflow, delayMs ->
                val delayText = when (delayMs) {
                    5_000L -> context.getString(R.string.workflow_execute_delay_5s)
                    15_000L -> context.getString(R.string.workflow_execute_delay_15s)
                    60_000L -> context.getString(R.string.workflow_execute_delay_1min)
                    else -> context.getString(R.string.workflow_execute_delay_seconds, delayMs / 1000)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.workflow_execute_delayed, delayText, workflow.name),
                    Toast.LENGTH_SHORT
                ).show()
                delayedExecuteHandler.postDelayed({ 
                    val missingPermissions = PermissionManager.getMissingPermissions(context, workflow)
                    if (missingPermissions.isEmpty()) {
                        WorkflowExecutor.execute(
                            workflow = workflow,
                            context = context,
                            triggerStepId = workflow.manualTrigger()?.id
                        )
                    }
                }, delayMs)
            },
            onAddShortcut = { workflow ->
                ShortcutHelper.requestPinnedShortcut(context, workflow)
            },
            onAddToTile = { workflow ->
                tileSelectionTarget = TileSelectionTarget(workflowId = workflow.id)
            },
            onCopyWorkflowId = { workflow ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Workflow ID", workflow.id))
                Toast.makeText(context, R.string.workflow_id_copied, Toast.LENGTH_SHORT).show()
            },
            onMoveWorkflowToFolder = { workflow ->
                showMoveToFolderDialog(context, folderManager, workflowManager, workflowSortMode, chineseCollator, workflow) {
                    loadData()
                }
            },
            onOpenFolder = { folderId ->
                if (folderId.isBlank()) return@WorkflowListScreenActions
                val folder = folderManager.getFolder(folderId) ?: return@WorkflowListScreenActions
                val folderWorkflows = workflowManager.getAllWorkflows()
                    .filter { it.folderId == folderId }
                    .sortedBy { it.order }
                workflowListViewModel.openFolder(folder, folderWorkflows)
            },
            onCloseFolder = {
                workflowListViewModel.closeFolder()
            },
            onMoveWorkflowOutOfFolder = { workflow ->
                workflowManager.saveWorkflow(workflow.copy(folderId = null))
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_workflow_moved_out_of_folder, workflow.name),
                    Toast.LENGTH_SHORT
                ).show()
                workflowListViewModel.updateFolderWorkflows(
                    workflowManager.getAllWorkflows()
                        .filter { it.folderId == workflow.folderId }
                        .sortedBy { it.order }
                )
                loadData()
            },
            onRenameFolder = { folderId ->
                showRenameFolderDialog(context, folderManager, folderId) { loadData() }
            },
            onDeleteFolder = { folderId ->
                showDeleteFolderConfirmationDialog(context, folderManager, workflowManager, folderId) {
                    loadData()
                }
            },
            onExportFolder = { folderId ->
                pendingExportFolderId = folderId
                val folder = folderManager.getFolder(folderId)
                exportFolderLauncher.launch("${folder?.name ?: "folder"}.json")
            },
            onPersistWorkflowOrder = { workflows ->
                workflowManager.saveAllWorkflows(workflows)
                ShortcutHelper.updateShortcuts(context)
                loadData()
            },
            onMoveWorkflowToFolderByDrop = { workflow, folderId ->
                val folder = folderManager.getFolder(folderId) ?: return@WorkflowListScreenActions
                workflowManager.saveWorkflow(workflow.copy(folderId = folder.id))
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_workflow_moved_to_folder, workflow.name, folder.name),
                    Toast.LENGTH_SHORT
                ).show()
                loadData()
            }
        )
    )

    tileSelectionTarget?.let { target ->
        val tileItems = remember(target.workflowId, tileSelectionVersion, uiState.executionStateVersion) {
            tileManager.getAllTilesWithEmpty().map { tile ->
                TileSelectionItem(
                    tileIndex = tile.tileIndex,
                    assignedWorkflowName = tile.workflowId?.let { workflowId ->
                        workflowManager.getWorkflow(workflowId)?.name
                    },
                    isSelected = tile.workflowId == target.workflowId,
                )
            }
        }
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { tileSelectionTarget = null },
            sheetState = tileSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            TileSelectionSheet(
                items = tileItems,
                onSelect = { item ->
                    if (item.isSelected) {
                        tileManager.removeTile(item.tileIndex)
                        Toast.makeText(
                            context,
                            context.getString(R.string.tile_removed, item.tileIndex + 1),
                            Toast.LENGTH_SHORT
                        ).show()
                        tileSelectionVersion++
                    } else {
                        tileManager.removeTileByWorkflowId(target.workflowId)
                        tileManager.saveTile(WorkflowTile(item.tileIndex, target.workflowId))
                        Toast.makeText(
                            context,
                            context.getString(R.string.tile_added, item.tileIndex + 1),
                            Toast.LENGTH_SHORT
                        ).show()
                        tileSelectionVersion++
                    }
                }
            )
        }
    }
}

private enum class ConflictChoice { ASK, REPLACE_ALL, KEEP_ALL }

private fun createWorkflowExportData(gson: Gson, workflow: Workflow): Map<String, Any?> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val updatedAt = dateFormat.format(Date(workflow.modifiedAt))
    val meta = mapOf(
        "id" to workflow.id,
        "name" to workflow.name,
        "version" to workflow.version,
        "vFlowLevel" to workflow.vFlowLevel,
        "description" to workflow.description,
        "author" to workflow.author,
        "homepage" to workflow.homepage,
        "tags" to workflow.tags,
        "updated_at" to updatedAt,
        "modified_at" to workflow.modifiedAt
    )
    val workflowMap = mapOf(
        "id" to workflow.id,
        "name" to workflow.name,
        "triggers" to workflow.triggers,
        "steps" to workflow.steps,
        "isEnabled" to workflow.isEnabled,
        "isFavorite" to workflow.isFavorite,
        "wasEnabledBeforePermissionsLost" to workflow.wasEnabledBeforePermissionsLost,
        "folderId" to workflow.folderId,
        "order" to workflow.order,
        "shortcutName" to workflow.shortcutName,
        "shortcutIconRes" to workflow.shortcutIconRes,
        "cardIconRes" to workflow.cardIconRes,
        "cardThemeColor" to workflow.cardThemeColor,
        "modifiedAt" to workflow.modifiedAt,
        "version" to workflow.version,
        "vFlowLevel" to workflow.vFlowLevel,
        "description" to workflow.description,
        "author" to workflow.author,
        "homepage" to workflow.homepage,
        "tags" to workflow.tags
    )
    return mapOf("_meta" to meta) + workflowMap
}

private fun backupAllWorkflowsToUri(
    context: Context,
    workflowManager: WorkflowManager,
    folderManager: FolderManager,
    gson: Gson,
    fileUri: Uri,
) {
    val allWorkflows = workflowManager.getAllWorkflows()
    val allFolders = folderManager.getAllFolders()
    val workflowsWithMeta = allWorkflows.map { createWorkflowExportData(gson, it) }
    val backupData = mapOf("workflows" to workflowsWithMeta, "folders" to allFolders)
    val jsonString = gson.toJson(backupData)
    context.contentResolver.openOutputStream(fileUri)?.use {
        it.write(jsonString.toByteArray())
    }
}

private fun maybePromptWorkflowEnumMigration(
    context: Context,
    preview: WorkflowBatchEnumMigrationPreview?,
    dismissedEnumMigrationSignature: String?,
    setDismissedSignature: (String?) -> Unit,
    setPendingPreview: (WorkflowBatchEnumMigrationPreview) -> Unit,
    onApplyMigration: (WorkflowBatchEnumMigrationPreview) -> Unit,
    onRequestBackup: () -> Unit,
) {
    if (preview == null) {
        setDismissedSignature(null)
        return
    }

    val signature = preview.previews
        .sortedBy { it.originalWorkflow.id }
        .joinToString(separator = "|") {
            "${it.originalWorkflow.id}:${it.originalWorkflow.modifiedAt}:${it.affectedFieldCount}"
        }
    if (signature == dismissedEnumMigrationSignature) return

    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.dialog_workflow_enum_migration_title)
        .setMessage(
            context.getString(
                R.string.dialog_workflow_enum_migration_batch_message,
                preview.affectedWorkflowCount,
                preview.affectedStepCount,
                preview.affectedFieldCount
            )
        )
        .setPositiveButton(R.string.common_yes) { _, _ ->
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_workflow_enum_migration_backup_title)
                .setMessage(R.string.dialog_workflow_enum_migration_backup_message)
                .setPositiveButton(R.string.common_yes) { _, _ ->
                    setPendingPreview(preview)
                    onRequestBackup()
                }
                .setNegativeButton(R.string.common_no) { _, _ ->
                    onApplyMigration(preview)
                }
                .setNeutralButton(R.string.common_cancel) { _, _ ->
                    setDismissedSignature(signature)
                }
                .setOnCancelListener {
                    setDismissedSignature(signature)
                }
                .show()
        }
        .setNegativeButton(R.string.common_no) { _, _ ->
            setDismissedSignature(signature)
        }
        .setOnCancelListener {
            setDismissedSignature(signature)
        }
        .show()
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun requestOverlayPermission(
    context: Context,
    launch: (Intent) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dialog_overlay_permission_title))
            .setMessage(context.getString(R.string.dialog_overlay_permission_message))
            .setPositiveButton(context.getString(R.string.dialog_button_go_to_settings)) { _, _ ->
                launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private fun showCreateFolderDialog(
    context: Context,
    folderManager: FolderManager,
    onChanged: () -> Unit,
) {
    val editText = EditText(context).apply {
        hint = context.getString(R.string.folder_name_hint)
        setPadding(48, 32, 48, 32)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.folder_create)
        .setView(editText)
        .setPositiveButton(R.string.common_confirm) { _, _ ->
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                folderManager.saveFolder(WorkflowFolder(name = name))
                Toast.makeText(context, context.getString(R.string.toast_folder_created), Toast.LENGTH_SHORT).show()
                onChanged()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_folder_name_empty), Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showRenameFolderDialog(
    context: Context,
    folderManager: FolderManager,
    folderId: String,
    onChanged: () -> Unit,
) {
    val folder = folderManager.getFolder(folderId) ?: return
    val editText = EditText(context).apply {
        setText(folder.name)
        hint = context.getString(R.string.folder_name_hint)
        setPadding(48, 32, 48, 32)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.folder_rename)
        .setView(editText)
        .setPositiveButton(R.string.common_confirm) { _, _ ->
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                folderManager.saveFolder(folder.copy(name = name))
                Toast.makeText(context, context.getString(R.string.toast_folder_renamed), Toast.LENGTH_SHORT).show()
                onChanged()
            }
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showMoveToFolderDialog(
    context: Context,
    folderManager: FolderManager,
    workflowManager: WorkflowManager,
    workflowSortMode: WorkflowSortMode,
    chineseCollator: Collator,
    workflow: Workflow,
    onChanged: () -> Unit,
) {
    val folders = folderManager.getAllFolders().let { allFolders ->
        when (workflowSortMode) {
            WorkflowSortMode.Name -> allFolders.sortedWith { a, b -> chineseCollator.compare(a.name, b.name) }
            else -> allFolders
        }
    }
    if (folders.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.dialog_move_to_folder_no_folders), Toast.LENGTH_SHORT).show()
        return
    }
    val folderNames = folders.map { it.name }.toTypedArray()
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.dialog_move_to_folder_title)
        .setItems(folderNames) { _, which ->
            val folder = folders[which]
            workflowManager.saveWorkflow(workflow.copy(folderId = folder.id))
            Toast.makeText(
                context,
                context.getString(R.string.toast_workflow_moved_to_folder, workflow.name, folder.name),
                Toast.LENGTH_SHORT
            ).show()
            onChanged()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showDeleteFolderConfirmationDialog(
    context: Context,
    folderManager: FolderManager,
    workflowManager: WorkflowManager,
    folderId: String,
    onChanged: () -> Unit,
) {
    val folder = folderManager.getFolder(folderId) ?: return
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.dialog_folder_delete_title)
        .setMessage(context.getString(R.string.dialog_folder_delete_message, folder.name))
        .setPositiveButton(R.string.common_delete) { _, _ ->
            workflowManager.getAllWorkflows()
                .filter { it.folderId == folderId }
                .forEach { workflow -> workflowManager.saveWorkflow(workflow.copy(folderId = null)) }
            folderManager.deleteFolder(folderId)
            Toast.makeText(context, context.getString(R.string.toast_folder_deleted), Toast.LENGTH_SHORT).show()
            onChanged()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}
