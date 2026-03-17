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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.R
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
                title = { Text(stringResource(R.string.api_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.api_settings_back))
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
                            text = stringResource(R.string.api_settings_server_status),
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
                                    ApiService.ServerState.RUNNING -> stringResource(R.string.api_settings_status_running)
                                    ApiService.ServerState.STOPPED -> stringResource(R.string.api_settings_status_stopped)
                                    ApiService.ServerState.ERROR -> stringResource(R.string.api_settings_status_error)
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
                            val url = serverUrl
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.api_settings_server_url, url ?: ""),
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
                            text = stringResource(R.string.api_settings_server_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.api_settings_port, serverConfig.port),
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
                                text = stringResource(R.string.api_settings_access_tokens),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = { showTokenDialog = true }) {
                                Text(stringResource(R.string.api_settings_generate_token))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (tokens.isEmpty()) {
                            Text(
                                text = stringResource(R.string.api_settings_no_tokens),
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
                            text = stringResource(R.string.api_settings_instructions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.api_settings_instructions_text),
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
                        text = token.deviceName ?: stringResource(R.string.api_settings_unknown_device),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.api_settings_device_id, token.deviceId.take(8)),
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
                    text = stringResource(R.string.api_settings_token, token.token.take(32)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.api_settings_expires_at, java.util.Date(token.expiresAt)),
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
                    Text(stringResource(R.string.api_settings_revoke_token))
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
        title = { Text(stringResource(R.string.api_settings_server_config)) },
        text = {
            Column {
                Text(stringResource(R.string.api_settings_port_label))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.api_settings_port_hint)) }
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
                Text(stringResource(R.string.api_settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.api_settings_cancel))
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
        title = { Text(if (generatedToken == null) stringResource(R.string.api_settings_generate_token) else stringResource(R.string.api_settings_token_generated)) },
        text = {
            val token = generatedToken
            if (token == null) {
                Column {
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = { Text(stringResource(R.string.api_settings_device_id_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text(stringResource(R.string.api_settings_device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.api_settings_token_warning),
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
                    Text(stringResource(R.string.api_settings_generate))
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
                    Text(if (showCopySuccess) stringResource(R.string.api_settings_copied) else stringResource(R.string.api_settings_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (generatedToken == null) stringResource(R.string.api_settings_cancel) else stringResource(R.string.api_settings_close))
            }
        }
    )
}
