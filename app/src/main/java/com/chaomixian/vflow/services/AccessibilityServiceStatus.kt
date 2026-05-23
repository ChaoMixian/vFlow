package com.chaomixian.vflow.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.chaomixian.vflow.ui.viewmodel.SettingsViewModel
import com.google.android.accessibility.selecttospeak.SelectToSpeakService

object AccessibilityServiceStatus {

    fun getOriginalServiceId(context: Context): String {
        return ComponentName(context, AccessibilityService::class.java).flattenToString()
    }

    fun getDisguisedServiceId(context: Context): String {
        return ComponentName(context, SelectToSpeakService::class.java).flattenToString()
    }

    fun getServiceId(context: Context): String {
        val prefs = context.getSharedPreferences(SettingsViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val disguised = prefs.getBoolean(SettingsViewModel.KEY_ACCESSIBILITY_DISGUISE, false)
        return if (disguised) getDisguisedServiceId(context) else getOriginalServiceId(context)
    }

    fun isEnabledInSettings(context: Context): Boolean {
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return containsServiceId(enabledServicesSetting, getServiceId(context))
    }

    fun isRunning(context: Context): Boolean {
        return ServiceStateBus.isAccessibilityServiceRunning()
    }

    internal fun containsServiceId(enabledServicesSetting: String?, expectedServiceId: String): Boolean {
        if (enabledServicesSetting.isNullOrBlank()) {
            return false
        }

        return enabledServicesSetting
            .split(':')
            .any { it.equals(expectedServiceId, ignoreCase = true) }
    }

    internal fun replaceServiceId(
        enabledServicesSetting: String?,
        fromServiceId: String,
        toServiceId: String
    ): String {
        val entries = enabledServicesSetting
            .orEmpty()
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot {
                it.equals(fromServiceId, ignoreCase = true) ||
                    it.equals(toServiceId, ignoreCase = true)
            }
            .toMutableList()

        entries += toServiceId
        return entries.joinToString(":")
    }
}
