package com.chaomixian.vflow.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.PermissionGuardianService
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.common.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 权限守护 Activity
 * 提供 "无障碍权限" 的自动授权和轮询守护功能。
 */
class PermissionGuardianActivity : ComponentActivity() {

    private val viewModel: GuardianViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GuardianTheme {
                GuardianScreen(
                    onBackClick = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus(this)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
private fun GuardianTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = ThemeUtils.getAppColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen(
    onBackClick: () -> Unit,
    viewModel: GuardianViewModel
) {
    val context = LocalContext.current

    val guardianEnabled by viewModel.guardianEnabled
    val accessibilityGranted by viewModel.accessibilityGranted
    val canUseShell by viewModel.canUseShell

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_guardian_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // 说明卡片
            DescriptionCard()

            Spacer(modifier = Modifier.height(16.dp))

            // 无障碍权限守护卡片
            AccessibilityGuardCard(
                granted = accessibilityGranted,
                guardEnabled = guardianEnabled,
                canUseShell = canUseShell,
                onToggleGuard = { enabled ->
                    viewModel.setGuardianEnabled(context, enabled)
                },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DescriptionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.permission_guardian_description_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_guardian_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun AccessibilityGuardCard(
    granted: Boolean,
    guardEnabled: Boolean,
    canUseShell: Boolean,
    onToggleGuard: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Accessible,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.permission_guardian_accessibility_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                StatusBadge(granted = granted)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 描述
            Text(
                text = stringResource(R.string.permission_guardian_accessibility_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 守护开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.permission_guardian_enable_guard),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = guardEnabled,
                    onCheckedChange = {
                        if (!canUseShell) {
                            // cannot enable
                        } else {
                            onToggleGuard(it)
                        }
                    },
                    enabled = canUseShell
                )
            }

            if (!canUseShell) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_guardian_shell_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 打开设置按钮
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_guardian_open_settings))
            }
        }
    }
}

@Composable
fun StatusBadge(granted: Boolean) {
    Surface(
        color = if (granted)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(
                if (granted) R.string.permission_guardian_status_granted
                else R.string.permission_guardian_status_denied
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (granted)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
        )
    }
}

/**
 * ViewModel 用于管理权限守护状态
 */
class GuardianViewModel : androidx.lifecycle.ViewModel() {
    companion object {
        private const val TAG = "GuardianViewModel"
        private const val PREFS_NAME = "vFlowPrefs"
    }

    private val _guardianEnabled = mutableStateOf(false)
    val guardianEnabled: State<Boolean> = _guardianEnabled

    private val _accessibilityGranted = mutableStateOf(false)
    val accessibilityGranted: State<Boolean> = _accessibilityGranted

    private val _canUseShell = mutableStateOf(false)
    val canUseShell: State<Boolean> = _canUseShell

    fun refreshStatus(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _guardianEnabled.value = prefs.getBoolean("accessibilityGuardEnabled", false)
        _accessibilityGranted.value = PermissionManager.isGranted(
            context,
            PermissionManager.ACCESSIBILITY
        )
        _canUseShell.value = ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()
    }

    fun setGuardianEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("accessibilityGuardEnabled", enabled).apply()
        _guardianEnabled.value = enabled

        if (enabled) {
            DebugLogger.i(TAG, "启动权限守护服务")
            PermissionGuardianService.start(context)
        } else {
            DebugLogger.i(TAG, "停止权限守护服务")
            PermissionGuardianService.stop(context)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
