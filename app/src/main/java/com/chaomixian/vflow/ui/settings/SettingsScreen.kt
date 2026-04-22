package com.chaomixian.vflow.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.SearchBarCard
import com.chaomixian.vflow.ui.common.SearchEmptyStateCard
import com.chaomixian.vflow.ui.common.matchesSearch
import com.chaomixian.vflow.ui.common.normalizeSearchQuery
import com.chaomixian.vflow.ui.viewmodel.SettingsUiState
import kotlin.math.roundToInt

data class SettingsScreenActions(
    val onSetDynamicColorEnabled: (Boolean) -> Unit,
    val onSetColorfulWorkflowCardsEnabled: (Boolean) -> Unit,
    val onSetLiquidGlassNavBarEnabled: (Boolean) -> Unit,
    val onSetAppScale: (Float) -> Unit,
    val onOpenLanguageDialog: () -> Unit,
    val onOpenModuleConfig: () -> Unit,
    val onOpenModelConfig: () -> Unit,
    val onSetAutoCheckUpdatesEnabled: (Boolean) -> Unit,
    val onSetAllowShowOnLockScreen: (Boolean) -> Unit,
    val onSetApiEnabled: (Boolean) -> Unit,
    val onOpenApiSettings: () -> Unit,
    val onSetProgressNotificationEnabled: (Boolean) -> Unit,
    val onSetBackgroundServiceNotificationEnabled: (Boolean) -> Unit,
    val onSetForceKeepAliveEnabled: (Boolean) -> Unit,
    val onSetAutoEnableAccessibility: (Boolean) -> Unit,
    val onSetEnableTypeFilter: (Boolean) -> Unit,
    val onSetHideFromRecents: (Boolean) -> Unit,
    val onSetDefaultShellMode: (String) -> Unit,
    val onOpenPermissionManager: () -> Unit,
    val onOpenPermissionGuardian: () -> Unit,
    val onSetLoggingEnabled: (Boolean) -> Unit,
    val onOpenCrashReports: () -> Unit,
    val onExportLogs: () -> Unit,
    val onClearLogs: () -> Unit,
    val onRunDiagnostic: () -> Unit,
    val onOpenKeyTester: () -> Unit,
    val onOpenCoreManagement: () -> Unit,
    val onStartUiInspector: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenUpdatePage: () -> Unit
)

private data class NativeSettingsTone(
    val containerColor: Color,
    val contentColor: Color
)

private enum class SettingsGroupPosition {
    Single,
    Top,
    Middle,
    Bottom
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    extraBottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(searchQuery) { normalizeSearchQuery(searchQuery) }
    val focusManager = LocalFocusManager.current

    val languageSectionTitle = stringResource(R.string.settings_section_language)
    val themeSectionTitle = stringResource(R.string.settings_section_theme)
    val generalSectionTitle = stringResource(R.string.settings_section_general)
    val permissionsSectionTitle = stringResource(R.string.settings_section_permissions_shell)
    val debuggingSectionTitle = stringResource(R.string.settings_section_debugging)
    val aboutSectionTitle = stringResource(R.string.settings_section_about)

    val updateVersionLabel = uiState.updateInfo?.let {
        stringResource(R.string.settings_update_available, it.latestVersion)
    }
    val updateDownloadLabel = stringResource(R.string.settings_update_download)

    val languageTitle = stringResource(R.string.settings_section_language)
    val languageSubtitle = stringResource(R.string.settings_language_desc)

    val dynamicColorTitle = stringResource(R.string.settings_switch_dynamic_color)
    val dynamicColorSubtitle = stringResource(R.string.settings_switch_dynamic_color_desc)
    val colorfulCardsTitle = stringResource(R.string.settings_switch_colorful_workflow_cards)
    val colorfulCardsSubtitle = stringResource(R.string.settings_switch_colorful_workflow_cards_desc)
    val liquidGlassTitle = stringResource(R.string.settings_switch_liquid_glass_nav_bar)
    val liquidGlassSubtitle = stringResource(R.string.settings_switch_liquid_glass_nav_bar_desc)
    val appScaleTitle = stringResource(R.string.settings_app_scale)
    val appScaleSubtitle = stringResource(R.string.settings_app_scale_desc)
    val appScaleValueLabel = stringResource(
        R.string.settings_app_scale_value,
        (uiState.appScale * 100).roundToInt()
    )

