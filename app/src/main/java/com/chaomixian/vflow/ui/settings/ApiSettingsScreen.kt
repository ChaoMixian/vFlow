package com.chaomixian.vflow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.api.ApiService
import kotlinx.coroutines.launch

/**
 * API设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    apiService: ApiService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverState by apiService.serverState.collectAsState()
    val serverConfig by apiService.serverConfig.collectAsState()

    var serverUrl by remember { mutableStateOf<String?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Token列表
    var tokens by remember { mutableStateOf<List<com.chaomixian.vflow.api.auth.TokenInfo>>(emptyList()) }

    LaunchedEffect(serverState) {
        if (serverState == ApiService.ServerState.RUNNING) {
            serverUrl = apiService.getServerUrl()
        } else {
            serverUrl = null
        }
    }

    LaunchedEffect(Unit) {
        tokens = apiService.getActiveTokens()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程API设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
            // 服务器状态卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "服务器状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (serverState) {
                                    ApiService.ServerState.RUNNING -> "运行中"
                                    ApiService.ServerState.STOPPED -> "已停止"
                                    ApiService.ServerState.ERROR -> "错误"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = when (serverState) {
                                    ApiService.ServerState.RUNNING -> MaterialTheme.colorScheme.primary
                                    ApiService.ServerState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    ApiService.ServerState.ERROR -> MaterialTheme.colorScheme.error
                                }
                            )

                            Switch(
                                checked = serverState == ApiService.ServerState.RUNNING,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        if (enabled) {
                                            apiService.startServer()
                                        } else {
                                            apiService.stopServer()
                                        }
                                    }
                                }
                            )
                        }

                        if (serverUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "服务器地址: $serverUrl",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 配置卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showConfigDialog = true }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "服务器配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "端口: ${serverConfig.port}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Token管理卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "访问令牌",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = { showTokenDialog = true }) {
                                Text("生成令牌")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (tokens.isEmpty()) {
                            Text(
                                text = "暂无活跃令牌",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            tokens.forEach { token ->
                                TokenItem(
                                    token = token,
                                    onRevoke = {
                                        scope.launch {
                                            apiService.revokeToken(token.token)
                                            tokens = apiService.getActiveTokens()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 使用说明
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "使用说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = """
                                1. 启用服务器开关
                                2. 访问服务器地址查看API文档
                                3. 生成令牌用于身份验证
                                4. 在请求头中添加: Authorization: Bearer <令牌>

                                注意: 请确保设备和Web编辑器在同一网络
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // 配置对话框
    if (showConfigDialog) {
        ApiConfigDialog(
            currentConfig = serverConfig,
            onDismiss = { showConfigDialog = false },
            onSave = { config ->
                apiService.updateConfig(config)
                showConfigDialog = false
            }
        )
    }

    // 生成令牌对话框
    if (showTokenDialog) {
        GenerateTokenDialog(
            onDismiss = { showTokenDialog = false },
            onGenerate = { deviceId, deviceName ->
                val token = apiService.generateToken(deviceId, deviceName)
                tokens = apiService.getActiveTokens()
                token
            }
        )
    }
}

@Composable
fun TokenItem(
    token: com.chaomixian.vflow.api.auth.TokenInfo,
    onRevoke: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = token.deviceName ?: "未知设备",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "设备ID: ${token.deviceId.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "令牌: ${token.token.take(32)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "过期时间: ${java.util.Date(token.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRevoke,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("撤销令牌")
                }
            }
        }
    }
}

@Composable
fun ApiConfigDialog(
    currentConfig: ApiService.ServerConfig,
    onDismiss: () -> Unit,
    onSave: (ApiService.ServerConfig) -> Unit
) {
    var port by remember { mutableStateOf(currentConfig.port.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器配置") },
        text = {
            Column {
                Text("端口:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("8080") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portNum = port.toIntOrNull() ?: 8080
                    onSave(currentConfig.copy(port = portNum.coerceIn(1024, 65535)))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun GenerateTokenDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, String?) -> String?
) {
    var deviceId by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var generatedToken by remember { mutableStateOf<String?>(null) }
    var showCopySuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (generatedToken == null) "生成令牌" else "令牌已生成") },
        text = {
            val token = generatedToken
            if (token == null) {
                Column {
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = { Text("设备ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("设备名称 (可选)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column {
                    Text(
                        text = "请保存此令牌，关闭对话框后将无法再次查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val token = generatedToken
            if (token == null) {
                Button(
                    onClick = {
                        if (deviceId.isNotBlank()) {
                            generatedToken = onGenerate(deviceId, deviceName.ifBlank { null })
                        }
                    },
                    enabled = deviceId.isNotBlank()
                ) {
                    Text("生成")
                }
            } else {
                Button(
                    onClick = {
                        // 复制到剪贴板
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("API Token", token)
                        clipboard.setPrimaryClip(clip)
                        showCopySuccess = true
                    }
                ) {
                    Text(if (showCopySuccess) "已复制!" else "复制")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (generatedToken == null) "取消" else "关闭")
            }
        }
    )
}
