package com.chaomixian.vflow.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun VFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ThemeUtils.getAppColorScheme(),
        content = content
    )
}