    val moduleConfigTitle = stringResource(R.string.settings_module_config)
    val moduleConfigSubtitle = stringResource(R.string.settings_module_config_desc)
    val modelConfigTitle = stringResource(R.string.settings_model_config)
    val modelConfigSubtitle = stringResource(R.string.settings_model_config_desc)
    val autoCheckUpdatesTitle = stringResource(R.string.settings_switch_auto_check_updates)
    val autoCheckUpdatesSubtitle = stringResource(R.string.settings_switch_auto_check_updates_desc)
    val lockScreenTitle = stringResource(R.string.settings_switch_allow_show_on_lock_screen)
    val lockScreenSubtitle = stringResource(R.string.settings_switch_allow_show_on_lock_screen_desc)
    val apiEnabledTitle = stringResource(R.string.settings_switch_api_enabled)
    val apiEnabledSubtitle = stringResource(R.string.settings_switch_api_enabled_desc)
    val apiSettingsTitle = stringResource(R.string.api_settings_label)
    val apiSettingsSubtitle = stringResource(R.string.api_settings_desc)
    val apiStatusValue = stringResource(
        if (uiState.apiRunning) {
            R.string.api_settings_status_running
        } else {
            R.string.api_settings_status_stopped
        }
    )
    val progressNotificationTitle = stringResource(R.string.settings_switch_progress_notification)
    val progressNotificationSubtitle = stringResource(R.string.settings_switch_progress_notification_desc)
    val backgroundServiceTitle = stringResource(R.string.settings_switch_background_service)
    val backgroundServiceSubtitle = stringResource(R.string.settings_switch_background_service_summary)
    val backgroundServiceInfo = stringResource(R.string.settings_switch_background_service_desc)
    val forceKeepAliveTitle = stringResource(R.string.settings_switch_force_keep_alive)
    val forceKeepAliveSubtitle = stringResource(R.string.settings_switch_force_keep_alive_summary)
    val forceKeepAliveInfo = stringResource(R.string.settings_switch_force_keep_alive_desc)
    val autoAccessibilityTitle = if (uiState.isShizukuActive || uiState.isRootAvailable) {
        stringResource(R.string.settings_switch_auto_enable_accessibility)
    } else {
        stringResource(R.string.settings_switch_auto_accessibility_unavailable)
    }
    val autoAccessibilitySubtitle = stringResource(R.string.settings_switch_auto_enable_accessibility_summary)
    val autoAccessibilityInfo = stringResource(R.string.settings_switch_auto_enable_accessibility_desc)
    val typeFilterTitle = stringResource(R.string.settings_switch_enable_type_filter)
    val typeFilterSubtitle = stringResource(R.string.settings_switch_enable_type_filter_summary)
    val typeFilterInfo = stringResource(R.string.settings_switch_enable_type_filter_desc)
    val hideRecentsTitle = stringResource(R.string.settings_switch_hide_from_recents)
    val hideRecentsSubtitle = stringResource(R.string.settings_switch_hide_from_recents_desc)

    val shellPreferenceTitle = stringResource(R.string.settings_shell_preference)
    val shellPreferenceSubtitle = stringResource(R.string.settings_shell_preference_desc)
    val shellShizukuLabel = stringResource(R.string.settings_btn_shell_shizuku)
    val shellRootLabel = stringResource(R.string.settings_btn_shell_root)
    val permissionManagerTitle = stringResource(R.string.settings_section_permissions)
    val permissionManagerSubtitle = stringResource(R.string.settings_section_permissions_desc)
    val permissionGuardianTitle = stringResource(R.string.settings_permission_guardian)
    val permissionGuardianSubtitle = stringResource(R.string.settings_permission_guardian_desc)

    val loggingTitle = stringResource(R.string.settings_switch_enable_logging)
    val loggingSubtitle = stringResource(R.string.settings_switch_enable_logging_summary)
    val loggingInfo = stringResource(R.string.settings_switch_enable_logging_desc)
    val crashReportsTitle = stringResource(R.string.settings_button_crash_reports)
    val crashReportsSubtitle = stringResource(R.string.settings_button_crash_reports_desc)
    val exportLogsLabel = stringResource(R.string.settings_button_export_logs)
    val clearLogsLabel = stringResource(R.string.settings_button_clear_logs)
    val runDiagnosticLabel = stringResource(R.string.settings_button_run_diagnostic)
    val keyTesterLabel = stringResource(R.string.settings_button_key_tester)
    val coreManagementLabel = stringResource(R.string.settings_button_core_management)
    val uiInspectorLabel = stringResource(R.string.settings_button_ui_inspector)

