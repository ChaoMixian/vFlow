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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.chaomixian.vflow.ui.main.fragments.HomeFragment
import com.chaomixian.vflow.ui.main.fragments.ModuleManagementFragment
import com.chaomixian.vflow.ui.main.fragments.SettingsFragment
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBar
import com.chaomixian.vflow.ui.main.glass.LiquidGlassBottomBarItem
import com.chaomixian.vflow.ui.main.navigation.MainRoute
import com.chaomixian.vflow.ui.repository.RepositoryFragment
import com.chaomixian.vflow.ui.workflow_list.WorkflowListFragment
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity

internal enum class MainTopLevelTab(
    val fragmentTag: String,
    val containerId: Int,
    val titleRes: Int,
    val selectedIconRes: Int,
    val unselectedIconRes: Int,
) {
    HOME("main_tab_home", R.id.main_compose_fragment_container_home, R.string.title_home, R.drawable.rounded_home_fill_24, R.drawable.rounded_home_24),
    WORKFLOWS("main_tab_workflows", R.id.main_compose_fragment_container_workflows, R.string.title_workflows, R.drawable.rounded_dashboard_fill_24, R.drawable.rounded_dashboard_24),
    MODULES("main_tab_modules", R.id.main_compose_fragment_container_modules, R.string.title_modules, R.drawable.rounded_sdk_fill_24, R.drawable.rounded_sdk_24),
    REPOSITORY("main_tab_repository", R.id.main_compose_fragment_container_repository, R.string.title_repository, R.drawable.rounded_extension_fill_24, R.drawable.rounded_extension_24),
    SETTINGS("main_tab_settings", R.id.main_compose_fragment_container_settings, R.string.title_settings, R.drawable.rounded_settings_fill_24, R.drawable.rounded_settings_24);

    fun createFragment(): Fragment = when (this) {
        HOME -> HomeFragment()
        WORKFLOWS -> WorkflowListFragment()
        MODULES -> ModuleManagementFragment()
        REPOSITORY -> RepositoryFragment()
        SETTINGS -> SettingsFragment()
    }
}

enum class WorkflowTopBarAction {
    FavoriteFloat,
    SortByName,
    CreateFolder,
    BackupWorkflows,
    ImportWorkflows,
}

interface MainTopBarActionHandler {
    fun onMainTopBarAction(action: WorkflowTopBarAction): Boolean
}

@Composable
internal fun MainActivityContent(
    isReady: Boolean,
    liquidGlassNavBarEnabled: Boolean,
    initialTab: MainTopLevelTab,
    onBackPressedAtRoot: () -> Unit,
    onDisplayTab: (MainTopLevelTab, Int, Int) -> Unit,
    onPrimaryTabChanged: (MainTopLevelTab) -> Unit,
    onWorkflowTopBarAction: (WorkflowTopBarAction) -> Unit,
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
                        liquidGlassEnabled = liquidGlassNavBarEnabled,
                        initialTab = initialTab,
                        onDisplayTab = onDisplayTab,
                        onPrimaryTabChanged = onPrimaryTabChanged,
                        onWorkflowTopBarAction = onWorkflowTopBarAction,
                    )
                }
            }
        )
    }
}

@Composable
private fun MainScreen(
    isReady: Boolean,
    liquidGlassEnabled: Boolean,
    initialTab: MainTopLevelTab,
    onDisplayTab: (MainTopLevelTab, Int, Int) -> Unit,
    onPrimaryTabChanged: (MainTopLevelTab) -> Unit,
    onWorkflowTopBarAction: (WorkflowTopBarAction) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal, pageCount = { MainTopLevelTab.entries.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    val selectedTab = MainTopLevelTab.entries[mainPagerState.selectedPage]
    val loadedPages = remember(initialTab) { mutableStateListOf(initialTab.ordinal) }
    val surfaceColor = MaterialTheme.colorScheme.surface
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
                        WorkflowTopBarActions(onAction = onWorkflowTopBarAction)
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
            innerPadding = innerPadding,
            liquidGlassEnabled = liquidGlassEnabled,
            backdrop = backdrop,
            loadedPages = loadedPages,
            onDisplayTab = onDisplayTab,
        )
    }
}

@Composable
private fun WorkflowTopBarActions(
    onAction: (WorkflowTopBarAction) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { onAction(WorkflowTopBarAction.FavoriteFloat) }) {
        Icon(
            painter = painterResource(R.drawable.rounded_branding_watermark_24),
            contentDescription = stringResource(R.string.workflow_list_menu_favorite_float)
        )
    }

    IconButton(onClick = { onAction(WorkflowTopBarAction.SortByName) }) {
        Icon(
            painter = painterResource(R.drawable.rounded_sort_24),
            contentDescription = stringResource(R.string.workflow_list_menu_sort_by_name)
        )
    }

    Box {
        IconButton(onClick = { overflowExpanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.workflow_item_more_options)
            )
        }

        DropdownMenu(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_create)) },
                onClick = {
                    overflowExpanded = false
                    onAction(WorkflowTopBarAction.CreateFolder)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workflow_list_menu_backup_all)) },
                onClick = {
                    overflowExpanded = false
                    onAction(WorkflowTopBarAction.BackupWorkflows)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workflow_list_menu_import_restore)) },
                onClick = {
                    overflowExpanded = false
                    onAction(WorkflowTopBarAction.ImportWorkflows)
                }
            )
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

@Composable
private fun MainContentPager(
    isReady: Boolean,
    pagerState: PagerState,
    innerPadding: PaddingValues,
    liquidGlassEnabled: Boolean,
    backdrop: LayerBackdrop,
    loadedPages: List<Int>,
    onDisplayTab: (MainTopLevelTab, Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val bottomInsetPx = with(density) { innerPadding.calculateBottomPadding().roundToPx() }
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
        MainTabFragmentPage(
            tab = MainTopLevelTab.entries[page],
            bottomInsetPx = bottomInsetPx,
            shouldDisplay = page in loadedPages,
            onDisplayTab = onDisplayTab,
        )
    }
}

@Composable
private fun MainTabFragmentPage(
    tab: MainTopLevelTab,
    bottomInsetPx: Int,
    shouldDisplay: Boolean,
    onDisplayTab: (MainTopLevelTab, Int, Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = tab.containerId
            }
        },
        update = { container ->
            if (shouldDisplay) {
                onDisplayTab(tab, container.id, bottomInsetPx)
            }
        }
    )
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
