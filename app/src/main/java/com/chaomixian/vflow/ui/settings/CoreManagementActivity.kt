// 文件：main/java/com/chaomixian/vflow/ui/settings/CoreManagementActivity.kt
package com.chaomixian.vflow.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.ThemeUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chaomixian.vflow.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.services.CoreManagementService
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * vFlowCore 管理 Activity
 * 用于查看 vFlowCore 状态、手动启动/杀死 Core 以及查看 Core 日志
 * 采用 Material You 设计风格
 */
class CoreManagementActivity : BaseActivity() {

    private val serverLogFile: File by lazy {
        File(StorageManager.logsDir, "server_process.log")
    }

    private var autoStartRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查是否需要自动启动
        autoStartRequested = intent.getBooleanExtra("auto_start", false)
        if (autoStartRequested) {
            DebugLogger.i("CoreManagementActivity", "收到自动启动请求")
        }

        setContent {
            MaterialTheme(
                colorScheme = ThemeUtils.getAppColorScheme()
            ) {
                CoreManagementScreen(
                    onBackClick = { finish() },
                    onCheckStatus = { checkServerStatus() },
                    onStartServer = { startServer() },
                    onStartServerWithMode = { mode -> startServerWithMode(mode) },
                    onStopServer = { stopServer() },
                    onLoadLogs = { loadServerLogs(this) },
                    autoStart = autoStartRequested
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Activity恢复时刷新状态（在 Composable 中处理）
    }

    /**
     * 检查 vFlowCore 状态
     */
    private suspend fun checkServerStatus(): Boolean {
        return withContext(Dispatchers.IO) {
            VFlowCoreBridge.ping()
        }
    }

    /**
     * 启动 vFlowCore
     */
    private suspend fun startServer(): Boolean {
        return withContext(Dispatchers.IO) {
            // 发送启动 Intent
            val intent = Intent(this@CoreManagementActivity, CoreManagementService::class.java).apply {
                action = CoreManagementService.ACTION_START_CORE
            }
            startService(intent)
            DebugLogger.i("CoreManagementActivity", "vFlow Core 启动 Intent 已发送，正在等待启动...")

            // 多次尝试 ping，最多等待 5 秒
            val maxRetries = 10
            val retryDelay = 500L

            repeat(maxRetries) { attempt ->
                delay(retryDelay)
                if (VFlowCoreBridge.ping()) {
                    DebugLogger.i("CoreManagementActivity", "vFlow Core 启动成功（尝试 ${attempt + 1}次）")
                    return@withContext true
                }
            }

            DebugLogger.w("CoreManagementActivity", "vFlow Core 未在 ${maxRetries * retryDelay}ms 内响应")
            false
        }
    }

    /**
     * 通过指定模式启动 vFlowCore
     * 使用 Service 来执行实际的启动逻辑
     */
    private suspend fun startServerWithMode(mode: ShellManager.ShellMode, forceRestart: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 发送启动 Intent 到 Service，并传递 ShellMode
                val intent = Intent(this@CoreManagementActivity, CoreManagementService::class.java).apply {
                    action = if (forceRestart) {
                        CoreManagementService.ACTION_RESTART_CORE
                    } else {
                        CoreManagementService.ACTION_START_CORE
                    }
                    putExtra(CoreManagementService.EXTRA_SHELL_MODE, mode.name)
                    putExtra(CoreManagementService.EXTRA_FORCE_RESTART, forceRestart)
                }
                startService(intent)
                DebugLogger.i("CoreManagementActivity", "vFlow Core 启动 Intent 已发送 (ShellMode: $mode), 正在等待启动...")

                // 多次尝试 ping，最多等待 5 秒
                val maxRetries = 10
                val retryDelay = 500L

                repeat(maxRetries) { attempt ->
                    delay(retryDelay)
                    if (VFlowCoreBridge.ping()) {
                        DebugLogger.i("CoreManagementActivity", "vFlow Core 启动成功（尝试 ${attempt + 1}次）")
                        return@withContext true
                    }
                }

                DebugLogger.w("CoreManagementActivity", "vFlow Core 未在 ${maxRetries * retryDelay}ms 内响应")
                false
            } catch (e: Exception) {
                DebugLogger.e("CoreManagementActivity", "启动过程发生异常", e)
                false
            }
        }
    }

    /**
     * 停止 vFlowCore
     */
    private suspend fun stopServer(): Boolean {
        return withContext(Dispatchers.IO) {
            // 发送停止 Intent
            val intent = Intent(this@CoreManagementActivity, CoreManagementService::class.java).apply {
                action = CoreManagementService.ACTION_STOP_CORE
            }
            startService(intent)

            // 等待停止操作完成
            delay(500)

            // 返回 false 表示 Core 已停止（不再是运行状态）
            // 不再重新检查，因为 CoreLauncher.stop() 已经处理了断开连接
            false
        }
    }

