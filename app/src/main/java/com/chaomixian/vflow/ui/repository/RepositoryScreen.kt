package com.chaomixian.vflow.ui.repository

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.scripted.ScriptedModule
import com.chaomixian.vflow.data.repository.api.RepositoryApiClient
import com.chaomixian.vflow.data.repository.model.RepoModule
import com.chaomixian.vflow.data.repository.model.RepoWorkflow
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.ui.workflow_list.WorkflowImportHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RepositoryTab(val titleRes: Int) {
    WORKFLOWS(R.string.tab_workflows),
    MODULE_STORE(R.string.tab_module_store),
    LOCAL_MODULES(R.string.tab_local_modules),
}

private data class RepositoryListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class LocalModulesState(
    val userModules: List<ActionModule> = emptyList(),
    val builtInModules: List<ActionModule> = emptyList(),
)

private data class PendingStoreModuleInstall(
    val repoModule: RepoModule,
    val session: ModuleManager.InstallSession,
    val archiveFile: File,
)

private data class PendingLocalModuleInstall(
    val session: ModuleManager.InstallSession,
)

private data class LocalModuleDeletePrompt(
    val module: ActionModule,
    val dependencyNames: List<String>,
)

private sealed interface ModuleInstallResult {
    data class ReadyToCommit(val pending: PendingStoreModuleInstall) : ModuleInstallResult
    data class Completed(val moduleName: String) : ModuleInstallResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val workflowManager = remember(appContext) { WorkflowManager(appContext) }
    val folderManager = remember(appContext) { FolderManager(appContext) }
    val importHelper = remember(context, workflowManager, folderManager) {
        WorkflowImportHelper(context, workflowManager, folderManager) {}
    }

    var selectedTab by rememberSaveable { mutableStateOf(RepositoryTab.WORKFLOWS) }
    var workflowsState by remember { mutableStateOf(RepositoryListState<RepoWorkflow>(isLoading = true)) }
    var moduleStoreState by remember { mutableStateOf(RepositoryListState<RepoModule>(isLoading = true)) }
    var localModulesState by remember { mutableStateOf(LocalModulesState()) }
    var pendingWorkflow by remember { mutableStateOf<RepoWorkflow?>(null) }
    var pendingStoreModule by remember { mutableStateOf<RepoModule?>(null) }
    var pendingStoreModuleInstall by remember { mutableStateOf<PendingStoreModuleInstall?>(null) }
    var pendingLocalModuleInstall by remember { mutableStateOf<PendingLocalModuleInstall?>(null) }
    var detailModule by remember { mutableStateOf<ActionModule?>(null) }
    var deletePrompt by remember { mutableStateOf<LocalModuleDeletePrompt?>(null) }

    fun refreshLocalModules() {
        localModulesState = loadLocalModulesState()
    }

