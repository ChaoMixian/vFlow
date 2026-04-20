package com.chaomixian.vflow.ui.settings

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.VFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import com.chaomixian.vflow.R

/**
 * 按键事件数据类
 */
data class KeyEventData(
    val timestamp: String,
    val devicePath: String,
    val keyCode: Int,  // 数字按键码
    val action: String,
    val rawString: String
)

/**
 * 按键测试 Activity - Compose 版本
 * 用于查找物理按键的设备路径和按键代码
 */
class KeyTesterActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
    }

    private var listenerJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // 用于从 BroadcastReceiver 更新 UI 的回调
    private var onEventCallback: ((KeyEventData) -> Unit)? = null

    // 接收来自 C++ 二进制文件的广播
    private val keyEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            DebugLogger.d(TAG, "KeyTesterActivity 收到广播: action=${intent?.action}")
            if (intent?.action == ACTION_KEY_TEST_EVENT) {
                val gestureType = intent.getStringExtra("gesture_type") ?: return
                val keyCode = intent.getIntExtra("key_code", -1)
                val devicePath = intent.getStringExtra("device_path")
                    ?: getString(R.string.api_settings_unknown_device)
                if (keyCode == -1) return

                DebugLogger.d(TAG, "KeyTesterActivity 按键事件: gesture=$gestureType, key_code=$keyCode, device=$devicePath")

                val event = KeyEventData(
                    timestamp = timeFormat.format(Date()),
                    devicePath = devicePath,
                    keyCode = keyCode,
                    action = gestureType,
                    rawString = "DEVICE:$devicePath KEY_CODE:$keyCode ACTION:$gestureType"
                )

                DebugLogger.d(TAG, "KeyTesterActivity 添加事件: ${event.keyCode}")
                onEventCallback?.invoke(event)
            }
        }
    }

    companion object {
        const val ACTION_KEY_TEST_EVENT = "com.chaomixian.vflow.KEY_TEST_EVENT"
        const val TAG = "KeyTesterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            var isListening by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf(getString(R.string.key_tester_status_ready)) }
            val keyEvents = remember { mutableStateListOf<KeyEventData>() }

            // 设置事件回调
            DisposableEffect(Unit) {
                onEventCallback = { event ->
                    keyEvents.add(0, event)
                    // 限制列表长度，避免内存问题
                    if (keyEvents.size > 100) {
                        keyEvents.removeLast()
                    }
                }
                onDispose {
                    onEventCallback = null
                }
            }

            // 注册广播接收器
            DisposableEffect(Unit) {
                val filter = IntentFilter(ACTION_KEY_TEST_EVENT)
                ContextCompat.registerReceiver(
                    context,
                    keyEventReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )

                onDispose {
                    try {
                        context.unregisterReceiver(keyEventReceiver)
                    } catch (e: Exception) {
                        // 忽略未注册异常
                    }
                }
            }

            // 监听生命周期
            DisposableEffect(Unit) {
                onDispose {
                    stopKeyListening()
                }
            }

            VFlowTheme {
                Scaffold(
                    topBar = {
                        KeyTesterTopBar(
                            onNavigateBack = { finish() },
                            isListening = isListening,
                            onStartStop = {
                                if (isListening) {
                                    stopKeyListening()
                                    isListening = false
                                    statusMessage = getString(R.string.key_tester_status_stopped)
                                } else {
                                    startKeyListening(
                                        onStatusChange = { statusMessage = it }
                                    )
                                    isListening = true
                                }
                            },
                            onClear = { keyEvents.clear() }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // 状态卡片
                        StatusCard(
                            isListening = isListening,
                            statusMessage = statusMessage,
                            eventCount = keyEvents.size
                        )

                        // 按键事件列表
                        if (keyEvents.isEmpty()) {
                            EmptyState(
                                isListening = isListening,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            KeyEventList(
                                events = keyEvents,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保 Activity 销毁时停止监听并清理资源
        onEventCallback = null
        stopKeyListening()
    }

    private fun startKeyListening(
        onStatusChange: (String) -> Unit
    ) {
        if (!ShellManager.isShizukuActive(this) && !ShellManager.isRootAvailable()) {
            onStatusChange(getString(R.string.key_tester_shell_not_available))
            return
        }

        onStatusChange(getString(R.string.key_tester_initializing))

        listenerJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 0. 清理可能残留的旧进程和文件
                ShellManager.execShellCommand(this@KeyTesterActivity, "killall key_event_tester")
                ShellManager.execShellCommand(this@KeyTesterActivity, "rm -f /data/local/tmp/key_event_tester")

                onStatusChange(getString(R.string.key_tester_deploying))

                // 1. 部署 key_event_trigger_handler 到 /data/local/tmp
                val deviceAbi = getDeviceAbi()
                val assetPath = "key_event_trigger_handler/$deviceAbi/key_event_trigger_handler"
                val tempFile = File(StorageManager.tempDir, "key_event_tester")

                try {
                    assets.open(assetPath).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.setReadable(true, false)
                    DebugLogger.d(TAG, "Binary copied to temp: ${tempFile.absolutePath}")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "Failed to copy binary", e)
                    onStatusChange(getString(R.string.key_tester_deploy_failed, e.message ?: ""))
                    return@launch
                }

                // 2. 通过 shell 复制到 /data/local/tmp，并在 shell 侧校验可执行状态
                val deployResult = ShellManager.deployExecutableViaShell(
                    context = this@KeyTesterActivity,
                    stagedFile = tempFile,
                    targetPath = "/data/local/tmp/key_event_tester"
                )
                if (!deployResult.success) {
                    DebugLogger.e(TAG, "部署 key_event_tester 失败: ${deployResult.output}")
                    onStatusChange(getString(R.string.key_tester_deploy_tmp_failed, deployResult.output))
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onStatusChange(getString(R.string.key_tester_listening_hint))
                }

                // 3. 启动 C++ 二进制（测试模式）
                val command = "${deployResult.targetFile.absolutePath} --test --package ${packageName}"
                DebugLogger.d(TAG, "Starting key event tester: $command")

                val result = ShellManager.execShellCommand(this@KeyTesterActivity, command)
                DebugLogger.d(TAG, "Key tester execution result: $result")

            } catch (e: Exception) {
                DebugLogger.e(TAG, "监听出错: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onStatusChange(getString(R.string.key_tester_error, e.message ?: ""))
                }
            }
        }
    }

    private fun stopKeyListening() {
        listenerJob?.cancel()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DebugLogger.d(TAG, "Stopping key event tester...")
                ShellManager.execShellCommand(this@KeyTesterActivity, "killall key_event_tester")
                ShellManager.execShellCommand(this@KeyTesterActivity, "rm -f /data/local/tmp/key_event_tester")
                DebugLogger.d(TAG, "Key event tester stopped and cleaned up")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "停止出错", e)
            }
        }
    }

    private fun getDeviceAbi(): String {
        val primaryAbi = android.os.Build.SUPPORTED_ABIS[0]
        return when {
            primaryAbi.startsWith("arm64") -> "arm64-v8a"
            primaryAbi.startsWith("armeabi-v7a") -> "armeabi-v7a"
            primaryAbi.startsWith("x86_64") -> "x86_64"
            primaryAbi.startsWith("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyTesterTopBar(
    onNavigateBack: () -> Unit,
    isListening: Boolean,
    onStartStop: () -> Unit,
    onClear: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    stringResource(R.string.key_tester_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.key_tester_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.key_tester_back)
                )
            }
        },
        actions = {
            // 清除按钮
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.key_tester_clear),
                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 开始/停止按钮
            FilledTonalIconButton(
                onClick = onStartStop,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isListening) {
                        stringResource(R.string.key_tester_stop)
                    } else {
                        stringResource(R.string.key_tester_start)
                    },
                    tint = if (isListening) Color(0xFFFF5252) else Color(0xFF4CAF50)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun StatusCard(
    isListening: Boolean,
    statusMessage: String,
    eventCount: Int
) {
    val backgroundColor = if (isListening) {
        Color(0xFF2D4A3E)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val statusColor = if (isListening) {
        Color(0xFF69DB7C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isListening) {
            BorderStroke(1.dp, Color(0xFF69DB7C).copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isListening) Color(0xFF69DB7C) else Color(0xFF9E9E9E),
                            shape = RoundedCornerShape(6.dp)
                        )
                )

                Column {
                    Text(
                        text = if (isListening) {
                            stringResource(R.string.key_tester_status_listening)
                        } else {
                            stringResource(R.string.key_tester_status_stopped)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 事件计数
            if (eventCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "$eventCount",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )

            Text(
                text = if (isListening) {
                    stringResource(R.string.key_tester_press_any_key)
                } else {
                    stringResource(R.string.key_tester_click_to_start)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isListening) {
                Text(
                    text = stringResource(R.string.key_tester_info_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun KeyEventList(
    events: List<KeyEventData>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 当有新事件时，自动滚动到顶部
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = events,
            key = { it.timestamp + it.keyCode }
        ) { event ->
            KeyEventCard(event = event)
        }
    }
}

@Composable
fun KeyEventCard(event: KeyEventData) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                // 复制到剪贴板
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(context.getString(R.string.key_tester_key_info), event.rawString)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    context.getString(R.string.key_tester_copied, event.keyCode.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部行：时间戳和按键码
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = getActionIcon(event.action),
                        contentDescription = event.action,
                        tint = getActionColor(event.action),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = event.keyCode.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = event.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 设备路径
            InfoRow(
                icon = Icons.Default.Storage,
                label = stringResource(R.string.key_tester_device_path),
                value = event.devicePath,
                color = Color(0xFF8D68F5)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 按键码
            InfoRow(
                icon = Icons.Default.Code,
                label = stringResource(R.string.key_tester_key_code),
                value = event.keyCode.toString(),
                color = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作类型
            InfoRow(
                icon = Icons.Default.TouchApp,
                label = stringResource(R.string.key_tester_action),
                value = event.action,
                color = getActionColor(event.action)
            )

            // 复制提示
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.key_tester_click_to_copy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun getActionIcon(action: String): ImageVector {
    return when (action.uppercase()) {
        "DOWN", "PRESS" -> Icons.Default.KeyboardArrowDown
        "UP", "RELEASE" -> Icons.Default.KeyboardArrowUp
        else -> Icons.Default.MoreVert
    }
}

@Composable
fun getActionColor(action: String): Color {
    return when (action.uppercase()) {
        "DOWN", "PRESS" -> Color(0xFF4CAF50)
        "UP", "RELEASE" -> Color(0xFFFF5252)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
