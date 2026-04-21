// 文件：WorkflowListFragment.kt
// 描述：显示工作流列表，并提供创建、编辑、删除、导入/导出和执行工作流的功能。支持文件夹功能。

package com.chaomixian.vflow.ui.workflow_list

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.TriggerExecutionCoordinator
import com.chaomixian.vflow.core.workflow.WorkflowBatchEnumMigrationPreview
import com.chaomixian.vflow.core.workflow.WorkflowEnumMigration
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.WorkflowPermissionRecovery
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.InsetAwareComposeContainer
import com.chaomixian.vflow.ui.common.ShortcutHelper
import com.chaomixian.vflow.ui.common.VFlowTheme
import com.chaomixian.vflow.ui.float.WorkflowsFloatPanelService
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.ui.main.MainTopBarActionHandler
import com.chaomixian.vflow.ui.main.WorkflowSortMode
import com.chaomixian.vflow.ui.main.WorkflowTopBarAction
import com.chaomixian.vflow.ui.screen.workflow.WorkflowListScreen
import com.chaomixian.vflow.ui.screen.workflow.WorkflowListScreenActions
import com.chaomixian.vflow.ui.viewmodel.WorkflowListViewModel
import com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity
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
import java.util.Queue
import java.util.UUID

class WorkflowListFragment : Fragment(), MainTopBarActionHandler {
    companion object {
        const val PREF_WORKFLOW_SORT_MODE = "workflow_sort_mode"
    }

    private lateinit var workflowManager: WorkflowManager
    private lateinit var folderManager: FolderManager
    private lateinit var importHelper: WorkflowImportHelper
    private var pendingWorkflow: Workflow? = null
    private var pendingExportFolderId: String? = null
    private val gson = Gson()
    private var pendingEnumMigrationPreview: WorkflowBatchEnumMigrationPreview? = null
    private var dismissedEnumMigrationSignature: String? = null
    private var loadDataJob: Job? = null

    private var workflowSortMode = WorkflowSortMode.Default
    private val workflowListViewModel: WorkflowListViewModel by viewModels()

