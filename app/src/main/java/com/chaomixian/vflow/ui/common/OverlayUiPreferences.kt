package com.chaomixian.vflow.ui.common

import android.content.Context

object OverlayUiPreferences {
    const val PREFS_NAME = "vFlowPrefs"
    const val KEY_ALLOW_SHOW_ON_LOCK_SCREEN = "allowShowOnLockScreen"

    fun isShowOnLockScreenAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_SHOW_ON_LOCK_SCREEN, false)
    }
}