    val aboutTitle = stringResource(R.string.settings_section_about)
    val aboutSubtitle = stringResource(R.string.settings_section_about_desc)

    val showUpdateCard = uiState.updateInfo != null && matchesSearch(
        normalizedQuery,
        updateVersionLabel,
        updateDownloadLabel
    )
    val showLanguageSection = matchesSearch(
        normalizedQuery,
        languageSectionTitle,
        languageTitle,
        languageSubtitle,
        uiState.currentLanguage
    )
    val showThemeSection = listOf(
        dynamicColorTitle, dynamicColorSubtitle,
        colorfulCardsTitle, colorfulCardsSubtitle,
        liquidGlassTitle, liquidGlassSubtitle,
        appScaleTitle, appScaleSubtitle, appScaleValueLabel,
        themeSectionTitle
    ).any { matchesSearch(normalizedQuery, it) }
    val showGeneralSection = listOf(
        generalSectionTitle,
        moduleConfigTitle, moduleConfigSubtitle,
        modelConfigTitle, modelConfigSubtitle,
        autoCheckUpdatesTitle, autoCheckUpdatesSubtitle,
        lockScreenTitle, lockScreenSubtitle,
        apiEnabledTitle, apiEnabledSubtitle,
        apiSettingsTitle, apiSettingsSubtitle, apiStatusValue,
        progressNotificationTitle, progressNotificationSubtitle,
        backgroundServiceTitle, backgroundServiceSubtitle, backgroundServiceInfo,
        forceKeepAliveTitle, forceKeepAliveSubtitle, forceKeepAliveInfo,
        autoAccessibilityTitle, autoAccessibilitySubtitle, autoAccessibilityInfo,
        typeFilterTitle, typeFilterSubtitle, typeFilterInfo,
        hideRecentsTitle, hideRecentsSubtitle
    ).any { matchesSearch(normalizedQuery, it) }
    val showPermissionsSection = listOf(
        permissionsSectionTitle,
        shellPreferenceTitle, shellPreferenceSubtitle, shellShizukuLabel, shellRootLabel,
        permissionManagerTitle, permissionManagerSubtitle,
        permissionGuardianTitle, permissionGuardianSubtitle
    ).any { matchesSearch(normalizedQuery, it) }
    val showDebuggingSection = listOf(
        debuggingSectionTitle,
        loggingTitle, loggingSubtitle, loggingInfo,
        crashReportsTitle, crashReportsSubtitle,
        exportLogsLabel, clearLogsLabel,
        runDiagnosticLabel, keyTesterLabel,
        coreManagementLabel, uiInspectorLabel
    ).any { matchesSearch(normalizedQuery, it) }
    val showAboutSection = matchesSearch(
        normalizedQuery,
        aboutSectionTitle,
        aboutTitle,
        aboutSubtitle
    )
    val hasSearchResults = showUpdateCard || showLanguageSection || showThemeSection ||
        showGeneralSection || showPermissionsSection || showDebuggingSection || showAboutSection