    fun loadWorkflows() {
        workflowsState = workflowsState.copy(isLoading = true, errorMessage = null)
        scope.launch {
            val result = RepositoryApiClient.fetchWorkflowIndex()
            workflowsState = result.fold(
                onSuccess = { index ->
                    RepositoryListState(
                        items = index.workflows,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_load_failed, error.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    RepositoryListState(
                        isLoading = false,
                        errorMessage = context.getString(R.string.text_load_failed, error.message ?: ""),
                    )
                }
            )
        }
    }

    fun loadModuleStore() {
        moduleStoreState = moduleStoreState.copy(isLoading = true, errorMessage = null)
        scope.launch {
            val result = RepositoryApiClient.fetchModuleIndex()
            moduleStoreState = result.fold(
                onSuccess = { index ->
                    RepositoryListState(
                        items = index.modules,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_load_failed, error.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    RepositoryListState(
                        isLoading = false,
                        errorMessage = context.getString(R.string.text_load_failed, error.message ?: ""),
                    )
                }
            )
        }
    }

    val installModuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepareResult = withContext(Dispatchers.IO) {
                ModuleManager.prepareInstall(appContext, uri)
            }
            prepareResult.onSuccess { session ->
                if (ModuleManager.isModuleInstalled(session.manifest.id)) {
                    pendingLocalModuleInstall = PendingLocalModuleInstall(session)
                } else {
                    commitLocalModuleInstall(
                        context = context,
                        session = session,
                        onSuccess = ::refreshLocalModules,
                    )
                }
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.dialog_parse_module_failed) + ": " +
                        (error.message ?: context.getString(R.string.error_unknown_error)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadWorkflows()
        loadModuleStore()
        refreshLocalModules()
    }

    LaunchedEffect(selectedTab) {
        if (
            selectedTab == RepositoryTab.WORKFLOWS &&
            workflowsState.items.isEmpty() &&
            !workflowsState.isLoading &&
            workflowsState.errorMessage == null
        ) {
            loadWorkflows()
        }
        if (
            selectedTab == RepositoryTab.MODULE_STORE &&
            moduleStoreState.items.isEmpty() &&
            !moduleStoreState.isLoading &&
            moduleStoreState.errorMessage == null
        ) {
            loadModuleStore()
        }
        if (
            selectedTab == RepositoryTab.LOCAL_MODULES &&
            localModulesState.userModules.isEmpty() &&
            localModulesState.builtInModules.isEmpty()
        ) {
            refreshLocalModules()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            RepositoryTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
            when (selectedTab) {
                RepositoryTab.WORKFLOWS -> RepositoryWorkflowTab(
                    modifier = Modifier.weight(1f),
                    state = workflowsState,
                    contentPadding = repositoryContentPadding(bottomContentPadding),
                    onRetry = ::loadWorkflows,
                    onRefresh = ::loadWorkflows,
                    onDownloadClick = { pendingWorkflow = it },
                )

                RepositoryTab.MODULE_STORE -> RepositoryModuleStoreTab(
                    modifier = Modifier.weight(1f),
                    state = moduleStoreState,
                    contentPadding = repositoryContentPadding(bottomContentPadding),
                    onRetry = ::loadModuleStore,
                    onRefresh = ::loadModuleStore,
                    onDownloadClick = { pendingStoreModule = it },
                )

                RepositoryTab.LOCAL_MODULES -> LocalModulesTab(
                    modifier = Modifier.weight(1f),
                    state = localModulesState,
                    contentPadding = repositoryContentPadding(bottomContentPadding),
                    onRefresh = ::refreshLocalModules,
                    onOpenDetails = { detailModule = it },
                    onDelete = { module ->
                        scope.launch {
                            deletePrompt = LocalModuleDeletePrompt(
                                module = module,
                                dependencyNames = withContext(Dispatchers.IO) {
                                    checkModuleDependencies(appContext, module.id)
                                }
                            )
                        }
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomContentPadding + 16.dp),
            onClick = {
                installModuleLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed")
                )
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_add_24),
                    contentDescription = null,
                )
            },
            text = {
                Text(stringResource(R.string.install_module))
            }
        )
    }

    val workflowToDownload = pendingWorkflow
    if (workflowToDownload != null) {
        AlertDialog(
            onDismissRequest = { pendingWorkflow = null },
            title = { Text(stringResource(R.string.dialog_download_workflow_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_download_workflow_message,
                        workflowToDownload.name,
                        workflowToDownload.description
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingWorkflow = null
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_downloading, workflowToDownload.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            val result = RepositoryApiClient.downloadWorkflowRaw(workflowToDownload.download_url)
                            result.onSuccess { jsonString ->
                                importHelper.importFromJson(jsonString)
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_download_failed, error.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_button_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWorkflow = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val storeModuleToDownload = pendingStoreModule
    if (storeModuleToDownload != null) {
        AlertDialog(
            onDismissRequest = { pendingStoreModule = null },
            title = { Text(stringResource(R.string.dialog_download_module_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(
                            R.string.dialog_download_module_message,
                            storeModuleToDownload.name,
                            "",
                            "",
                            "",
                            ""
                        ).substringBefore("\n\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DialogDetailBlock(title = stringResource(R.string.workflow_description)) {
                        Text(
                            text = storeModuleToDownload.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DialogDetailBlock(title = stringResource(R.string.label_version)) {
                        MonospaceDetailText("v${storeModuleToDownload.version}")
                    }
                    DialogDetailBlock(title = stringResource(R.string.label_category)) {
                        Text(
                            text = storeModuleToDownload.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DialogDetailBlock(title = stringResource(R.string.label_permissions)) {
                        if (storeModuleToDownload.permissions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.label_no_permissions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                storeModuleToDownload.permissions.forEach { permission ->
                                    RepositoryTextPill(permission)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingStoreModule = null
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_downloading, storeModuleToDownload.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            val downloadResult = RepositoryApiClient.downloadModule(storeModuleToDownload.download_url)
                            downloadResult.onSuccess { bytes ->
                                val installResult = withContext(Dispatchers.IO) {
                                    prepareStoreModuleInstall(appContext, storeModuleToDownload, bytes)
                                }
                                installResult.onSuccess { result ->
                                    when (result) {
                                        is ModuleInstallResult.ReadyToCommit -> {
                                            pendingStoreModuleInstall = result.pending
                                        }
                                        is ModuleInstallResult.Completed -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_install_success, result.moduleName),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            refreshLocalModules()
                                        }
                                    }
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_install_failed, error.message ?: ""),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_download_failed, error.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_button_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStoreModule = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val storeInstallPrompt = pendingStoreModuleInstall
    if (storeInstallPrompt != null) {
        AlertDialog(
            onDismissRequest = {
                storeInstallPrompt.archiveFile.delete()
                pendingStoreModuleInstall = null
            },
            title = { Text(stringResource(R.string.dialog_module_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_module_conflict_message,
                        storeInstallPrompt.session.manifest.id,
                        storeInstallPrompt.repoModule.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val commitResult = withContext(Dispatchers.IO) {
                                ModuleManager.commitInstall(storeInstallPrompt.session)
                            }
                            storeInstallPrompt.archiveFile.delete()
                            pendingStoreModuleInstall = null
                            commitResult.onSuccess {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_module_replaced,
                                        storeInstallPrompt.repoModule.name
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                refreshLocalModules()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_install_failed, error.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_button_overwrite))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        storeInstallPrompt.archiveFile.delete()
                        pendingStoreModuleInstall = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_button_skip))
                }
            }
        )
    }

    val localInstallPrompt = pendingLocalModuleInstall
    if (localInstallPrompt != null) {
        AlertDialog(
            onDismissRequest = { pendingLocalModuleInstall = null },
            title = { Text(stringResource(R.string.dialog_module_exists_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_module_exists_message,
                        localInstallPrompt.session.manifest.id
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalModuleInstall = null
                        scope.launch {
                            commitLocalModuleInstall(
                                context = context,
                                session = localInstallPrompt.session,
                                onSuccess = ::refreshLocalModules,
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_button_overwrite))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalModuleInstall = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val moduleDetails = detailModule
    if (moduleDetails != null) {
        val inputs = remember(moduleDetails) { moduleDetails.getInputs() }
        val outputs = remember(moduleDetails) {
            runCatching { moduleDetails.getOutputs(null) }.getOrDefault(emptyList())
        }
        val paramTypeLabel = stringResource(R.string.label_param_type)
        AlertDialog(
            onDismissRequest = { detailModule = null },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${moduleDetails.metadata.getLocalizedName(context)} - " +
                            stringResource(R.string.label_module_details),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SelectionContainer {
                        Text(
                            "${stringResource(R.string.label_module_id)}: ${moduleDetails.id}",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    DialogDetailBlock(
                        title = stringResource(R.string.workflow_description)
                    ) {
                        Text(
                            text = moduleDetails.metadata.getLocalizedDescription(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DialogDetailBlock(
                        title = stringResource(R.string.label_inputs)
                    ) {
                        MonospaceDetailText(
                            text = if (inputs.isEmpty()) {
                                stringResource(R.string.label_no_input_params)
                            } else {
                                inputs.mapIndexed { index, input ->
                                    "${index + 1}. ${input.getLocalizedName(context)} (${input.id})\n" +
                                        "   $paramTypeLabel: ${input.staticType.name}"
                                }
                                    .joinToString("\n\n")
                            }
                        )
                    }
                    DialogDetailBlock(
                        title = stringResource(R.string.label_outputs)
                    ) {
                        MonospaceDetailText(
                            text = if (outputs.isEmpty()) {
                                stringResource(R.string.label_no_output_vars)
                            } else {
                                outputs.mapIndexed { index, output ->
                                    "${index + 1}. ${output.getLocalizedName(context)} (${output.id})\n" +
                                        "   $paramTypeLabel: ${output.typeName}"
                                }
                                    .joinToString("\n\n")
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailModule = null }) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }

    val currentDeletePrompt = deletePrompt
    if (currentDeletePrompt != null) {
        val localizedName = currentDeletePrompt.module.metadata.getLocalizedName(context)
        val title =
            if (currentDeletePrompt.dependencyNames.isNotEmpty()) {
                stringResource(R.string.dialog_module_dependency_title)
            } else {
                stringResource(R.string.dialog_delete_module_title)
            }
        val message =
            if (currentDeletePrompt.dependencyNames.isNotEmpty()) {
                buildString {
                    append(
                        context.getString(
                            R.string.dialog_module_dependency_message,
                            currentDeletePrompt.dependencyNames.size,
                            ""
                        )
                    )
                    if (currentDeletePrompt.dependencyNames.isNotEmpty()) {
                        appendLine()
                        currentDeletePrompt.dependencyNames.forEach { appendLine("• $it") }
                    }
                }.trimEnd()
            } else {
                context.getString(R.string.dialog_delete_module_message, localizedName)
            }
        AlertDialog(
            onDismissRequest = { deletePrompt = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletePrompt = null
                        scope.launch {
                            deleteLocalModule(
                                context = context,
                                module = currentDeletePrompt.module,
                                onDeleted = ::refreshLocalModules,
                            )
                        }
                    }
                ) {
                    Text(
                        if (currentDeletePrompt.dependencyNames.isNotEmpty()) {
                            stringResource(R.string.dialog_button_force_delete)
                        } else {
                            stringResource(android.R.string.ok)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePrompt = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RepositoryTabs(
    selectedTab: RepositoryTab,
    onTabSelected: (RepositoryTab) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RepositoryTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(stringResource(tab.titleRes)) }
            )
        }
    }
}

@Composable
private fun RepositoryWorkflowTab(
    modifier: Modifier = Modifier,
    state: RepositoryListState<RepoWorkflow>,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadClick: (RepoWorkflow) -> Unit,
) {
    RepositoryListLayout(
        modifier = modifier,
        isLoading = state.isLoading,
        itemsEmpty = state.items.isEmpty(),
        emptyText = stringResource(R.string.text_no_workflows),
        errorText = state.errorMessage,
        contentPadding = contentPadding,
        onRetry = onRetry,
        onRefresh = onRefresh,
    ) {
        items(state.items, key = { it.id }) { workflow ->
            WorkflowRepoCard(
                workflow = workflow,
                onDownloadClick = { onDownloadClick(workflow) }
            )
        }
    }
}

@Composable
private fun RepositoryModuleStoreTab(
    modifier: Modifier = Modifier,
    state: RepositoryListState<RepoModule>,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadClick: (RepoModule) -> Unit,
) {
    RepositoryListLayout(
        modifier = modifier,
        isLoading = state.isLoading,
        itemsEmpty = state.items.isEmpty(),
        emptyText = stringResource(R.string.text_no_modules),
        errorText = state.errorMessage,
        contentPadding = contentPadding,
        onRetry = onRetry,
        onRefresh = onRefresh,
    ) {
        items(state.items, key = { it.id }) { module ->
            ModuleStoreCard(
                repoModule = module,
                onDownloadClick = { onDownloadClick(module) }
            )
        }
    }
}

@Composable
private fun LocalModulesTab(
    modifier: Modifier = Modifier,
    state: LocalModulesState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpenDetails: (ActionModule) -> Unit,
    onDelete: (ActionModule) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = false,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = false,
                state = pullToRefreshState,
            )
        },
    ) {
        if (state.userModules.isEmpty() && state.builtInModules.isEmpty()) {
            RepositoryFeedbackState(
                text = stringResource(R.string.text_no_modules),
                contentPadding = contentPadding,
                onRetry = onRefresh,
            )
            return@PullToRefreshBox
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.userModules.isNotEmpty()) {
                item {
                    LocalModuleSectionHeader(
                        title = stringResource(
                            R.string.header_user_modules,
                            state.userModules.size
                        )
                    )
                }
                items(state.userModules, key = { it.id }) { module ->
                    LocalModuleCard(
                        module = module,
                        onOpenDetails = { onOpenDetails(module) },
                        onDelete = { onDelete(module) },
                    )
                }
            }

            if (state.builtInModules.isNotEmpty()) {
                item {
                    LocalModuleSectionHeader(
                        title = stringResource(
                            R.string.header_builtin_modules,
                            state.builtInModules.size
                        )
                    )
                }
                items(state.builtInModules, key = { it.id }) { module ->
                    LocalModuleCard(
                        module = module,
                        onOpenDetails = { onOpenDetails(module) },
                        onDelete = { onDelete(module) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryListLayout(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    itemsEmpty: Boolean,
    emptyText: String,
    errorText: String?,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isLoading,
                state = pullToRefreshState,
            )
        },
    ) {
        when {
            isLoading && itemsEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }

            errorText != null && itemsEmpty -> {
                RepositoryFeedbackState(
                    text = errorText,
                    contentPadding = contentPadding,
                    onRetry = onRetry,
                )
            }

            itemsEmpty -> {
                RepositoryFeedbackState(
                    text = emptyText,
                    contentPadding = contentPadding,
                    onRetry = onRetry,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun RepositoryFeedbackState(
    text: String,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun LocalModuleSectionHeader(
    title: String,
) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LocalModuleCard(
    module: ActionModule,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember(module) {
        runCatching { module.getRequiredPermissions(null) }.getOrDefault(emptyList())
    }
    val scriptedModule = module as? ScriptedModule

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    painter = painterResource(
                        if (module.metadata.iconRes != 0) {
                            module.metadata.iconRes
                        } else {
                            R.drawable.rounded_circles_ext_24
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.metadata.getLocalizedName(context),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        RepositoryCategoryPill(module.metadata.getLocalizedCategory(context))
                        IconButton(
                            onClick = onOpenDetails,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_info_24),
                                contentDescription = stringResource(R.string.desc_details),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = module.metadata.getLocalizedDescription(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (scriptedModule != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.module_author_version,
                        scriptedModule.author,
                        scriptedModule.version
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (permissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    permissions.forEach { permission ->
                        PermissionPill(permission)
                    }
                }
            }

            if (scriptedModule != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.desc_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRepoCard(
    workflow: RepoWorkflow,
    onDownloadClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = workflow.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column {
                Text(
                    text = stringResource(R.string.repo_author, workflow.author),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (workflow.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.repo_workflow_tags,
                            workflow.tags.joinToString(", ")
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = workflow.description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            RepositoryDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    MetaText("v${workflow.version}")
                    MetaText("ID: ${workflow.id}", maxLines = 1)
                }
                FilledTonalButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.dialog_button_download),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleStoreCard(
    repoModule: RepoModule,
    onDownloadClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = repoModule.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.repo_author, repoModule.author),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (repoModule.permissions.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.repo_module_permissions_count,
                                repoModule.permissions.size
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                RepositoryCategoryPill(repoModule.category)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = repoModule.description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            RepositoryDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    MetaText("v${repoModule.version}")
                    MetaText("ID: ${repoModule.id}", maxLines = 1)
                }
                FilledTonalButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.dialog_button_download),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryCategoryPill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun PermissionPill(
    permission: Permission,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProvideTextStyle(
                value = MaterialTheme.typography.labelSmall
            ) {
                Text(
                    text = permission.getLocalizedName(context),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RepositoryTextPill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RepositoryDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        thickness = 1.dp
    )
}

@Composable
private fun DetailSection(
    title: String,
    lines: List<String>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogDetailBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.padding(12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MonospaceDetailText(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        lineHeight = 20.sp
    )
}

@Composable
private fun MetaText(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private fun repositoryContentPadding(
    bottomContentPadding: Dp,
): PaddingValues =
    PaddingValues(
        start = 8.dp,
        top = 8.dp,
        end = 8.dp,
        bottom = bottomContentPadding + 96.dp,
    )

private fun loadLocalModulesState(): LocalModulesState {
    if (!StorageManager.modulesDir.exists()) {
        StorageManager.modulesDir.mkdirs()
    }
    val allModules = ModuleRegistry.getAllModules()
    return LocalModulesState(
        userModules = allModules.filter { it is ScriptedModule },
        builtInModules = allModules.filter { it !is ScriptedModule }
    )
}

private fun prepareStoreModuleInstall(
    context: android.content.Context,
    repoModule: RepoModule,
    bytes: ByteArray,
): Result<ModuleInstallResult> {
    val archiveFile = File(context.cacheDir, "repo_module_${System.currentTimeMillis()}.zip")
    return try {
        archiveFile.writeBytes(bytes)
        val installResult = ModuleManager.prepareInstall(context, Uri.fromFile(archiveFile))
        installResult.mapCatching { session ->
            if (ModuleManager.isModuleInstalled(session.manifest.id)) {
                ModuleInstallResult.ReadyToCommit(
                    PendingStoreModuleInstall(
                        repoModule = repoModule,
                        session = session,
                        archiveFile = archiveFile,
                    )
                )
            } else {
                ModuleManager.commitInstall(session).getOrThrow()
                archiveFile.delete()
                ModuleInstallResult.Completed(session.manifest.name)
            }
        }.onFailure {
            archiveFile.delete()
        }
    } catch (error: Exception) {
        archiveFile.delete()
        Result.failure(error)
    }
}

private suspend fun commitLocalModuleInstall(
    context: android.content.Context,
    session: ModuleManager.InstallSession,
    onSuccess: () -> Unit,
) {
    val result = withContext(Dispatchers.IO) {
        ModuleManager.commitInstall(session)
    }
    result.onSuccess { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onSuccess()
    }.onFailure { error ->
        Toast.makeText(
            context,
            context.getString(R.string.dialog_install_failed) + ": " +
                (error.message ?: context.getString(R.string.error_unknown_error)),
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun checkModuleDependencies(
    context: android.content.Context,
    moduleId: String,
): List<String> {
    val workflowManager = WorkflowManager(context)
    return workflowManager.getAllWorkflows()
        .filter { workflow -> workflow.allSteps.any { it.moduleId == moduleId } }
        .mapNotNull { it.name }
}

private suspend fun deleteLocalModule(
    context: android.content.Context,
    module: ActionModule,
    onDeleted: () -> Unit,
) {
    withContext(Dispatchers.IO) {
        val targetDir = File(StorageManager.modulesDir, module.id)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        ModuleRegistry.reset()
        ModuleRegistry.initialize(context.applicationContext)
        ModuleManager.loadModules(context.applicationContext, force = true)
    }
    Toast.makeText(
        context,
        context.getString(R.string.toast_module_deleted),
        Toast.LENGTH_SHORT
    ).show()
    onDeleted()
}
