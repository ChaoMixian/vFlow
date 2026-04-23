package com.chaomixian.vflow.ui.main

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.chat.ChatConversation
import com.chaomixian.vflow.ui.chat.ChatProvider
import com.chaomixian.vflow.ui.chat.ChatScreen
import com.chaomixian.vflow.ui.chat.ChatUiState
import com.chaomixian.vflow.ui.chat.ChatViewModel
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.chaomixian.vflow.ui.home.HomeScreen
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBar
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBarItem
import com.chaomixian.vflow.ui.main.navigation.MainRoute
import com.chaomixian.vflow.ui.repository.RepositoryScreen
import com.chaomixian.vflow.ui.settings.ModelConfigActivity
import com.chaomixian.vflow.ui.settings.SettingsRoute
import com.chaomixian.vflow.ui.workflow_list.WorkflowListRoute
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.shape.RoundedCornerShape

internal enum class MainTopLevelTab(
    val fragmentTag: String,
    val titleRes: Int,
    val selectedIconRes: Int,
    val unselectedIconRes: Int,
) {
    HOME("main_tab_home", R.string.title_home, R.drawable.rounded_home_fill_24, R.drawable.rounded_home_24),
    WORKFLOWS("main_tab_workflows", R.string.title_workflows, R.drawable.rounded_dashboard_fill_24, R.drawable.rounded_dashboard_24),
    CHAT("main_tab_chat", R.string.title_chat, R.drawable.rounded_star_shine_fill_24, R.drawable.rounded_star_shine_24),
    REPOSITORY("main_tab_repository", R.string.title_repository, R.drawable.rounded_sdk_fill_24, R.drawable.rounded_sdk_24),
    SETTINGS("main_tab_settings", R.string.title_settings, R.drawable.rounded_settings_fill_24, R.drawable.rounded_settings_24);
}

enum class WorkflowTopBarAction {
    FavoriteFloat,
    SortDefault,
    SortByName,
    SortByRecentModified,
    SortFavoritesFirst,
    CreateFolder,
    BackupWorkflows,
    ImportWorkflows,
}

enum class WorkflowSortMode {
    Default,
    Name,
    RecentModified,
    FavoritesFirst,
}

