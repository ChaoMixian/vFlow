// 文件：main/java/com/chaomixian/vflow/ui/settings/CoreManagementActivity.kt
package com.chaomixian.vflow.ui.settings

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    onLoadLogs = { loadServerLogs() },
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
     * forceRestart: 是否强制重启现有进程
     */
    private suspend fun startServerWithMode(mode: ShellManager.ShellMode, forceRestart: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 只在明确要求重启时才停止现有进程
                if (forceRestart && VFlowCoreBridge.ping()) {
                    DebugLogger.d("CoreManagementActivity", "强制重启模式：检测到旧的 vFlow Core 正在运行，先停止...")
                    VFlowCoreBridge.shutdown()
                    delay(1500)
                }

                // 如果 Core 已经在运行且不是强制重启模式，直接返回成功
                if (!forceRestart && VFlowCoreBridge.ping()) {
                    DebugLogger.d("CoreManagementActivity", "vFlow Core 已在运行，无需启动")
                    return@withContext true
                }

                // 部署 Dex 到 Shell 可读的目录
                val dexFile = File(StorageManager.tempDir, "vFlowCore.dex")
                val parentDir = dexFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                // 即使文件存在也覆盖，确保版本更新
                applicationContext.assets.open("vFlowCore.dex").use { input ->
                    dexFile.outputStream().use { output -> input.copyTo(output) }
                }
                DebugLogger.d("CoreManagementActivity", "Dex 已部署到公共目录: ${dexFile.absolutePath}")

                // 准备日志文件
                val logFile = File(StorageManager.logsDir, "server_process.log")
                val logParentDir = logFile.parentFile
                if (logParentDir != null && !logParentDir.exists()) {
                    logParentDir.mkdirs()
                }

                // 构建启动命令
                val classpath = dexFile.absolutePath
                val logPath = logFile.absolutePath
                val coreClass = "com.chaomixian.vflow.server.VFlowCore"

                val fullCmd = "sh -c 'export CLASSPATH=\"$classpath\"; exec app_process /system/bin $coreClass' > \"$logPath\" 2>&1 &"

                // 执行命令
                DebugLogger.d("CoreManagementActivity", "执行启动命令: $fullCmd")
                val result = ShellManager.execShellCommand(applicationContext, fullCmd, mode)

                if (result.startsWith("Error")) {
                    DebugLogger.e("CoreManagementActivity", "vFlow Core 启动命令执行失败: $result")
                    false
                } else {
                    DebugLogger.i("CoreManagementActivity", "vFlow Core 启动命令已发送，正在等待启动...")

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

            // 等待一段时间让 Core 停止
            delay(1000)

            // 检查停止结果（返回是否仍在运行）
            VFlowCoreBridge.ping()
        }
    }

    /**
     * 加载 vFlowCore 日志
     */
    private suspend fun loadServerLogs(): String {
        return withContext(Dispatchers.IO) {
            try {
                val logs = if (serverLogFile.exists()) {
                    serverLogFile.readText()
                } else {
                    "日志文件不存在"
                }

                if (logs.isBlank()) {
                    "暂无日志..."
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
                "读取日志失败: ${e.message}"
            }
        }
    }
}

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
    var logs by remember { mutableStateOf("暂无日志...") }
    var statusDetail by remember { mutableStateOf("正在检查vFlow Core状态...") }
    var logsExpanded by remember { mutableStateOf(false) }

    // 处理返回键
    BackHandler(enabled = true, onBack = onBackClick)

    // Toast 辅助函数
    fun showToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // 初始加载和自动启动
    LaunchedEffect(Unit) {
        isServerRunning = onCheckStatus()
        logs = onLoadLogs()

        // 如果需要自动启动且 Core 未运行
        if (autoStart && isServerRunning == false) {
            DebugLogger.i("CoreManagementScreen", "自动启动 vFlow Core...")
            statusDetail = "正在自动启动 vFlow Core..."
            isChecking = true

            val success = onStartServer()
            isChecking = false
            isServerRunning = success
            statusDetail = if (success) {
                showToast("vFlow Core 自动启动成功")
                // 重新加载日志
                logs = onLoadLogs()
                "vFlow Core 正常运行中"
            } else {
                showToast("vFlow Core 自动启动失败")
                "vFlow Core 启动失败"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("vFlow Core 管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                text = "启动方式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Shizuku 启动卡片
            LaunchMethodCard(
                title = "通过 Shizuku 启动",
                description = "使用 Shizuku 权限启动 Core，无需 Root",
                icon = Icons.Default.Terminal,
                iconTint = MaterialTheme.colorScheme.tertiary,
                isAvailable = ShellManager.isShizukuActive(context),
                isRunning = isChecking,
                onClick = {
                    if (isChecking) return@LaunchMethodCard
                    if (!ShellManager.isShizukuActive(context)) {
                        showToast("Shizuku 未激活或未授权，无法启动")
                        return@LaunchMethodCard
                    }
                    statusDetail = "正在通过 Shizuku 启动 vFlow Core..."
                    isChecking = true
                    coroutineScope.launch {
                        val success = onStartServerWithMode(ShellManager.ShellMode.SHIZUKU)
                        isChecking = false
                        isServerRunning = success
                        statusDetail = if (success) {
                            showToast("vFlow Core 启动成功")
                            logs = onLoadLogs()
                            "vFlow Core 正常运行中"
                        } else {
                            showToast("vFlow Core 启动失败，请查看日志")
                            "vFlow Core 启动失败"
                        }
                    }
                }
            )

            // Root 启动卡片
            LaunchMethodCard(
                title = "通过 Root 启动",
                description = "使用 Root 权限启动 Core，需要设备已 Root",
                icon = Icons.Default.Security,
                iconTint = MaterialTheme.colorScheme.primary,
                isAvailable = ShellManager.isRootAvailable(),
                isRunning = isChecking,
                onClick = {
                    if (isChecking) return@LaunchMethodCard
                    if (!ShellManager.isRootAvailable()) {
                        showToast("设备未 Root 或 Root 权限未授予，无法启动")
                        return@LaunchMethodCard
                    }
                    statusDetail = "正在通过 Root 启动 vFlow Core..."
                    isChecking = true
                    coroutineScope.launch {
                        val success = onStartServerWithMode(ShellManager.ShellMode.ROOT)
                        isChecking = false
                        isServerRunning = success
                        statusDetail = if (success) {
                            showToast("vFlow Core 启动成功")
                            logs = onLoadLogs()
                            "vFlow Core 正常运行中"
                        } else {
                            showToast("vFlow Core 启动失败，请查看日志")
                            "vFlow Core 启动失败"
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
                // 重启 Core 按钮 (用于 DEX 更新后)
                FilledTonalButton(
                    onClick = {
                        if (isChecking) return@FilledTonalButton
                        statusDetail = "正在重启 vFlowCore（加载新 DEX）..."
                        isChecking = true
                        coroutineScope.launch {
                            val success = VFlowCoreBridge.restart(context)
                            isChecking = false
                            isServerRunning = success
                            statusDetail = if (success) {
                                showToast("vFlow Core 重启成功")
                                logs = onLoadLogs()
                                "vFlow Core 正常运行中"
                            } else {
                                showToast("vFlow Core 重启失败")
                                "vFlow Core 重启失败"
                            }
                        }
                    },
                    enabled = !isChecking && isServerRunning == true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("重启 Core")
                }

                // 停止 Core 按钮
                FilledTonalButton(
                    onClick = {
                        if (isChecking) return@FilledTonalButton
                        statusDetail = "正在停止 vFlow Core..."
                        isChecking = true
                        coroutineScope.launch {
                            val stillRunning = onStopServer()
                            isChecking = false
                            isServerRunning = stillRunning
                            statusDetail = if (!stillRunning) {
                                showToast("vFlow Core 已停止")
                                "vFlow Core 未运行"
                            } else {
                                showToast("vFlow Core 仍在运行")
                                "vFlow Core 仍在运行"
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
                    Text("停止 Core")
                }
            }

            // 刷新状态按钮（单独一行，不需要等待 Core 运行）
            FilledTonalButton(
                onClick = {
                    if (isChecking) return@FilledTonalButton
                    statusDetail = "正在刷新状态..."
                    isChecking = true
                    coroutineScope.launch {
                        val running = onCheckStatus()
                        isChecking = false
                        isServerRunning = running
                        statusDetail = if (running) {
                            "vFlow Core 正常运行中"
                        } else {
                            "vFlow Core 未运行"
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
                Text("刷新状态")
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
        isChecking -> "检查中..."
        isRunning == true -> "运行中"
        isRunning == false -> "已停止"
        else -> "检查中..."
    }

    val modeText = when (privilegeMode) {
        VFlowCoreBridge.PrivilegeMode.ROOT -> "Root"
        VFlowCoreBridge.PrivilegeMode.SHELL -> "Shell"
        VFlowCoreBridge.PrivilegeMode.NONE -> "未连接"
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
                    text = "vFlow Core 状态",
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
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                        text = "当前不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                        text = "通过电脑授权启动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "使用 ADB 从电脑启动 Core",
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
                        contentDescription = if (showCommand) "收起" else "展开",
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
                        text = "在电脑上执行以下命令：",
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
                                    android.widget.Toast.makeText(context, "命令已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()

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
                                    contentDescription = "复制命令",
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
                        text = "• 确保 Android 设备已通过 USB 连接到电脑\n" +
                               "• 已启用 USB 调试模式\n" +
                               "• 电脑已安装 ADB 工具",
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
                        text = "vFlow Core 日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "点击展开查看",
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
                        contentDescription = "重新加载",
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
                        contentDescription = if (expanded) "收起" else "展开",
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
