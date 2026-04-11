package com.chaomixian.vflow.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceStatus {

    fun getServiceId(context: Context): String {
        return ComponentName(context, AccessibilityService::class.java).flattenToString()
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
}
