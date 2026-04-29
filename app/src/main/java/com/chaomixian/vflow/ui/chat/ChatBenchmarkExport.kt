package com.chaomixian.vflow.ui.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ChatBenchmarkExportPayload(
    val exportVersion: Int = 1,
    val exportedAtMillis: Long,
    val appPackageName: String,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val run: ChatBenchmarkRun,
)

internal object ChatBenchmarkExportManager {
    private val exportJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }
    private val exportDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun buildShareIntent(
        context: Context,
        run: ChatBenchmarkRun,
    ): Intent {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val payload = buildExportPayload(
            run = run,
            packageName = context.packageName,
            versionName = packageInfo.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
        )
        val exportsDir = File(context.cacheDir, "benchmark_exports").apply { mkdirs() }
        val file = File(exportsDir, buildExportFileName(run))
        file.writeText(encodeExportPayload(payload))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "vFlow benchmark log ${run.id}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
        return Intent.createChooser(sendIntent, "Export benchmark log")
    }

    internal fun buildExportPayload(
        run: ChatBenchmarkRun,
        packageName: String,
        versionName: String?,
        versionCode: Long?,
    ): ChatBenchmarkExportPayload {
        return ChatBenchmarkExportPayload(
            exportedAtMillis = System.currentTimeMillis(),
            appPackageName = packageName,
            appVersionName = versionName,
            appVersionCode = versionCode,
            run = run,
        )
    }

    internal fun encodeExportPayload(
        payload: ChatBenchmarkExportPayload,
    ): String {
        return exportJson.encodeToString(payload)
    }

    private fun buildExportFileName(run: ChatBenchmarkRun): String {
        val timestamp = exportDateFormat.format(Date(run.startedAtMillis))
        val preset = run.presetName.ifBlank { run.modelName }
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "benchmark" }
        return "vflow-benchmark-$preset-$timestamp.json"
    }
}
