// 文件：main/java/com/chaomixian/vflow/ui/settings/CoreManagementActivity.kt
package com.chaomixian.vflow.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.services.CoreManagementService
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * vFlowCore 管理 Activity
 * 用于查看 vFlowCore 状态、手动启动/杀死 Core 以及查看 Core 日志
 */
class CoreManagementActivity : ComponentActivity() {

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
            MaterialTheme {
                CoreManagementScreen(
                    onBackClick = { finish() },
                    onCheckStatus = { checkServerStatus() },
                    onStartServer = { startServer() },
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

            // 等待一段时间让 Core 启动
            delay(2000)

            // 检查启动结果
            VFlowCoreBridge.ping()
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
                    // 只显示最后5000个字符，避免内存问题
                    if (logs.length > 5000) {
                        "...\n" + logs.takeLast(5000)
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
    onStopServer: suspend () -> Boolean,
    onLoadLogs: suspend () -> String,
    autoStart: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isServerRunning by remember { mutableStateOf<Boolean?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("暂无日志...") }
    var statusDetail by remember { mutableStateOf("正在检查vFlowCore状态...") }

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
            DebugLogger.i("CoreManagementScreen", "自动启动 vFlowCore...")
            statusDetail = "正在自动启动 vFlowCore..."
            isChecking = true

            val success = onStartServer()
            isChecking = false
            isServerRunning = success
            statusDetail = if (success) {
                showToast("vFlowCore 自动启动成功")
                // 重新加载日志
                logs = onLoadLogs()
                "vFlowCore 正常运行中"
            } else {
                showToast("vFlowCore 自动启动失败")
                "vFlowCore 启动失败"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("vFlowCore 管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // vFlowCore 状态卡片
                StatusCard(
                    isRunning = isServerRunning,
                    isChecking = isChecking,
                    statusDetail = statusDetail
                )
            }

            item {
                // vFlowCore 控制卡片
                ControlCard(
                    isRunning = isServerRunning,
                    isChecking = isChecking,
                    onStartClick = {
                        if (isChecking) return@ControlCard
                        statusDetail = "正在启动 vFlowCore..."
                        isChecking = true
                        coroutineScope.launch {
                            val success = onStartServer()
                            isChecking = false
                            isServerRunning = success
                            statusDetail = if (success) {
                                showToast("vFlowCore 启动成功")
                                // 重新加载日志
                                logs = onLoadLogs()
                                "vFlowCore 正常运行中"
                            } else {
                                showToast("vFlowCore 启动失败，请查看日志")
                                "vFlowCore 启动失败"
                            }
                        }
                    },
                    onStopClick = {
                        if (isChecking) return@ControlCard
                        statusDetail = "正在停止 vFlowCore..."
                        isChecking = true
                        coroutineScope.launch {
                            val success = onStopServer()
                            isChecking = false
                            isServerRunning = !success
                            statusDetail = if (!success) {
                                showToast("vFlowCore 已停止")
                                "vFlowCore 未运行"
                            } else {
                                showToast("vFlowCore 仍在运行")
                                "vFlowCore 仍在运行"
                            }
                        }
                    },
                    onRefreshClick = {
                        if (isChecking) return@ControlCard
                        statusDetail = "正在刷新状态..."
                        isChecking = true
                        coroutineScope.launch {
                            val running = onCheckStatus()
                            isChecking = false
                            isServerRunning = running
                            statusDetail = if (running) {
                                "vFlowCore 正常运行中"
                            } else {
                                "vFlowCore 未运行"
                            }
                        }
                    }
                )
            }

            item {
                // vFlowCore 日志卡片
                LogsCard(
                    logs = logs,
                    onReloadClick = {
                        coroutineScope.launch {
                            logs = onLoadLogs()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean?,
    isChecking: Boolean,
    statusDetail: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "vFlowCore 状态",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "状态: ",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = when {
                        isChecking -> "检查中..."
                        isRunning == true -> "运行中"
                        isRunning == false -> "已停止"
                        else -> "检查中..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        isChecking -> MaterialTheme.colorScheme.onSurface
                        isRunning == true -> Color(0xFF4CAF50)
                        isRunning == false -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )

                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ControlCard(
    isRunning: Boolean?,
    isChecking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "vFlowCore 控制",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = !isChecking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("启动 Core")
                }

                Button(
                    onClick = onStopClick,
                    enabled = !isChecking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("杀死 Core")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRefreshClick,
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新状态")
            }
        }
    }
}

@Composable
private fun LogsCard(
    logs: String,
    onReloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "vFlowCore 日志",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onReloadClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新加载",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = logs,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