    LazyColumn(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        },
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 8.dp + extraBottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SearchBarCard(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholderRes = R.string.settings_search_placeholder,
                clearContentDescriptionRes = R.string.settings_search_clear,
                onClearFocus = { focusManager.clearFocus() }
            )
        }

        if (!hasSearchResults) {
            item {
                SearchEmptyStateCard(
                    titleRes = R.string.settings_search_no_results,
                    hintRes = R.string.settings_search_no_results_hint
                )
            }
        }

        if (showUpdateCard) {
            item {
                UpdateCard(
                    versionLabel = updateVersionLabel.orEmpty(),
                    onClick = actions.onOpenUpdatePage
                )
            }
        }

        if (showLanguageSection) item {
            SettingsSection(title = languageSectionTitle) {
                NativeEntryRow(
                    title = languageTitle,
                    subtitle = languageSubtitle,
                    value = uiState.currentLanguage,
                    icon = Icons.Default.Language,
                    tone = languageTone(),
                    position = SettingsGroupPosition.Single,
                    onClick = actions.onOpenLanguageDialog
                )
            }
        }

        if (showThemeSection) item {
            SettingsSection(title = themeSectionTitle) {
                NativeSwitchRow(
                    title = dynamicColorTitle,
                    subtitle = dynamicColorSubtitle,
                    icon = Icons.Default.Palette,
                    tone = cloudTone(),
                    position = SettingsGroupPosition.Top,
                    checked = uiState.dynamicColorEnabled,
                    onCheckedChange = actions.onSetDynamicColorEnabled
                )
                NativeSwitchRow(
                    title = colorfulCardsTitle,
                    subtitle = colorfulCardsSubtitle,
                    icon = Icons.Default.AutoAwesome,
                    tone = accentTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.colorfulWorkflowCardsEnabled,
                    onCheckedChange = actions.onSetColorfulWorkflowCardsEnabled
                )
                NativeSwitchRow(
                    title = liquidGlassTitle,
                    subtitle = liquidGlassSubtitle,
                    icon = Icons.Default.BlurOn,
                    tone = softTone(),
                    checked = uiState.liquidGlassNavBarEnabled,
                    position = SettingsGroupPosition.Middle,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = actions.onSetLiquidGlassNavBarEnabled
                )
                NativeSliderRow(
                    title = appScaleTitle,
                    subtitle = appScaleSubtitle,
                    icon = Icons.Default.Tune,
                    tone = warningTone(),
                    position = SettingsGroupPosition.Bottom,
                    valueLabel = appScaleValueLabel,
                    initialSliderValue = uiState.appScale * 100f,
                    onScaleChange = actions.onSetAppScale
                )
            }
        }

        if (showGeneralSection) item {
            SettingsSection(title = generalSectionTitle) {
                NativeEntryRow(
                    title = moduleConfigTitle,
                    subtitle = moduleConfigSubtitle,
                    icon = Icons.Default.Settings,
                    tone = paletteTone(),
                    position = SettingsGroupPosition.Top,
                    onClick = actions.onOpenModuleConfig
                )
                NativeEntryRow(
                    title = modelConfigTitle,
                    subtitle = modelConfigSubtitle,
                    icon = Icons.Default.AutoAwesome,
                    tone = paletteTone(),
                    position = SettingsGroupPosition.Middle,
                    onClick = actions.onOpenModelConfig
                )
                NativeSwitchRow(
                    title = autoCheckUpdatesTitle,
                    subtitle = autoCheckUpdatesSubtitle,
                    icon = Icons.Default.SystemUpdate,
                    tone = warmTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.autoCheckUpdatesEnabled,
                    onCheckedChange = actions.onSetAutoCheckUpdatesEnabled
                )
                NativeSwitchRow(
                    title = lockScreenTitle,
                    subtitle = lockScreenSubtitle,
                    icon = Icons.Default.Lock,
                    tone = warmTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.allowShowOnLockScreen,
                    onCheckedChange = actions.onSetAllowShowOnLockScreen
                )
                NativeSwitchRow(
                    title = apiEnabledTitle,
                    subtitle = apiEnabledSubtitle,
                    icon = Icons.Default.Cloud,
                    tone = cloudTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.apiRunning,
                    onCheckedChange = actions.onSetApiEnabled
                )
                NativeEntryRow(
                    title = apiSettingsTitle,
                    subtitle = apiSettingsSubtitle,
                    value = apiStatusValue,
                    icon = Icons.Default.Cloud,
                    tone = cloudTone(),
                    position = SettingsGroupPosition.Middle,
                    onClick = actions.onOpenApiSettings
                )
                NativeSwitchRow(
                    title = progressNotificationTitle,
                    subtitle = progressNotificationSubtitle,
                    icon = Icons.Default.Notifications,
                    tone = accentTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.progressNotificationEnabled,
                    onCheckedChange = actions.onSetProgressNotificationEnabled
                )
                NativeSwitchRow(
                    title = backgroundServiceTitle,
                    subtitle = backgroundServiceSubtitle,
                    icon = Icons.Default.Notifications,
                    tone = accentTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.backgroundServiceNotificationEnabled,
                    onCheckedChange = actions.onSetBackgroundServiceNotificationEnabled,
                    infoText = backgroundServiceInfo
                )
                NativeSwitchRow(
                    title = forceKeepAliveTitle,
                    subtitle = forceKeepAliveSubtitle,
                    icon = Icons.Default.Security,
                    tone = warningTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.forceKeepAliveEnabled,
                    onCheckedChange = actions.onSetForceKeepAliveEnabled,
                    infoText = forceKeepAliveInfo
                )
                NativeSwitchRow(
                    title = autoAccessibilityTitle,
                    subtitle = autoAccessibilitySubtitle,
                    icon = Icons.Default.Security,
                    tone = warningTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = (uiState.isShizukuActive || uiState.isRootAvailable) &&
                        uiState.autoEnableAccessibility,
                    enabled = uiState.isShizukuActive || uiState.isRootAvailable,
                    onCheckedChange = actions.onSetAutoEnableAccessibility,
                    infoText = autoAccessibilityInfo
                )
                NativeSwitchRow(
                    title = typeFilterTitle,
                    subtitle = typeFilterSubtitle,
                    icon = Icons.Default.Tune,
                    tone = languageTone(),
                    position = SettingsGroupPosition.Middle,
                    checked = uiState.enableTypeFilter,
                    onCheckedChange = actions.onSetEnableTypeFilter,
                    infoText = typeFilterInfo
                )
                NativeSwitchRow(
                    title = hideRecentsTitle,
                    subtitle = hideRecentsSubtitle,
                    icon = Icons.Default.VisibilityOff,
                    tone = languageTone(),
                    position = SettingsGroupPosition.Bottom,
                    checked = uiState.hideFromRecents,
                    onCheckedChange = actions.onSetHideFromRecents
                )
            }
        }

        if (showPermissionsSection) item {
            SettingsSection(title = permissionsSectionTitle) {
                ShellModeCard(
                    title = shellPreferenceTitle,
                    subtitle = shellPreferenceSubtitle,
                    primaryLabel = shellShizukuLabel,
                    secondaryLabel = shellRootLabel,
                    selectedMode = uiState.defaultShellMode,
                    position = SettingsGroupPosition.Top,
                    onModeSelected = actions.onSetDefaultShellMode
                )
                NativeEntryRow(
                    title = permissionManagerTitle,
                    subtitle = permissionManagerSubtitle,
                    icon = Icons.Default.Security,
                    tone = warningTone(),
                    position = SettingsGroupPosition.Middle,
                    onClick = actions.onOpenPermissionManager
                )
                NativeEntryRow(
                    title = permissionGuardianTitle,
                    subtitle = permissionGuardianSubtitle,
                    icon = Icons.Default.Lock,
                    tone = warningTone(),
                    position = SettingsGroupPosition.Bottom,
                    onClick = actions.onOpenPermissionGuardian
                )
            }
        }

        if (showDebuggingSection) item {
            SettingsSection(title = debuggingSectionTitle) {
                NativeSwitchRow(
                    title = loggingTitle,
                    subtitle = loggingSubtitle,
                    icon = Icons.Default.BugReport,
                    tone = accentTone(),
                    position = SettingsGroupPosition.Top,
                    checked = uiState.loggingEnabled,
                    onCheckedChange = actions.onSetLoggingEnabled,
                    infoText = loggingInfo
                )
                NativeEntryRow(
                    title = crashReportsTitle,
                    subtitle = crashReportsSubtitle,
                    icon = Icons.Default.BugReport,
                    tone = accentTone(),
                    position = SettingsGroupPosition.Middle,
                    onClick = actions.onOpenCrashReports
                )
                SettingsButtonRow(
                    primaryLabel = exportLogsLabel,
                    onPrimaryClick = actions.onExportLogs,
                    primaryEnabled = uiState.loggingEnabled,
                    secondaryLabel = clearLogsLabel,
                    onSecondaryClick = actions.onClearLogs,
                    secondaryEnabled = uiState.loggingEnabled,
                    position = SettingsGroupPosition.Middle
                )
                SettingsButtonRow(
                    primaryLabel = runDiagnosticLabel,
                    onPrimaryClick = actions.onRunDiagnostic,
                    primaryEnabled = uiState.loggingEnabled,
                    secondaryLabel = keyTesterLabel,
                    onSecondaryClick = actions.onOpenKeyTester,
                    position = SettingsGroupPosition.Middle
                )
                SettingsButtonRow(
                    primaryLabel = coreManagementLabel,
                    onPrimaryClick = actions.onOpenCoreManagement,
                    secondaryLabel = uiInspectorLabel,
                    onSecondaryClick = actions.onStartUiInspector,
                    position = SettingsGroupPosition.Bottom
                )
            }
        }

        if (showAboutSection) item {
            SettingsSection(title = aboutSectionTitle) {
                NativeEntryRow(
                    title = aboutTitle,
                    subtitle = aboutSubtitle,
                    icon = Icons.Default.Info,
                    tone = neutralTone(),
                    position = SettingsGroupPosition.Single,
                    onClick = actions.onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(
    versionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NativeIconBadge(
                icon = Icons.Default.Info,
                tone = NativeSettingsTone(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.settings_update_download),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsItemSurface(
    position: SettingsGroupPosition,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = settingsItemShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun NativeSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: NativeSettingsTone,
    position: SettingsGroupPosition,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    infoText: String? = null
) {
    SettingsItemSurface(position = position) {
        NativeListRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            tone = tone,
            infoText = infoText,
            enabled = enabled,
            onClick = { onCheckedChange(!checked) },
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
    }
}

@Composable
private fun NativeEntryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: NativeSettingsTone,
    position: SettingsGroupPosition,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true
) {
    SettingsItemSurface(position = position) {
        NativeListRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            tone = tone,
            enabled = enabled,
            onClick = onClick,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    value?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun NativeSliderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: NativeSettingsTone,
    position: SettingsGroupPosition,
    valueLabel: String,
    initialSliderValue: Float,
    onScaleChange: (Float) -> Unit
) {
    var sliderValue by remember(initialSliderValue) { mutableFloatStateOf(initialSliderValue) }

    SettingsItemSurface(position = position) {
        NativeListRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            tone = tone,
            trailing = {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 76.dp, end = 20.dp, bottom = 12.dp),
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 75f..125f,
            steps = 9,
            onValueChangeFinished = {
                onScaleChange(sliderValue / 100f)
            }
        )
    }
}

@Composable
private fun NativeListRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: NativeSettingsTone,
    modifier: Modifier = Modifier,
    infoText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    var showInfoDialog by remember(infoText) { mutableStateOf(false) }

    ListItem(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            } else {
                Modifier
            }
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (infoText != null) {
                    IconButton(
                        modifier = Modifier.size(28.dp),
                        onClick = { showInfoDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_more_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            NativeIconBadge(icon = icon, tone = tone)
        },
        trailingContent = trailing
    )

    if (showInfoDialog && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(text = title) },
            text = { Text(text = infoText) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun SettingsButtonRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    position: SettingsGroupPosition
) {
    SettingsItemSurface(position = position) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = primaryLabel)
            }
            FilledTonalButton(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onSecondaryClick,
                enabled = secondaryEnabled,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = secondaryLabel)
            }
        }
    }
}