    private val chineseCollator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }

    private val delayedExecuteHandler = Handler(Looper.getMainLooper())

    private val importQueue: Queue<Workflow> = LinkedList()
    private enum class ConflictChoice { ASK, REPLACE_ALL, KEEP_ALL }
    private var conflictChoice = ConflictChoice.ASK

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { executeWorkflow(it) }
        }
        pendingWorkflow = null
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            showFavoriteWorkflowsFloat()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_overlay_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val exportSingleLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportWorkflow?.let { workflow ->
                try {
                    val exportData = createWorkflowExportData(workflow)
                    val jsonString = gson.toJson(exportData)
                    requireContext().contentResolver.openOutputStream(fileUri)?.use {
                        it.write(jsonString.toByteArray())
                    }
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_export_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_export_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        pendingExportWorkflow = null
    }

    private fun createWorkflowExportData(workflow: Workflow): Map<String, Any?> {
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

    private var pendingExportWorkflow: Workflow? = null

    private val exportFolderLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportFolderId?.let { folderId ->
                try {
                    val folder = folderManager.getFolder(folderId)
                    val workflows = workflowManager.getAllWorkflows().filter { it.folderId == folderId }
                    if (folder != null) {
                        val workflowsWithMeta = workflows.map { createWorkflowExportData(it) }
                        val exportData = mapOf(
                            "folder" to folder,
                            "workflows" to workflowsWithMeta
                        )
                        val jsonString = gson.toJson(exportData)
                        requireContext().contentResolver.openOutputStream(fileUri)?.use {
                            it.write(jsonString.toByteArray())
                        }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_folder_export_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_export_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        pendingExportFolderId = null
    }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val migrationPreview = pendingEnumMigrationPreview
        uri?.let { fileUri ->
            try {
                backupAllWorkflowsToUri(fileUri)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_backup_success),
                    Toast.LENGTH_SHORT
                ).show()
                migrationPreview?.let { applyWorkflowEnumMigration(it) }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_backup_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        pendingEnumMigrationPreview = null
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                val jsonString = requireContext().contentResolver.openInputStream(fileUri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: throw Exception(getString(R.string.error_cannot_read_file))
                importHelper.importFromJson(jsonString)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_import_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        workflowManager = WorkflowManager(requireContext())
        folderManager = FolderManager(requireContext())
        workflowSortMode = readWorkflowSortMode()
        importHelper = WorkflowImportHelper(
            requireContext(),
            workflowManager,
            folderManager
        ) { loadData() }

        val composeContainer = InsetAwareComposeContainer(requireContext())
        return composeContainer.apply {
            composeView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            composeView.setContent {
                val uiState by workflowListViewModel.uiState.collectAsStateWithLifecycle()
                val density = LocalDensity.current
                VFlowTheme {
                    WorkflowListScreen(
                        uiState = uiState,
                        extraBottomPadding = with(density) { contentBottomInsetPx.toDp() },
                        actions = WorkflowListScreenActions(
                            onCreateWorkflow = {
                                startActivity(Intent(requireContext(), WorkflowEditorActivity::class.java))
                            },
                            onToggleFavorite = { workflow ->
                                workflowManager.saveWorkflow(workflow.copy(isFavorite = !workflow.isFavorite))
                                ShortcutHelper.updateShortcuts(requireContext())
                                loadData()
                            },
                            onToggleEnabled = ::toggleWorkflowEnabled,
                            onOpenWorkflow = { workflow ->
                                val intent = Intent(
                                    requireContext(),
                                    WorkflowEditorActivity::class.java
                                ).apply {
                                    putExtra(WorkflowEditorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                                }
                                startActivity(intent)
                            },
                            onDeleteWorkflow = ::showDeleteWorkflowConfirmationDialog,
                            onDuplicateWorkflow = { workflow ->
                                workflowManager.duplicateWorkflow(workflow.id)
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.toast_copied_as, workflow.name),
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
                                    executeWorkflow(workflow)
                                }
                            },
                            onExecuteWorkflowDelayed = ::scheduleDelayedExecution,
                            onAddShortcut = { workflow ->
                                ShortcutHelper.requestPinnedShortcut(requireContext(), workflow)
                            },
                            onAddToTile = { workflow ->
                                val dialog = TileSelectionDialog.newInstance(workflow.id, workflow.name)
                                dialog.show(childFragmentManager, TileSelectionDialog.TAG)
                            },
                            onCopyWorkflowId = { workflow ->
                                val clipboard = requireContext().getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Workflow ID", workflow.id))
                                Toast.makeText(
                                    requireContext(),
                                    R.string.workflow_id_copied,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onMoveWorkflowToFolder = ::showMoveToFolderDialog,
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
                                    requireContext(),
                                    getString(R.string.toast_workflow_moved_out_of_folder, workflow.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                workflowListViewModel.updateFolderWorkflows(
                                    workflowManager.getAllWorkflows()
                                        .filter { it.folderId == workflow.folderId }
                                        .sortedBy { it.order }
                                )
                                loadData()
                            },
                            onRenameFolder = ::showRenameFolderDialog,
                            onDeleteFolder = ::showDeleteFolderConfirmationDialog,
                            onExportFolder = { folderId ->
                                pendingExportFolderId = folderId
                                val folder = folderManager.getFolder(folderId)
                                exportFolderLauncher.launch("${folder?.name ?: "folder"}.json")
                            },
                            onPersistWorkflowOrder = { workflows ->
                                workflowManager.saveAllWorkflows(workflows)
                                ShortcutHelper.updateShortcuts(requireContext())
                                loadData()
                            },
                            onMoveWorkflowToFolderByDrop = { workflow, folderId ->
                                val folder = folderManager.getFolder(folderId) ?: return@WorkflowListScreenActions
                                workflowManager.saveWorkflow(workflow.copy(folderId = folder.id))
                                Toast.makeText(
                                    requireContext(),
                                    getString(
                                        R.string.toast_workflow_moved_to_folder,
                                        workflow.name,
                                        folder.name
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadData()
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData(showMigrationPrompt = false)
        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest {
                workflowListViewModel.bumpExecutionStateVersion()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        workflowListViewModel.bumpExecutionStateVersion()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            WorkflowPermissionRecovery.recoverEligibleWorkflows(requireContext())
            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext
                loadData(showMigrationPrompt = true)
            }
        }
    }

    override fun onMainTopBarAction(action: WorkflowTopBarAction): Boolean {
        when (action) {
            WorkflowTopBarAction.FavoriteFloat -> handleFavoriteFloatClick()
            WorkflowTopBarAction.SortDefault -> {
                workflowSortMode = WorkflowSortMode.Default
                persistWorkflowSortMode()
                loadData()
            }
            WorkflowTopBarAction.SortByName -> {
                workflowSortMode = WorkflowSortMode.Name
                persistWorkflowSortMode()
                loadData()
            }
            WorkflowTopBarAction.SortByRecentModified -> {
                workflowSortMode = WorkflowSortMode.RecentModified
                persistWorkflowSortMode()
                loadData()
            }
            WorkflowTopBarAction.SortFavoritesFirst -> {
                workflowSortMode = WorkflowSortMode.FavoritesFirst
                persistWorkflowSortMode()
                loadData()
            }
            WorkflowTopBarAction.CreateFolder -> showCreateFolderDialog()
            WorkflowTopBarAction.BackupWorkflows -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                backupLauncher.launch("vflow_backup_${timestamp}.json")
            }
            WorkflowTopBarAction.ImportWorkflows -> {
                importLauncher.launch(arrayOf("application/json"))
            }
        }
        return true
    }

    private fun handleFavoriteFloatClick() {
        if (checkOverlayPermission()) {
            showFavoriteWorkflowsFloat()
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_overlay_permission_title))
                .setMessage(getString(R.string.dialog_overlay_permission_message))
                .setPositiveButton(getString(R.string.dialog_button_go_to_settings)) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showFavoriteWorkflowsFloat() {
        val intent = Intent(requireContext(), WorkflowsFloatPanelService::class.java).apply {
            action = WorkflowsFloatPanelService.ACTION_SHOW
        }
        requireContext().startService(intent)
    }

    private fun toggleWorkflowEnabled(workflow: Workflow, enabled: Boolean) {
        val appContext = requireContext().applicationContext
        val updatedWorkflow = workflow.copy(
            isEnabled = enabled,
            wasEnabledBeforePermissionsLost = false
        )

        workflowManager.saveWorkflow(updatedWorkflow)
        loadData()

        if (!enabled || !workflow.hasAutoTriggers()) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val latestWorkflow = workflowManager.getWorkflow(workflow.id) ?: return@launch
            val remainingPermissions = withContext(Dispatchers.IO) {
                TriggerExecutionCoordinator.recoverMissingPermissions(
                    appContext,
                    latestWorkflow
                )
            }

            if (remainingPermissions.isEmpty()) {
                workflowListViewModel.bumpExecutionStateVersion()
                return@launch
            }

            val currentWorkflow = workflowManager.getWorkflow(workflow.id) ?: return@launch
            if (!currentWorkflow.isEnabled) {
                return@launch
            }

            workflowManager.saveWorkflow(
                currentWorkflow.copy(
                    isEnabled = false,
                    wasEnabledBeforePermissionsLost = true
                )
            )
            loadData()

            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_missing_permissions_cannot_enable_workflow),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadData() {
        loadData(showMigrationPrompt = false)
    }

    private fun loadData(showMigrationPrompt: Boolean) {
        loadDataJob?.cancel()
        workflowListViewModel.setLoading(true)
        loadDataJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val workflows = workflowManager.getAllWorkflows()
            val folders = folderManager.getAllFolders()
            val items = buildWorkflowItems(workflows, folders)
            val migrationPreview = if (showMigrationPrompt) {
                WorkflowEnumMigration.scan(workflows)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext
                workflowListViewModel.setItems(items)
                workflowListViewModel.uiState.value.openFolder?.let { openFolder ->
                    workflowListViewModel.updateFolderWorkflows(
                        workflows
                            .filter { it.folderId == openFolder.id }
                            .sortedBy { it.order }
                    )
                }

                if (showMigrationPrompt) {
                    maybePromptWorkflowEnumMigration(migrationPreview)
                }
            }

            launch(Dispatchers.IO) {
                ShortcutHelper.updateShortcuts(requireContext())
            }
        }
    }

    private fun buildWorkflowItems(
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
            WorkflowSortMode.Name -> rootWorkflows.sortedWith(compareWithChineseCollator { it.name })
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

    private fun readWorkflowSortMode(): WorkflowSortMode {
        val prefs = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, Activity.MODE_PRIVATE)
        val storedValue = prefs.getString(PREF_WORKFLOW_SORT_MODE, WorkflowSortMode.Default.name)
        return WorkflowSortMode.entries.firstOrNull { it.name == storedValue }
            ?: WorkflowSortMode.Default
    }

    private fun persistWorkflowSortMode() {
        requireContext().getSharedPreferences(MainActivity.PREFS_NAME, Activity.MODE_PRIVATE)
            .edit()
            .putString(PREF_WORKFLOW_SORT_MODE, workflowSortMode.name)
            .apply()
    }

    private fun backupAllWorkflowsToUri(fileUri: Uri) {
        val allWorkflows = workflowManager.getAllWorkflows()
        val allFolders = folderManager.getAllFolders()
        val workflowsWithMeta = allWorkflows.map { createWorkflowExportData(it) }
        val backupData = mapOf(
            "workflows" to workflowsWithMeta,
            "folders" to allFolders
        )
        val jsonString = gson.toJson(backupData)
        requireContext().contentResolver.openOutputStream(fileUri)?.use {
            it.write(jsonString.toByteArray())
        }
    }

    private fun maybePromptWorkflowEnumMigration(preview: WorkflowBatchEnumMigrationPreview?) {
        if (pendingEnumMigrationPreview != null) return
        if (preview == null) {
            dismissedEnumMigrationSignature = null
            return
        }

        val signature = buildWorkflowEnumMigrationSignature(preview)
        if (signature == dismissedEnumMigrationSignature) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_workflow_enum_migration_title)
            .setMessage(
                getString(
                    R.string.dialog_workflow_enum_migration_batch_message,
                    preview.affectedWorkflowCount,
                    preview.affectedStepCount,
                    preview.affectedFieldCount
                )
            )
            .setPositiveButton(R.string.common_yes) { _, _ ->
                showWorkflowEnumMigrationBackupDialog(preview, signature)
            }
            .setNegativeButton(R.string.common_no) { _, _ ->
                dismissedEnumMigrationSignature = signature
            }
            .setOnCancelListener {
                dismissedEnumMigrationSignature = signature
            }
            .show()
    }

    private fun showWorkflowEnumMigrationBackupDialog(
        preview: WorkflowBatchEnumMigrationPreview,
        signature: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_workflow_enum_migration_backup_title)
            .setMessage(R.string.dialog_workflow_enum_migration_backup_message)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                pendingEnumMigrationPreview = preview
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                backupLauncher.launch("vflow_backup_before_enum_migration_${timestamp}.json")
            }
            .setNegativeButton(R.string.common_no) { _, _ ->
                applyWorkflowEnumMigration(preview)
            }
            .setNeutralButton(R.string.common_cancel) { _, _ ->
                dismissedEnumMigrationSignature = signature
            }
            .setOnCancelListener {
                dismissedEnumMigrationSignature = signature
            }
            .show()
    }

    private fun applyWorkflowEnumMigration(preview: WorkflowBatchEnumMigrationPreview) {
        preview.migratedWorkflows.forEach(workflowManager::saveWorkflow)
        dismissedEnumMigrationSignature = null
        loadData()
        Toast.makeText(
            requireContext(),
            getString(
                R.string.toast_workflow_enum_migration_success,
                preview.affectedWorkflowCount,
                preview.affectedFieldCount
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildWorkflowEnumMigrationSignature(
        preview: WorkflowBatchEnumMigrationPreview
    ): String {
        return preview.previews
            .sortedBy { it.originalWorkflow.id }
            .joinToString(separator = "|") {
                "${it.originalWorkflow.id}:${it.originalWorkflow.modifiedAt}:${it.affectedFieldCount}"
            }
    }

    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
        if (missingPermissions.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_starting_workflow, workflow.name),
                Toast.LENGTH_SHORT
            ).show()
            WorkflowExecutor.execute(
                workflow = workflow,
                context = requireContext(),
                triggerStepId = workflow.manualTrigger()?.id
            )
        } else {
            pendingWorkflow = workflow
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(
                    PermissionActivity.EXTRA_PERMISSIONS,
                    ArrayList(missingPermissions)
                )
                putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
            }
            permissionLauncher.launch(intent)
        }
    }

    private fun scheduleDelayedExecution(workflow: Workflow, delayMs: Long) {
        val delayText = when (delayMs) {
            5_000L -> getString(R.string.workflow_execute_delay_5s)
            15_000L -> getString(R.string.workflow_execute_delay_15s)
            60_000L -> getString(R.string.workflow_execute_delay_1min)
            else -> getString(R.string.workflow_execute_delay_seconds, delayMs / 1000)
        }
        Toast.makeText(
            requireContext(),
            getString(R.string.workflow_execute_delayed, delayText, workflow.name),
            Toast.LENGTH_SHORT
        ).show()

        delayedExecuteHandler.postDelayed({
            executeWorkflow(workflow)
        }, delayMs)
    }

    private fun showCreateFolderDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_create)
            .setView(editText)
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val folder = WorkflowFolder(name = name)
                    folderManager.saveFolder(folder)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_folder_created),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadData()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_folder_name_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showRenameFolderDialog(folderId: String) {
        val folder = folderManager.getFolder(folderId) ?: return
        val editText = EditText(requireContext()).apply {
            setText(folder.name)
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_rename)
            .setView(editText)
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    folderManager.saveFolder(folder.copy(name = name))
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_folder_renamed),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadData()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showMoveToFolderDialog(workflow: Workflow) {
        val folders = folderManager.getAllFolders().let { allFolders ->
            when (workflowSortMode) {
                WorkflowSortMode.Name -> allFolders.sortedWith(compareWithChineseCollator { it.name })
                else -> allFolders
            }
        }
        if (folders.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dialog_move_to_folder_no_folders),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val folderNames = folders.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_move_to_folder_title)
            .setItems(folderNames) { _, which ->
                val folder = folders[which]
                workflowManager.saveWorkflow(workflow.copy(folderId = folder.id))
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.toast_workflow_moved_to_folder,
                        workflow.name,
                        folder.name
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                loadData()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showDeleteFolderConfirmationDialog(folderId: String) {
        val folder = folderManager.getFolder(folderId) ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_folder_delete_title)
            .setMessage(getString(R.string.dialog_folder_delete_message, folder.name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                val workflows = workflowManager.getAllWorkflows()
                workflows.filter { it.folderId == folderId }.forEach { workflow ->
                    workflowManager.saveWorkflow(workflow.copy(folderId = null))
                }
                folderManager.deleteFolder(folderId)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_folder_deleted),
                    Toast.LENGTH_SHORT
                ).show()
                loadData()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showDeleteWorkflowConfirmationDialog(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                loadData()
            }
            .show()
    }

    private fun startImportProcess(workflows: List<Workflow>) {
        importQueue.clear()
        importQueue.addAll(workflows)
        conflictChoice = ConflictChoice.ASK
        processNextInImportQueue()
    }

    private fun processNextInImportQueue() {
        if (importQueue.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_import_completed),
                Toast.LENGTH_SHORT
            ).show()
            loadData()
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
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_import_conflict, null)
        val rememberChoiceCheckbox =
            dialogView.findViewById<CheckBox>(R.id.checkbox_remember_choice)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_import_conflict_title))
            .setMessage(
                getString(
                    R.string.dialog_import_conflict_message,
                    existing.name,
                    existing.id.substring(0, 8),
                    toImport.name
                )
            )
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_keep_both)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.KEEP_ALL
                handleKeepBoth(toImport)
                processNextInImportQueue()
            }
            .setNegativeButton(getString(R.string.dialog_button_replace)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.REPLACE_ALL
                handleReplace(toImport)
                processNextInImportQueue()
            }
            .setNeutralButton(getString(R.string.dialog_button_skip)) { _, _ ->
                processNextInImportQueue()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleReplace(workflow: Workflow) {
        workflowManager.saveWorkflow(workflow)
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_workflow_replaced, workflow.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleKeepBoth(workflow: Workflow) {
        val workflowWithDefaults = workflow.copy(
            version = workflow.version ?: "1.0.0",
            vFlowLevel = if (workflow.vFlowLevel == 0) 1 else workflow.vFlowLevel,
            description = workflow.description ?: "",
            author = workflow.author ?: "",
            homepage = workflow.homepage ?: "",
            tags = workflow.tags ?: emptyList(),
            modifiedAt = if (workflow.modifiedAt == 0L) System.currentTimeMillis() else workflow.modifiedAt
        )
        val newWorkflow = workflowWithDefaults.copy(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.toast_workflow_imported_name, workflow.name)
        )
        workflowManager.saveWorkflow(newWorkflow)
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_workflow_imported_as_copy, workflow.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private inline fun <T> compareWithChineseCollator(
        crossinline selector: (T) -> String
    ): Comparator<T> {
        return Comparator { a, b ->
            chineseCollator.compare(selector(a), selector(b))
        }
    }
}
