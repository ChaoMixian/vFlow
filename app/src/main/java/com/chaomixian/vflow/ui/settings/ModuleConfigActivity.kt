// 文件: main/java/com/chaomixian/vflow/ui/settings/ModuleConfigActivity.kt
// 描述: 模块配置 Activity，用于配置各个模块的设置参数

package com.chaomixian.vflow.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.ThemeUtils

class ModuleConfigActivity : BaseActivity() {

    companion object {
        const val PREFS_NAME = "module_config_prefs"
        const val KEY_BACKTAP_SENSITIVITY = "backtap_sensitivity"

        // 灵敏度范围：0-10，对应灵敏度值从 0.0（最灵敏）到 0.75（最难触发）
        const val MIN_SENSITIVITY_VALUE = 0.0f
        const val MAX_SENSITIVITY_VALUE = 0.75f

        fun getSensitivityDisplayValue(value: Float): String {
            return when {
                value <= 0.01f -> "非常灵敏"
                value <= 0.02f -> "很灵敏"
                value <= 0.03f -> "灵敏"
                value <= 0.04f -> "一般"
                value <= 0.05f -> "较慢"
                value <= 0.1f -> "慢"
                value <= 0.25f -> "较难"
                value <= 0.4f -> "很难"
                value <= 0.53f -> "非常难"
                else -> "极难"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = ThemeUtils.getAppColorScheme()
            ) {
                ModuleConfigScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)

    // 读取灵敏度值 (0.0 - 0.75)，并转换为滑块值 (0 - 10)
    var sensitivityValue by remember {
        mutableFloatStateOf(prefs.getFloat(ModuleConfigActivity.KEY_BACKTAP_SENSITIVITY, 0.05f)
            .coerceIn(ModuleConfigActivity.MIN_SENSITIVITY_VALUE, ModuleConfigActivity.MAX_SENSITIVITY_VALUE))
    }

    // 滑块值 (0-10) 转换为实际灵敏度值
    val sliderPosition = ((sensitivityValue - ModuleConfigActivity.MIN_SENSITIVITY_VALUE) /
            (ModuleConfigActivity.MAX_SENSITIVITY_VALUE - ModuleConfigActivity.MIN_SENSITIVITY_VALUE) * 10).toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 轻敲背面触发器配置
            ModuleConfigSection(title = stringResource(R.string.module_config_section_backtap)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.module_config_backtap_sensitivity),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 灵敏度描述
                    Text(
                        text = ModuleConfigActivity.getSensitivityDisplayValue(sensitivityValue) +
                                String.format(" (%.2f)", sensitivityValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 滑块
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newPosition ->
                            // 将滑块值 (0-10) 转换为灵敏度值 (0.0-0.75)
                            sensitivityValue = ModuleConfigActivity.MIN_SENSITIVITY_VALUE +
                                    (newPosition / 10f) * (ModuleConfigActivity.MAX_SENSITIVITY_VALUE - ModuleConfigActivity.MIN_SENSITIVITY_VALUE)
                        },
                        onValueChangeFinished = {
                            // 保存到 SharedPreferences
                            prefs.edit {
                                putFloat(ModuleConfigActivity.KEY_BACKTAP_SENSITIVITY, sensitivityValue)
                            }
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 提示文字
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "灵敏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "难触发",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 可以在这里添加更多模块的配置
        }
    }
}

@Composable
fun ModuleConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                content()
            }
        }
    }
}