@Composable
private fun NativeIconBadge(
    icon: ImageVector,
    tone: NativeSettingsTone
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tone.containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.contentColor
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellModeCard(
    title: String,
    subtitle: String,
    primaryLabel: String,
    secondaryLabel: String,
    selectedMode: String,
    position: SettingsGroupPosition,
    onModeSelected: (String) -> Unit
) {
    SettingsItemSurface(position = position) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("shizuku", "root").forEachIndexed { index, mode ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = {
                            Text(
                                text = if (mode == "root") secondaryLabel else primaryLabel
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun languageTone() = NativeSettingsTone(
    containerColor = Color(0xFFC7C7C7),
    contentColor = Color(0xFF474747)
)

@Composable
private fun paletteTone() = NativeSettingsTone(
    containerColor = Color(0xFFF2C041),
    contentColor = Color(0xFF663D12)
)

@Composable
private fun accentTone() = NativeSettingsTone(
    containerColor = Color(0xFFF3B1E1),
    contentColor = Color(0xFF811751)
)

@Composable
private fun warningTone() = NativeSettingsTone(
    containerColor = Color(0xFFF4B98B),
    contentColor = Color(0xFF663D12)
)

@Composable
private fun neutralTone() = NativeSettingsTone(
    containerColor = Color(0xFF80D2EF),
    contentColor = Color(0xFF1F4D5B)
)

@Composable
private fun warmTone() = NativeSettingsTone(
    containerColor = Color(0xFF95D890),
    contentColor = Color(0xFF215130)
)

@Composable
private fun cloudTone() = NativeSettingsTone(
    containerColor = Color(0xFF83D1FB),
    contentColor = Color(0xFF1E4C65)
)

@Composable
private fun softTone() = NativeSettingsTone(
    containerColor = Color(0xFFA9C8FA),
    contentColor = Color(0xFF1A3F99)
)

private fun settingsItemShape(position: SettingsGroupPosition): RoundedCornerShape =
    when (position) {
        SettingsGroupPosition.Single -> RoundedCornerShape(28.dp)
        SettingsGroupPosition.Top -> RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 10.dp,
            bottomEnd = 10.dp
        )
        SettingsGroupPosition.Middle -> RoundedCornerShape(10.dp)
        SettingsGroupPosition.Bottom -> RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp
        )
    }
