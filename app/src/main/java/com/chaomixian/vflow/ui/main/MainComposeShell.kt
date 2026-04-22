package com.chaomixian.vflow.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.chat.ChatScreen
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.chaomixian.vflow.ui.home.HomeScreen
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBar
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBarItem
import com.chaomixian.vflow.ui.main.navigation.MainRoute
import com.chaomixian.vflow.ui.repository.RepositoryScreen
import com.chaomixian.vflow.ui.settings.SettingsRoute
import com.chaomixian.vflow.ui.workflow_list.WorkflowListRoute
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.rounded.Check

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
    val surfaceColor = MaterialTheme.colorScheme.surface
    var workflowSortMode by rememberSaveable { mutableStateOf(initialWorkflowSortMode) }
    var latestWorkflowAction by remember { mutableStateOf<WorkflowTopBarAction?>(null) }
    var workflowActionVersion by remember { mutableIntStateOf(0) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedTab.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        )
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
                contentPadding = innerPadding,
            )

            MainTopLevelTab.SETTINGS -> SettingsRoute(
                activity = activity,
                extraBottomContentPadding = innerPadding.calculateBottomPadding(),
                modifier = Modifier.fillMaxSize(),
            )
        }
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