    /**
     * 加载 vFlowCore 日志
     */
    private suspend fun loadServerLogs(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                val logs = if (serverLogFile.exists()) {
                    serverLogFile.readText()
                } else {
                    context.getString(R.string.text_logs_not_available)
                }

                if (logs.isBlank()) {
                    context.getString(R.string.text_no_logs_yet)
                } else {
                    // 只显示最后8000个字符，避免内存问题
                    if (logs.length > 8000) {
                        "...\n" + logs.takeLast(8000)
                    } else {
                        logs
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("CoreManagementActivity", "读取日志失败", e)
                context.getString(R.string.text_log_load_failed, e.message ?: "")
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoreManagementScreen(
    onBackClick: () -> Unit,
    onCheckStatus: suspend () -> Boolean,
    onStartServer: suspend () -> Boolean,
    onStartServerWithMode: suspend (ShellManager.ShellMode) -> Boolean,
    onStopServer: suspend () -> Boolean,
    onLoadLogs: suspend () -> String,
    autoStart: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isServerRunning by remember { mutableStateOf<Boolean?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("") }
    var statusDetail by remember { mutableStateOf("") }
    var logsExpanded by remember { mutableStateOf(false) }

    // 保存的启动方式和自动启动设置
    var selectedLaunchMode by remember { mutableStateOf<ShellManager.ShellMode?>(null) }
    var autoStartEnabled by remember { mutableStateOf(false) }

    // 处理返回键
    BackHandler(enabled = true, onBack = onBackClick)

    // Toast 辅助函数
    fun showToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // 初始加载和自动启动
    LaunchedEffect(Unit) {
        // 初始化本地化状态
        statusDetail = context.getString(R.string.status_checking)
        logs = context.getString(R.string.text_no_logs_yet)

        // 加载保存的偏好设置
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("preferred_core_launch_mode", "shizuku")
        selectedLaunchMode = if (savedMode == "root") {
            ShellManager.ShellMode.ROOT
        } else {
            ShellManager.ShellMode.SHIZUKU
        }
        autoStartEnabled = prefs.getBoolean("core_auto_start_enabled", false)

        isServerRunning = onCheckStatus()
        logs = onLoadLogs()

        // 如果需要自动启动且 Core 未运行
        if (autoStart && isServerRunning == false) {
            DebugLogger.i("CoreManagementScreen", "自动启动 vFlow Core...")
            statusDetail = context.getString(R.string.status_starting_core, "Shizuku")
            isChecking = true

            val success = onStartServer()
            isChecking = false
            isServerRunning = success
            statusDetail = if (success) {
                showToast(context.getString(R.string.toast_core_start_success))
                // 重新加载日志
                logs = onLoadLogs()
                context.getString(R.string.status_core_running)
            } else {
                showToast(context.getString(R.string.toast_core_start_failed))
                context.getString(R.string.status_core_start_failed)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_core_management)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            StatusCard(
                isRunning = isServerRunning,
                isChecking = isChecking,
                statusDetail = statusDetail,
                privilegeMode = VFlowCoreBridge.privilegeMode
            )

            // 启动方式卡片组
            Text(
                text = stringResource(R.string.label_launch_method),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Shizuku 启动卡片
            LaunchMethodCard(
                title = stringResource(R.string.title_shizuku_launch),
                description = stringResource(R.string.desc_shizuku_launch),
                icon = Icons.Default.Terminal,
                iconTint = MaterialTheme.colorScheme.tertiary,
                isAvailable = ShellManager.isShizukuActive(context),
                isRunning = isChecking,
                isSelected = selectedLaunchMode == ShellManager.ShellMode.SHIZUKU,
                onClick = {
                    if (isChecking) return@LaunchMethodCard
                    if (!ShellManager.isShizukuActive(context)) {
                        showToast(context.getString(R.string.toast_shizuku_not_active))
                        return@LaunchMethodCard
                    }

                    // 保存偏好设置
                    val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                    prefs.edit { putString("preferred_core_launch_mode", "shizuku") }
                    selectedLaunchMode = ShellManager.ShellMode.SHIZUKU

                    statusDetail = context.getString(R.string.status_starting_shizuku)
                    isChecking = true
                    coroutineScope.launch {
                        val success = onStartServerWithMode(ShellManager.ShellMode.SHIZUKU)
                        isChecking = false
                        isServerRunning = success
                        statusDetail = if (success) {
                            showToast(context.getString(R.string.toast_core_start_success))
                            logs = onLoadLogs()
                            context.getString(R.string.status_core_running)
                        } else {
                            showToast(context.getString(R.string.toast_core_start_failed))
                            context.getString(R.string.status_core_start_failed)
                        }
                    }
                }
            )

            // Root 启动卡片
            LaunchMethodCard(
                title = stringResource(R.string.title_root_launch),
                description = stringResource(R.string.desc_root_launch),
                icon = Icons.Default.Security,
                iconTint = MaterialTheme.colorScheme.primary,
                isAvailable = ShellManager.isRootAvailable(),
                isRunning = isChecking,
                isSelected = selectedLaunchMode == ShellManager.ShellMode.ROOT,
                onClick = {
                    if (isChecking) return@LaunchMethodCard
                    if (!ShellManager.isRootAvailable()) {
                        showToast(context.getString(R.string.toast_root_not_available))
                        return@LaunchMethodCard
                    }

                    // 保存偏好设置
                    val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                    prefs.edit { putString("preferred_core_launch_mode", "root") }
                    selectedLaunchMode = ShellManager.ShellMode.ROOT

                    statusDetail = context.getString(R.string.status_starting_root)
                    isChecking = true
                    coroutineScope.launch {
                        val success = onStartServerWithMode(ShellManager.ShellMode.ROOT)
                        isChecking = false
                        isServerRunning = success
                        statusDetail = if (success) {
                            showToast(context.getString(R.string.toast_core_start_success))
                            logs = onLoadLogs()
                            context.getString(R.string.status_core_running)
                        } else {
                            showToast(context.getString(R.string.toast_core_start_failed))
                            context.getString(R.string.status_core_start_failed)
                        }
                    }
                }
            )

            // 电脑授权启动卡片
            AdbLaunchCard(
                context = context,
                isRunning = isChecking
            )

            // 控制按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 启动/重启 Core 按钮
                FilledTonalButton(
                    onClick = {
                        if (isChecking) return@FilledTonalButton

                        // 根据 Core 状态选择操作
                        if (isServerRunning == true) {
                            // Core 正在运行，执行重启
                            statusDetail = context.getString(R.string.status_restarting_core)
                            isChecking = true
                            coroutineScope.launch {
                                val success = VFlowCoreBridge.restart(context)
                                isChecking = false
                                isServerRunning = success
                                statusDetail = if (success) {
                                    showToast(context.getString(R.string.toast_core_restart_success))
                                    logs = onLoadLogs()
                                    context.getString(R.string.status_core_running)
                                } else {
                                    showToast(context.getString(R.string.toast_core_restart_failed))
                                    context.getString(R.string.status_core_restart_failed)
                                }
                            }
                        } else {
                            // Core 未运行，执行启动
                            val mode = selectedLaunchMode ?: ShellManager.ShellMode.AUTO
                            statusDetail = context.getString(R.string.status_starting_core, mode.name)
                            isChecking = true
                            coroutineScope.launch {
                                val success = onStartServerWithMode(mode)
                                isChecking = false
                                isServerRunning = success
                                statusDetail = if (success) {
                                    showToast(context.getString(R.string.toast_core_start_success))
                                    logs = onLoadLogs()
                                    context.getString(R.string.status_core_running)
                                } else {
                                    showToast(context.getString(R.string.toast_core_start_failed))
                                    context.getString(R.string.status_core_start_failed)
                                }
                            }
                        }
                    },
                    enabled = !isChecking && selectedLaunchMode != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isServerRunning == true) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isServerRunning == true) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                ) {
                    Icon(
                        if (isServerRunning == true) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isServerRunning == true) stringResource(R.string.button_restart_core) else stringResource(R.string.button_start_core))
                }

                // 停止 Core 按钮
                FilledTonalButton(
                    onClick = {
                        if (isChecking) return@FilledTonalButton
                        statusDetail = context.getString(R.string.status_stopping_core)
                        isChecking = true
                        coroutineScope.launch {
                            val stillRunning = onStopServer()
                            isChecking = false
                            isServerRunning = stillRunning
                            statusDetail = if (!stillRunning) {
                                showToast(context.getString(R.string.toast_core_stopped))
                                context.getString(R.string.status_core_not_running)
                            } else {
                                showToast(context.getString(R.string.toast_core_still_running))
                                context.getString(R.string.status_core_still_running)
                            }
                        }
                    },
                    enabled = !isChecking && isServerRunning == true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.button_stop_core))
                }
            }

            // 刷新状态按钮（单独一行，不需要等待 Core 运行）
            FilledTonalButton(
                onClick = {
                    if (isChecking) return@FilledTonalButton
                    statusDetail = context.getString(R.string.status_refreshing)
                    isChecking = true
                    coroutineScope.launch {
                        val running = onCheckStatus()
                        isChecking = false
                        isServerRunning = running
                        statusDetail = if (running) {
                            context.getString(R.string.status_core_running)
                        } else {
                            context.getString(R.string.status_core_not_running)
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.button_refresh_status))
            }

            // 日志卡片
            LogsCard(
                logs = logs,
                expanded = logsExpanded,
                onExpandChange = { logsExpanded = it },
                onReloadClick = {
                    coroutineScope.launch {
                        logs = onLoadLogs()
                    }
                }
            )

            // vFlow Core 设置卡片
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.title_core_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.text_auto_start_core),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.desc_auto_start_core),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = autoStartEnabled,
                            onCheckedChange = {
                                autoStartEnabled = it
                                val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                                prefs.edit { putBoolean("core_auto_start_enabled", it) }
                                if (it && selectedLaunchMode == null) {
                                    showToast(context.getString(R.string.toast_set_preferred_mode_first))
                                }
                            }
                        )
                    }
}
            }

            // 底部间距
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean?,
    isChecking: Boolean,
    statusDetail: String,
    privilegeMode: VFlowCoreBridge.PrivilegeMode
) {
    val statusColor = when {
        isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
        isRunning == true -> MaterialTheme.colorScheme.primary
        isRunning == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when {
        isChecking -> stringResource(R.string.status_checking)
        isRunning == true -> stringResource(R.string.status_running)
        isRunning == false -> stringResource(R.string.status_stopped)
        else -> stringResource(R.string.status_checking)
    }

    val modeText = when (privilegeMode) {
        VFlowCoreBridge.PrivilegeMode.ROOT -> stringResource(R.string.mode_root)
        VFlowCoreBridge.PrivilegeMode.SHELL -> stringResource(R.string.mode_shell)
        VFlowCoreBridge.PrivilegeMode.NONE -> stringResource(R.string.status_not_connected)
    }

    val modeColor = when (privilegeMode) {
        VFlowCoreBridge.PrivilegeMode.ROOT -> MaterialTheme.colorScheme.tertiary
        VFlowCoreBridge.PrivilegeMode.SHELL -> MaterialTheme.colorScheme.primary
        VFlowCoreBridge.PrivilegeMode.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isRunning == true) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_core_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(0.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (isRunning == true) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    modeText,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (privilegeMode) {
                                        VFlowCoreBridge.PrivilegeMode.ROOT -> Icons.Default.Security
                                        VFlowCoreBridge.PrivilegeMode.SHELL -> Icons.Default.Terminal
                                        else -> Icons.Default.Laptop
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = modeColor
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = modeColor.copy(alpha = 0.12f),
                                labelColor = modeColor,
                                leadingIconContentColor = modeColor
                            ),
                            border = null,
                            modifier = Modifier.heightIn(min = 24.dp)
                        )
                    }
                }
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LaunchMethodCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    isAvailable: Boolean,
    isRunning: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                enabled = isAvailable && !isRunning,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isAvailable) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isAvailable) {
                        iconTint
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAvailable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                if (!isAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.text_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 选中指示器
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.content_desc_close),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AdbLaunchCard(
    context: Context,
    isRunning: Boolean
) {
    var showCommand by remember { mutableStateOf(false) }
    var commandCopied by remember { mutableStateOf(false) }

    // 生成 ADB 命令（与 CoreManagementService 相同的逻辑）
    val dexPath = "/sdcard/vFlow/temp/vFlowCore.dex"
    val logPath = "/sdcard/vFlow/logs/server_process.log"
    val adbCommand = """adb shell "sh -c 'export CLASSPATH="$dexPath"; exec app_process /system/bin com.chaomixian.vflow.server.VFlowCore' > '$logPath' 2>&1 &""""

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Laptop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.title_adb_launch),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.desc_adb_launch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 展开/收起按钮
                IconButton(
                    onClick = { showCommand = !showCommand },
                    modifier = Modifier.size(40.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (showCommand) 180f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "rotation"
                    )
                    Icon(
                        if (showCommand) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showCommand) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }

            // 命令显示区域
            AnimatedVisibility(
                visible = showCommand,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(16.dp))

                    // 提示文本
                    Text(
                        text = stringResource(R.string.text_execute_on_pc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(8.dp))

                    // 命令框
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = adbCommand,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // 复制按钮
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ADB Command", adbCommand)
                                    clipboard.setPrimaryClip(clip)
                                    commandCopied = true
                                    android.widget.Toast.makeText(context, context.getString(R.string.toast_command_copied), android.widget.Toast.LENGTH_SHORT).show()

                                    // 2秒后重置复制状态
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                        delay(2000)
                                        commandCopied = false
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (commandCopied) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_reload),
                                    tint = if (commandCopied) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 使用说明
                    Text(
                        text = stringResource(R.string.text_adb_prerequisites),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsCard(
    logs: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onReloadClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.title_core_logs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.text_tap_to_expand),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onReloadClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_reload),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { onExpandChange(!expanded) },
                    modifier = Modifier.size(40.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "rotation"
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }

            // 日志内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = logs,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
