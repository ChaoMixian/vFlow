package com.chaomixian.vflow.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.chaomixian.vflow.api.ApiService
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.VFlowTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * API设置Activity
 */
class ApiSettingsActivity : ComponentActivity() {

    private lateinit var apiService: ApiService

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取单例ApiService
        val workflowManager = WorkflowManager(applicationContext)
        apiService = ApiService.getInstance(applicationContext, workflowManager, null)

        setContent {
            ApiSettingsTheme {
                ApiSettingsScreen(
                    apiService = apiService,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ApiSettingsTheme(
    content: @Composable () -> Unit
) {
    VFlowTheme(content = content)
}
