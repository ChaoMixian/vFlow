package com.chaomixian.vflow.ui.recording

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.recording.UiRecording
import com.chaomixian.vflow.core.recording.UiRecordingStore
import com.chaomixian.vflow.services.AccessibilityServiceStatus
import com.chaomixian.vflow.services.UiRecordingService
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.VFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录制 UI 管理界面：查看、删除录制结果，并启动新的 UI 录制。
 */
class UiRecordingsActivity : BaseActivity() {

    private var recordings by mutableStateOf<List<UiRecording>>(emptyList())
    private var recordingSizes by mutableStateOf<Map<String, Long>>(emptyMap())
    private var loading by mutableStateOf(true)
    private var showInstructions by mutableStateOf(false)
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VFlowTheme {
                UiRecordingsScreen(
                    recordings = recordings,
                    recordingSizes = recordingSizes,
                    loading = loading,
                    showInstructions = showInstructions,
                    onBack = { finish() },
                    onRecordClick = { onRecordClick() },
                    onRename = { recording, newName -> renameRecording(recording, newName) },
                    onDelete = { deleteRecording(it) },
                    onDismissInstructions = { showInstructions = false },
                    onConfirmInstructions = {
                        showInstructions = false
                        startRecordingService()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadRecordings()
    }

    private fun reloadRecordings() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    UiRecordingStore.listRecordings().map { recording ->
                        recording to UiRecordingStore.sizeBytes(recording)
                    }
                }.getOrElse { emptyList() }
            }
            recordings = result.map { it.first }
            recordingSizes = result.associate { it.first.id to it.second }
            loading = false
        }
    }

    private fun deleteRecording(recording: UiRecording) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { UiRecordingStore.delete(recording) }.getOrDefault(false)
            }
            if (success) {
                toast("已删除录制「${recording.name}」")
            } else {
                toast("删除失败")
            }
            reloadRecordings()
        }
    }

    private fun renameRecording(recording: UiRecording, newName: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { UiRecordingStore.rename(recording, newName) }.getOrDefault(false)
            }
            toast(if (success) "已重命名录制" else "重命名失败")
            reloadRecordings()
        }
    }

    private fun onRecordClick() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                )
            }
            return
        }

        if (!AccessibilityServiceStatus.isEnabledInSettings(this) ||
            !AccessibilityServiceStatus.isRunning(this)
        ) {
            toast("请先开启并连接 vFlow 无障碍服务")
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            return
        }

        showInstructions = true
    }

    private fun startRecordingService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, UiRecordingService::class.java),
            )
            toast("UI 录制已启动，可切换到其他应用操作")
        }.onFailure {
            toast("启动录制失败：${it.message ?: "未知错误"}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiRecordingsScreen(
    recordings: List<UiRecording>,
    recordingSizes: Map<String, Long>,
    loading: Boolean,
    showInstructions: Boolean,
    onBack: () -> Unit,
    onRecordClick: () -> Unit,
    onRename: (UiRecording, String) -> Unit,
    onDelete: (UiRecording) -> Unit,
    onDismissInstructions: () -> Unit,
    onConfirmInstructions: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<UiRecording?>(null) }
    var pendingRename by remember { mutableStateOf<UiRecording?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("录制UI管理") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecordClick,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                    )
                },
                text = { Text("录制UI") },
            )
        },
    ) { innerPadding ->
        when {
            loading && recordings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            recordings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "还没有录制结果",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击右下角「录制UI」开始第一次录制",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = recordings) { recording ->
                        RecordingCard(
                            recording = recording,
                            sizeBytes = recordingSizes[recording.id] ?: 0L,
                            onRename = { pendingRename = recording },
                            onDelete = { pendingDelete = recording },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除录制") },
            text = { Text("确定删除「${target.name}」吗？截图和录制数据都会被删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(target)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    pendingRename?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("重命名录制") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim().ifBlank { target.name }
                        pendingRename = null
                        onRename(target, name)
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showInstructions) {
        AlertDialog(
            onDismissRequest = onDismissInstructions,
            title = { Text("UI 录制使用说明") },
            text = {
                Text(
                    "1. 点击悬浮窗上的「开始」按钮开始录制。\n" +
                        "2. 录制中可随时「暂停 / 继续」。\n" +
                        "3. 点击「结束」后为录制命名并保存。\n\n" +
                        "录制每 300ms 采样一次屏幕 UI 状态和截图。\n\n" +
                        "开始后可以离开本页面或切换到其他应用操作。"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmInstructions) {
                    Text("开始录制")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissInstructions) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RecordingCard(
    recording: UiRecording,
    sizeBytes: Long,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name.ifBlank { "未命名录制" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "创建：${formatDateTime(recording.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "时长 ${formatDuration(recording.durationMs)} · " +
                        "${recording.segments.size} 段 · ${formatSize(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "重命名",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
