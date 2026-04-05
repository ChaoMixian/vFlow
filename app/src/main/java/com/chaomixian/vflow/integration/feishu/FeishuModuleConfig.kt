package com.chaomixian.vflow.integration.feishu

import android.content.Context
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity

object FeishuModuleConfig {
    fun getToken(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ModuleConfigActivity.KEY_FEISHU_ACCESS_TOKEN, "")
            ?.trim()
            .orEmpty()
    }
}
