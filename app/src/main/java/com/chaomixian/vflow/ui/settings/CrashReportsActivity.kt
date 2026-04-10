package com.chaomixian.vflow.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.CrashReportManager
import com.chaomixian.vflow.core.logging.CrashReportRecord
import com.chaomixian.vflow.ui.common.ThemeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportsActivity : ComponentActivity() {

    private var pendingExportText: String? = null
    private val exportCrashReportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            try {
                val reportText = pendingExportText ?: return@registerForActivityResult
                val targetUri = uri ?: return@registerForActivityResult
                val outputStream = contentResolver.openOutputStream(targetUri)
                    ?: throw IllegalStateException("Failed to open output stream")
                outputStream.use {
                    it.write(reportText.toByteArray())
                }
                toast(R.string.settings_toast_logs_exported)
            } catch (e: Exception) {
                toast(getString(R.string.settings_toast_export_failed, e.message))
            } finally {
                pendingExportText = null
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val context = LocaleManager.applyLanguage(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrashReportsTheme {
                CrashReportsScreen(
                    onBack = { finish() },
                    onShare = ::shareCrashReport,
                    onExport = ::exportCrashReport,
                    onCopy = ::copyCrashReport,
                    onTriggerCrash = ::triggerTestCrash
                )
            }
        }
    }

    private fun exportCrashReport(record: CrashReportRecord, reportText: String) {
        pendingExportText = reportText
        val timestamp = exportDateFormat.format(Date(record.report.timestamp))
        exportCrashReportLauncher.launch("vflow-crash-$timestamp.txt")
    }

    private fun shareCrashReport(reportText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_share_subject))
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_report_share_title)))
    }

    private fun copyCrashReport(reportText: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crash Report", reportText)
        clipboard.setPrimaryClip(clip)
        toast(R.string.toast_logs_copied)
    }

    private fun triggerTestCrash() {
        Handler(Looper.getMainLooper()).post {
            throw RuntimeException(getString(R.string.crash_reports_test_exception_message))
        }
    }

    private val exportDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}

@Composable
private fun CrashReportsTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = ThemeUtils.getAppColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportsScreen(
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onExport: (CrashReportRecord, String) -> Unit,
    onCopy: (String) -> Unit,
    onTriggerCrash: () -> Unit
) {
    var reports by remember { mutableStateOf(CrashReportManager.getCrashReportHistory()) }
    var selectedRecord by remember { mutableStateOf<CrashReportRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<CrashReportRecord?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }

    fun refreshReports() {
        reports = CrashReportManager.getCrashReportHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (reports.isNotEmpty()) {
                        TextButton(onClick = { showClearAllConfirm = true }) {
                            Text(stringResource(R.string.crash_reports_clear_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CrashReportsList(
                reports = reports,
                onOpenRecord = { selectedRecord = it },
                onTriggerCrash = { showCrashConfirm = true }
            )
        }
    }

    if (selectedRecord != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedRecord = null }
        ) {
            CrashReportDetail(
                record = selectedRecord!!,
                onShare = onShare,
                onExport = onExport,
                onCopy = onCopy,
                onDelete = { deleteTarget = it }
            )
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.crash_reports_delete_confirm_title)) },
            text = { Text(stringResource(R.string.crash_reports_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget ?: return@TextButton
                        CrashReportManager.deleteCrashReport(target)
                        if (selectedRecord?.fileName == target.fileName) {
                            selectedRecord = null
                        }
                        refreshReports()
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.crash_reports_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.crash_reports_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashReportManager.deleteAllCrashReports()
                        selectedRecord = null
                        refreshReports()
                        showClearAllConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.crash_reports_test_confirm_title)) },
            text = { Text(stringResource(R.string.crash_reports_test_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCrashConfirm = false
                        onTriggerCrash()
                    }
                ) {
                    Text(stringResource(R.string.crash_reports_test_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun CrashReportsList(
    reports: List<CrashReportRecord>,
    onOpenRecord: (CrashReportRecord) -> Unit,
    onTriggerCrash: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.crash_reports_subtitle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (reports.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.crash_reports_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.crash_reports_empty_message),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(reports, key = { it.fileName }) { record ->
                CrashReportCard(
                    record = record,
                    onClick = { onOpenRecord(record) }
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.crash_reports_test_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.crash_reports_test_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onTriggerCrash) {
                        Text(stringResource(R.string.crash_reports_test_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportCard(
    record: CrashReportRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = crashCardDateFormat.format(Date(record.report.timestamp)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (record.isPending) {
                    Text(
                        text = stringResource(R.string.crash_reports_pending_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${stringResource(R.string.crash_reports_label_thread)}: ${record.report.threadName}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.crash_reports_label_exception)}: ${record.report.exceptionType}",
                style = MaterialTheme.typography.bodyMedium
            )
            record.report.exceptionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CrashReportDetail(
    record: CrashReportRecord,
    onShare: (String) -> Unit,
    onExport: (CrashReportRecord, String) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (CrashReportRecord) -> Unit
) {
    val reportText = remember(record.fileName) { CrashReportManager.formatReport(record.report) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (record.isPending) {
            Text(
                text = stringResource(R.string.crash_reports_pending_detail_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SelectionContainer {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = reportText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onExport(record, reportText) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.common_export))
            }
            OutlinedButton(
                onClick = { onCopy(reportText) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.common_copy))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onDelete(record) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.common_delete))
            }
            Button(
                onClick = { onShare(reportText) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.common_share))
            }
        }
    }
}

private val crashCardDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
