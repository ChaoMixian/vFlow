package com.chaomixian.vflow.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaomixian.vflow.api.ApiService
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.data.update.UpdateChecker
import com.chaomixian.vflow.data.update.UpdateInfo
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.OverlayUiPreferences
import com.chaomixian.vflow.ui.common.ThemeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val updateInfo: UpdateInfo? = null,
    val autoCheckUpdatesEnabled: Boolean = true,
    val currentLanguage: String = "",
    val dynamicColorEnabled: Boolean = false,
    val colorfulWorkflowCardsEnabled: Boolean = false,
    val liquidGlassNavBarEnabled: Boolean = false,
    val appScale: Float = AppearanceManager.DEFAULT_APP_SCALE,
    val progressNotificationEnabled: Boolean = true,
    val backgroundServiceNotificationEnabled: Boolean = true,
    val forceKeepAliveEnabled: Boolean = false,
    val autoEnableAccessibility: Boolean = false,
    val enableTypeFilter: Boolean = false,
    val hideFromRecents: Boolean = false,
    val allowShowOnLockScreen: Boolean = false,
    val defaultShellMode: String = "shizuku",
    val loggingEnabled: Boolean = false,
    val isShizukuActive: Boolean = false,
    val isRootAvailable: Boolean = false,
    val apiRunning: Boolean = false,
    val accessibilityDisguiseEnabled: Boolean = false
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh(context: Context, refreshUpdateInfo: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoCheckUpdatesEnabled = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES_ENABLED, true)
        _uiState.update {
            it.copy(
                updateInfo = if (autoCheckUpdatesEnabled) it.updateInfo else null,
                autoCheckUpdatesEnabled = autoCheckUpdatesEnabled,
                currentLanguage = LocaleManager.getLanguageDisplayName(
                    LocaleManager.getLanguage(context)
                ),
                dynamicColorEnabled = ThemeUtils.isDynamicColorEnabled(context),
                colorfulWorkflowCardsEnabled = ThemeUtils.isColorfulWorkflowCardsEnabled(context),
                liquidGlassNavBarEnabled = AppearanceManager.isLiquidGlassNavBarEnabled(context),
                appScale = AppearanceManager.getAppScale(context),
                progressNotificationEnabled = prefs.getBoolean(
                    KEY_PROGRESS_NOTIFICATION_ENABLED,
                    true
                ),
                backgroundServiceNotificationEnabled = prefs.getBoolean(
                    KEY_BACKGROUND_SERVICE_NOTIFICATION_ENABLED,
                    true
                ),
                forceKeepAliveEnabled = prefs.getBoolean(KEY_FORCE_KEEP_ALIVE_ENABLED, false),
                autoEnableAccessibility = prefs.getBoolean(KEY_AUTO_ENABLE_ACCESSIBILITY, false),
                enableTypeFilter = prefs.getBoolean(KEY_ENABLE_TYPE_FILTER, false),
                hideFromRecents = prefs.getBoolean(KEY_HIDE_FROM_RECENTS, false),
                allowShowOnLockScreen = OverlayUiPreferences.isShowOnLockScreenAllowed(context),
                defaultShellMode = prefs.getString(
                    KEY_DEFAULT_SHELL_MODE,
                    DEFAULT_SHELL_MODE
                ) ?: DEFAULT_SHELL_MODE,
                loggingEnabled = DebugLogger.isLoggingEnabled(),
                isShizukuActive = ShellManager.isShizukuActive(context),
                accessibilityDisguiseEnabled = prefs.getBoolean(KEY_ACCESSIBILITY_DISGUISE, false),
                isRootAvailable = ShellManager.isRootAvailable()
            )
        }

        if (!refreshUpdateInfo || !autoCheckUpdatesEnabled) {
            return
        }

        viewModelScope.launch {
            val updateInfo = runCatching {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName ?: "1.0.0"
                UpdateChecker.checkUpdate(currentVersion)
            }.getOrNull()?.getOrNull()

            _uiState.update { state ->
                state.copy(updateInfo = updateInfo?.takeIf(UpdateInfo::hasUpdate))
            }
        }
    }

    fun setApiRunning(serverState: ApiService.ServerState) {
        _uiState.update {
            it.copy(apiRunning = serverState == ApiService.ServerState.RUNNING)
        }
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_DYNAMIC_COLOR_ENABLED, enabled)
        _uiState.update { it.copy(dynamicColorEnabled = enabled) }
    }

    fun setColorfulWorkflowCardsEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(ThemeUtils.KEY_COLORFUL_WORKFLOW_CARDS_ENABLED, enabled)
        _uiState.update { it.copy(colorfulWorkflowCardsEnabled = enabled) }
    }

    fun setLiquidGlassNavBarEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(AppearanceManager.KEY_LIQUID_GLASS_NAV_BAR_ENABLED, enabled)
        _uiState.update { it.copy(liquidGlassNavBarEnabled = enabled) }
    }

    fun setAppScale(context: Context, scale: Float) = editPref(context) {
        val clampedScale = AppearanceManager.clampAppScale(scale)
        putFloat(AppearanceManager.KEY_APP_SCALE, clampedScale)
        _uiState.update { it.copy(appScale = clampedScale) }
    }

    fun setProgressNotificationEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_PROGRESS_NOTIFICATION_ENABLED, enabled)
        _uiState.update { it.copy(progressNotificationEnabled = enabled) }
    }

    fun setBackgroundServiceNotificationEnabled(
        context: Context,
        enabled: Boolean
    ) = editPref(context) {
        putBoolean(KEY_BACKGROUND_SERVICE_NOTIFICATION_ENABLED, enabled)
        _uiState.update { it.copy(backgroundServiceNotificationEnabled = enabled) }
    }

    fun setForceKeepAliveEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_FORCE_KEEP_ALIVE_ENABLED, enabled)
        _uiState.update { it.copy(forceKeepAliveEnabled = enabled) }
    }

    fun setAutoEnableAccessibility(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_AUTO_ENABLE_ACCESSIBILITY, enabled)
        _uiState.update { it.copy(autoEnableAccessibility = enabled) }
    }

    fun setEnableTypeFilter(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_ENABLE_TYPE_FILTER, enabled)
        _uiState.update { it.copy(enableTypeFilter = enabled) }
    }

    fun setHideFromRecents(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_HIDE_FROM_RECENTS, enabled)
        _uiState.update { it.copy(hideFromRecents = enabled) }
    }

    fun setAllowShowOnLockScreen(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(OverlayUiPreferences.KEY_ALLOW_SHOW_ON_LOCK_SCREEN, enabled)
        _uiState.update { it.copy(allowShowOnLockScreen = enabled) }
    }

    fun setDefaultShellMode(context: Context, mode: String) = editPref(context) {
        putString(KEY_DEFAULT_SHELL_MODE, mode)
        _uiState.update { it.copy(defaultShellMode = mode) }
    }

    fun setAutoCheckUpdatesEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_AUTO_CHECK_UPDATES_ENABLED, enabled)
        _uiState.update {
            it.copy(
                autoCheckUpdatesEnabled = enabled,
                updateInfo = if (enabled) it.updateInfo else null
            )
        }
        if (enabled) {
            refresh(context, refreshUpdateInfo = true)
        }
    }

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        DebugLogger.setLoggingEnabled(enabled, context)
        _uiState.update { it.copy(loggingEnabled = enabled) }
    }

    fun setAccessibilityDisguiseEnabled(context: Context, enabled: Boolean) = editPref(context) {
        putBoolean(KEY_ACCESSIBILITY_DISGUISE, enabled)
        _uiState.update { it.copy(accessibilityDisguiseEnabled = enabled) }
    }

    private inline fun editPref(
        context: Context,
        crossinline block: android.content.SharedPreferences.Editor.() -> Unit
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            block()
        }
    }

    companion object {
        const val PREFS_NAME = "vFlowPrefs"
        const val KEY_ACCESSIBILITY_DISGUISE = "accessibilityDisguiseEnabled"

        private const val DEFAULT_SHELL_MODE = "shizuku"
        private const val KEY_AUTO_CHECK_UPDATES_ENABLED = "autoCheckUpdatesEnabled"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamicColorEnabled"
        private const val KEY_PROGRESS_NOTIFICATION_ENABLED = "progressNotificationEnabled"
        private const val KEY_BACKGROUND_SERVICE_NOTIFICATION_ENABLED =
            "backgroundServiceNotificationEnabled"
        private const val KEY_FORCE_KEEP_ALIVE_ENABLED = "forceKeepAliveEnabled"
        private const val KEY_AUTO_ENABLE_ACCESSIBILITY = "autoEnableAccessibility"
        private const val KEY_ENABLE_TYPE_FILTER = "enableTypeFilter"
        private const val KEY_HIDE_FROM_RECENTS = "hideFromRecents"
        private const val KEY_DEFAULT_SHELL_MODE = "default_shell_mode"
    }
}
