package com.chaomixian.vflow.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import com.chaomixian.vflow.api.ApiService
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.ui.common.ThemeUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * API设置Activity
 */
class ApiSettingsActivity : ComponentActivity() {

    private lateinit var apiService: ApiService

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
    val colorScheme = ThemeUtils.getAppColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