enum class ChatTopBarAction {
    NewConversation,
    ToggleSideSheet,
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun MainActivityContent(
    isReady: Boolean,
    liquidGlassNavBarEnabled: Boolean,
    activity: MainActivity,
    initialTab: MainTopLevelTab,
    initialWorkflowSortMode: WorkflowSortMode,
    onBackPressedAtRoot: () -> Unit,
    onPrimaryTabChanged: (MainTopLevelTab) -> Unit,
) {
    MaterialTheme(colorScheme = ThemeUtils.getAppColorScheme()) {
        val backStack = rememberNavBackStack(MainRoute.Main)
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            onBack = onBackPressedAtRoot,
            entryProvider = entryProvider {
                entry<MainRoute.Main> {
                    MainScreen(
                        isReady = isReady,
                        activity = activity,
                        liquidGlassEnabled = liquidGlassNavBarEnabled,
                        initialTab = initialTab,
                        initialWorkflowSortMode = initialWorkflowSortMode,
                        onPrimaryTabChanged = onPrimaryTabChanged,
                    )
                }
            }
        )
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun MainScreen(
    isReady: Boolean,
    activity: MainActivity,
    liquidGlassEnabled: Boolean,
    initialTab: MainTopLevelTab,
    initialWorkflowSortMode: WorkflowSortMode,
    onPrimaryTabChanged: (MainTopLevelTab) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal, pageCount = { MainTopLevelTab.entries.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    val selectedTab = MainTopLevelTab.entries[mainPagerState.selectedPage]
    val loadedPages = remember(initialTab) { mutableStateListOf(initialTab.ordinal) }
    val chatViewModel: ChatViewModel = viewModel()
    val chatUiState by chatViewModel.uiState.collectAsState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    var workflowSortMode by rememberSaveable { mutableStateOf(initialWorkflowSortMode) }
    var latestWorkflowAction by remember { mutableStateOf<WorkflowTopBarAction?>(null) }
    var workflowActionVersion by remember { mutableIntStateOf(0) }
    var chatSideSheetVisible by rememberSaveable { mutableStateOf(false) }
    var chatDraftResetVersion by rememberSaveable { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage !in loadedPages) {
            loadedPages += pagerState.currentPage
        }
        mainPagerState.syncPage()
    }

    LaunchedEffect(mainPagerState.selectedPage) {
        onPrimaryTabChanged(MainTopLevelTab.entries[mainPagerState.selectedPage])
    }

    BackHandler(enabled = mainPagerState.selectedPage != MainTopLevelTab.HOME.ordinal) {
        mainPagerState.animateToPage(MainTopLevelTab.HOME.ordinal)
    }

    BackHandler(enabled = chatSideSheetVisible) {
        chatSideSheetVisible = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (selectedTab == MainTopLevelTab.CHAT) {
                            ChatTopBarTitle(
                                uiState = chatUiState,
                                onSelectPreset = chatViewModel::selectPreset,
                            )
                        } else {
                            Text(
                                stringResource(selectedTab.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == MainTopLevelTab.WORKFLOWS) {
                            WorkflowTopBarActions(
                                sortMode = workflowSortMode,
                                onAction = { action ->
                                    workflowSortMode = when (action) {
                                        WorkflowTopBarAction.SortDefault -> WorkflowSortMode.Default
                                        WorkflowTopBarAction.SortByName -> WorkflowSortMode.Name
                                        WorkflowTopBarAction.SortByRecentModified -> WorkflowSortMode.RecentModified
                                        WorkflowTopBarAction.SortFavoritesFirst -> WorkflowSortMode.FavoritesFirst
                                        else -> workflowSortMode
                                    }
                                    latestWorkflowAction = action
                                    workflowActionVersion += 1
                                }
                            )
                        } else if (selectedTab == MainTopLevelTab.CHAT) {
                            ChatTopBarActions(
                                onAction = { action ->
                                    when (action) {
                                        ChatTopBarAction.NewConversation -> {
                                            chatViewModel.newConversation()
                                            chatDraftResetVersion += 1
                                            chatSideSheetVisible = false
                                        }

                                        ChatTopBarAction.ToggleSideSheet -> {
                                            chatSideSheetVisible = !chatSideSheetVisible
                                        }
                                    }
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (liquidGlassEnabled) {
                    LiquidGlassBottomBarContainer(
                        selectedTab = selectedTab,
                        onTabSelected = { mainPagerState.animateToPage(it.ordinal) },
                        backdrop = backdrop,
                    )
                } else {
                    StandardBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { mainPagerState.animateToPage(it.ordinal) },
                    )
                }
            }
        ) { innerPadding ->
            MainContentPager(
                isReady = isReady,
                pagerState = mainPagerState.pagerState,
                selectedPage = mainPagerState.selectedPage,
                innerPadding = innerPadding,
                liquidGlassEnabled = liquidGlassEnabled,
                backdrop = backdrop,
                loadedPages = loadedPages,
                activity = activity,
                workflowSortMode = workflowSortMode,
                workflowAction = latestWorkflowAction,
                workflowActionVersion = workflowActionVersion,
                chatDraftResetVersion = chatDraftResetVersion,
                chatViewModel = chatViewModel,
            )
        }

        if (selectedTab == MainTopLevelTab.CHAT) {
            val context = LocalContext.current
            ChatHistorySideSheet(
                visible = chatSideSheetVisible,
                uiState = chatUiState,
                onDismiss = { chatSideSheetVisible = false },
                onNewConversation = {
                    chatViewModel.newConversation()
                    chatDraftResetVersion += 1
                    chatSideSheetVisible = false
                },
                onSelectConversation = { conversationId ->
                    chatViewModel.selectConversation(conversationId)
                    chatSideSheetVisible = false
                },
                onDeleteConversation = { conversationId ->
                    val title = chatUiState.conversations.find { it.id == conversationId }?.title.orEmpty()
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.dialog_delete_title)
                        .setMessage(context.getString(R.string.chat_delete_conversation_message, title))
                        .setNegativeButton(R.string.common_cancel, null)
                        .setPositiveButton(R.string.common_delete) { _, _ ->
                            chatViewModel.deleteConversation(conversationId)
                        }
                        .show()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkflowTopBarActions(
    sortMode: WorkflowSortMode,
    onAction: (WorkflowTopBarAction) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { onAction(WorkflowTopBarAction.FavoriteFloat) }) {
        Icon(
            painter = painterResource(R.drawable.rounded_branding_watermark_24),
            contentDescription = stringResource(R.string.workflow_list_menu_favorite_float)
        )
    }

    Box {
        IconButton(
            onClick = { sortMenuExpanded = true },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (sortMode != WorkflowSortMode.Default) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.workflow_list_menu_sort)
            )
        }

        DropdownMenuPopup(
            expanded = sortMenuExpanded,
            onDismissRequest = { sortMenuExpanded = false }
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
            ) {
                WorkflowSortMenuItem(
                    text = stringResource(R.string.workflow_list_sort_default),
                    selected = sortMode == WorkflowSortMode.Default,
                    index = 0,
                    count = 4,
                    onClick = {
                        sortMenuExpanded = false
                        onAction(WorkflowTopBarAction.SortDefault)
                    }
                )
                WorkflowSortMenuItem(
                    text = stringResource(R.string.workflow_list_sort_name),
                    selected = sortMode == WorkflowSortMode.Name,
                    index = 1,
                    count = 4,
                    onClick = {
                        sortMenuExpanded = false
                        onAction(WorkflowTopBarAction.SortByName)
                    }
                )
                WorkflowSortMenuItem(
                    text = stringResource(R.string.workflow_list_sort_recent_modified),
                    selected = sortMode == WorkflowSortMode.RecentModified,
                    index = 2,
                    count = 4,
                    onClick = {
                        sortMenuExpanded = false
                        onAction(WorkflowTopBarAction.SortByRecentModified)
                    }
                )
                WorkflowSortMenuItem(
                    text = stringResource(R.string.workflow_list_sort_favorites_first),
                    selected = sortMode == WorkflowSortMode.FavoritesFirst,
                    index = 3,
                    count = 4,
                    onClick = {
                        sortMenuExpanded = false
                        onAction(WorkflowTopBarAction.SortFavoritesFirst)
                    }
                )
            }
        }
    }

    Box {
        IconButton(onClick = { overflowExpanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.workflow_item_more_options)
            )
        }

        DropdownMenuPopup(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false }
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
            ) {
                WorkflowActionMenuItem(
                    text = stringResource(R.string.folder_create),
                    index = 0,
                    count = 3,
                    onClick = {
                        overflowExpanded = false
                        onAction(WorkflowTopBarAction.CreateFolder)
                    }
                )
                WorkflowActionMenuItem(
                    text = stringResource(R.string.workflow_list_menu_backup_all),
                    index = 1,
                    count = 3,
                    onClick = {
                        overflowExpanded = false
                        onAction(WorkflowTopBarAction.BackupWorkflows)
                    }
                )
                WorkflowActionMenuItem(
                    text = stringResource(R.string.workflow_list_menu_import_restore),
                    index = 2,
                    count = 3,
                    onClick = {
                        overflowExpanded = false
                        onAction(WorkflowTopBarAction.ImportWorkflows)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatTopBarActions(
    onAction: (ChatTopBarAction) -> Unit,
) {
    IconButton(onClick = { onAction(ChatTopBarAction.NewConversation) }) {
        Icon(
            painter = painterResource(R.drawable.rounded_add_comment_24),
            contentDescription = stringResource(R.string.chat_topbar_new_conversation)
        )
    }

    IconButton(onClick = { onAction(ChatTopBarAction.ToggleSideSheet) }) {
        Icon(
            painter = painterResource(R.drawable.rounded_menu_open_24),
            contentDescription = stringResource(R.string.chat_topbar_toggle_side_sheet)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatTopBarTitle(
    uiState: ChatUiState,
    onSelectPreset: (String) -> Unit,
) {
    val context = LocalContext.current
    val activeConversation = remember(uiState.activeConversationId, uiState.conversations) {
        uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
    }
    val activePreset = remember(uiState.defaultPresetId, uiState.presets, activeConversation?.presetId) {
        val preferredId = activeConversation?.presetId ?: uiState.defaultPresetId
        uiState.presets.firstOrNull { it.id == preferredId } ?: uiState.presets.firstOrNull()
    }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = buildChatToolbarLabel(activePreset?.name, activePreset?.model),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val menuCount = uiState.presets.size + 1
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
            ) {
                if (uiState.presets.isEmpty()) {
                    DropdownMenuItem(
                        onClick = {},
                        enabled = false,
                        text = { Text(stringResource(R.string.chat_no_saved_models)) },
                        shape = MenuDefaults.itemShape(index = 0, count = menuCount).shape,
                    )
                } else {
                    uiState.presets.forEachIndexed { index, preset ->
                        DropdownMenuItem(
                            selected = preset.id == activePreset?.id,
                            text = {
                                Text(
                                    text = buildChatToolbarMenuLabel(preset.name, preset.providerEnum, preset.model),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelectPreset(preset.id)
                            },
                            shapes = MenuDefaults.itemShape(index = index, count = menuCount),
                            selectedLeadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            },
                            colors = MenuDefaults.selectableItemColors(),
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_config_model_action)) },
                    onClick = {
                        expanded = false
                        context.startActivity(ModelConfigActivity.createIntent(context))
                    },
                    shape = MenuDefaults.itemShape(index = menuCount - 1, count = menuCount).shape,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkflowSortMenuItem(
    text: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        selected = selected,
        text = { Text(text) },
        onClick = onClick,
        shapes = MenuDefaults.itemShape(index = index, count = count),
        selectedLeadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null
            )
        },
        colors = MenuDefaults.selectableItemColors(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkflowActionMenuItem(
    text: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text) },
        shape = MenuDefaults.itemShape(index = index, count = count).shape,
    )
}

@Composable
private fun ChatHistorySideSheet(
    visible: Boolean,
    uiState: ChatUiState,
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f))
                    .clickable(onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.84f),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_side_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.chat_side_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    FilledTonalButton(
                        onClick = onNewConversation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text(stringResource(R.string.chat_topbar_new_conversation))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.conversations,
                            key = { it.id },
                        ) { conversation ->
                            ChatHistorySideSheetItem(
                                conversation = conversation,
                                selected = conversation.id == uiState.activeConversationId,
                                presetName = uiState.presets.firstOrNull { it.id == conversation.presetId }?.name,
                                onClick = { onSelectConversation(conversation.id) },
                                onDelete = { onDeleteConversation(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistorySideSheetItem(
    conversation: ChatConversation,
    selected: Boolean,
    presetName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = conversation.messages.lastOrNull()?.content
                    ?.replace('\n', ' ')
                    ?.trim()
                    .orEmpty()
                    .ifBlank { stringResource(R.string.chat_side_sheet_empty_preview) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presetName ?: stringResource(R.string.chat_model_unconfigured),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = DateFormat.format("HH:mm", conversation.updatedAtMillis).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StandardBottomBar(
    selectedTab: MainTopLevelTab,
    onTabSelected: (MainTopLevelTab) -> Unit,
) {
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
        MainTopLevelTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onTabSelected(tab) },
                alwaysShowLabel = false,
                icon = {
                    Icon(
                        painter = painterResource(if (selected) tab.selectedIconRes else tab.unselectedIconRes),
                        contentDescription = stringResource(tab.titleRes)
                    )
                },
                label = { Text(stringResource(tab.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun LiquidGlassBottomBarContainer(
    selectedTab: MainTopLevelTab,
    onTabSelected: (MainTopLevelTab) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassBottomBar(
            selectedIndex = selectedTab.ordinal,
            onSelected = { onTabSelected(MainTopLevelTab.entries[it]) },
            backdrop = backdrop,
            tabsCount = MainTopLevelTab.entries.size,
        ) {
            MainTopLevelTab.entries.forEach { tab ->
                LiquidGlassBottomBarItem(
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                    onClick = {
                        if (selectedTab != tab) {
                            onTabSelected(tab)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (selectedTab == tab) tab.selectedIconRes else tab.unselectedIconRes
                        ),
                        contentDescription = stringResource(tab.titleRes),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(tab.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun MainContentPager(
    isReady: Boolean,
    activity: MainActivity,
    pagerState: PagerState,
    selectedPage: Int,
    innerPadding: PaddingValues,
    liquidGlassEnabled: Boolean,
    backdrop: LayerBackdrop,
    loadedPages: List<Int>,
    workflowSortMode: WorkflowSortMode,
    workflowAction: WorkflowTopBarAction?,
    workflowActionVersion: Int,
    chatDraftResetVersion: Int,
    chatViewModel: ChatViewModel,
) {
    if (!isReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    HorizontalPager(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
            .then(if (liquidGlassEnabled) Modifier.layerBackdrop(backdrop) else Modifier),
        state = pagerState,
        beyondViewportPageCount = MainTopLevelTab.entries.size - 1,
        userScrollEnabled = false,
    ) { page ->
        val tab = MainTopLevelTab.entries[page]
        when (tab) {
            MainTopLevelTab.HOME -> HomeScreen(
                isActive = selectedPage == page,
                bottomContentPadding = innerPadding.calculateBottomPadding(),
                modifier = Modifier.fillMaxSize(),
            )

            MainTopLevelTab.WORKFLOWS -> WorkflowListRoute(
                activity = activity,
                isActive = selectedPage == page,
                workflowSortMode = workflowSortMode,
                workflowAction = workflowAction,
                workflowActionVersion = workflowActionVersion,
                extraBottomPadding = innerPadding.calculateBottomPadding(),
                modifier = Modifier.fillMaxSize(),
            )

            MainTopLevelTab.REPOSITORY -> RepositoryScreen(
                modifier = Modifier.fillMaxSize(),
                bottomContentPadding = innerPadding.calculateBottomPadding(),
            )

            MainTopLevelTab.CHAT -> ChatScreen(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                newConversationVersion = chatDraftResetVersion,
                chatViewModel = chatViewModel,
            )

            MainTopLevelTab.SETTINGS -> SettingsRoute(
                activity = activity,
                extraBottomContentPadding = innerPadding.calculateBottomPadding(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun buildChatToolbarLabel(
    presetName: String?,
    modelName: String?,
): String {
    val primary = presetName?.trim().orEmpty().ifBlank {
        modelName?.trim().orEmpty().ifBlank { "Chat" }
    }
    return if (primary.length > 20) {
        "${primary.take(20)}..."
    } else {
        primary
    }
}

private fun buildChatToolbarMenuLabel(
    presetName: String,
    provider: ChatProvider,
    modelName: String,
): String {
    val raw = "$presetName · ${provider.displayName} · $modelName"
    return if (raw.length > 42) {
        "${raw.take(42)}..."
    } else {
        raw
    }
}

private class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navigationJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navigationJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        navigationJob = coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(targetIndex)
            } finally {
                isNavigating = false
                if (pagerState.currentPage != targetIndex) {
                    selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberMainPagerState(
    pagerState: PagerState,
): MainPagerState {
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        MainPagerState(
            pagerState = pagerState,
            coroutineScope = coroutineScope,
        )
    }
}
