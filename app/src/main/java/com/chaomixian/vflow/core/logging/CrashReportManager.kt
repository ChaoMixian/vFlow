package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

data class CrashReport(
    val timestamp: Long,
    val processName: String,
    val threadName: String,
    val exceptionType: String,
    val exceptionMessage: String?,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidVersion: String,
    val apiLevel: Int,
    val device: String,
    val fingerprint: String,
    val stackTrace: String,
    val recentLogs: List<String>,
    val shellLogs: String?
)

data class CrashReportRecord(
    val fileName: String,
    val report: CrashReport,
    val isPending: Boolean
)

object CrashReportManager {
    private const val TAG = "CrashReportManager"
    private const val CRASH_DIR_NAME = "crash_reports"
    private const val PENDING_CRASH_FILE = "pending_crash.json"
    private const val MAX_ARCHIVES = 5

    @Volatile
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var appContext: Context

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            appContext = context.applicationContext
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                persistCrashReportSafely(thread, throwable)
                previousHandler?.uncaughtException(thread, throwable) ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun getPendingCrashReport(): CrashReport? {
        val file = File(getCrashDir(), PENDING_CRASH_FILE)
        if (!file.exists()) return null
        return runCatching {
            parseReport(file.readText())
        }.onFailure {
            android.util.Log.e(TAG, "读取崩溃报告失败", it)
        }.getOrNull()
    }

    fun clearPendingCrashReport() {
        runCatching {
            val file = File(getCrashDir(), PENDING_CRASH_FILE)
            if (file.exists()) {
                file.delete()
            }
        }.onFailure {
            android.util.Log.e(TAG, "删除崩溃报告失败", it)
        }
    }

    fun getCrashReportHistory(): List<CrashReportRecord> {
        val pendingReport = getPendingCrashReport()
        val crashDir = getCrashDir()
        val archiveRecords = crashDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".json")
        }?.mapNotNull { file ->
            runCatching {
                val report = parseReport(file.readText())
                CrashReportRecord(
                    fileName = file.name,
                    report = report,
                    isPending = pendingReport != null && pendingReport == report
                )
            }.onFailure {
                android.util.Log.e(TAG, "读取历史崩溃报告失败: ${file.name}", it)
            }.getOrNull()
        }?.sortedByDescending { it.report.timestamp }?.toMutableList() ?: mutableListOf()

        if (pendingReport != null && archiveRecords.none { it.report == pendingReport }) {
            archiveRecords.add(
                0,
                CrashReportRecord(
                    fileName = PENDING_CRASH_FILE,
                    report = pendingReport,
                    isPending = true
                )
            )
        }

        return archiveRecords.sortedByDescending { it.report.timestamp }
    }

    fun deleteCrashReport(record: CrashReportRecord) {
        runCatching {
            if (record.fileName == PENDING_CRASH_FILE) {
                clearPendingCrashReport()
            } else {
                val file = File(getCrashDir(), record.fileName)
                if (file.exists()) {
                    file.delete()
                }
                val pendingReport = getPendingCrashReport()
                if (pendingReport != null && pendingReport == record.report) {
                    clearPendingCrashReport()
                }
            }
        }.onFailure {
            android.util.Log.e(TAG, "删除崩溃报告失败: ${record.fileName}", it)
        }
    }

    fun deleteAllCrashReports() {
        runCatching {
            val crashDir = getCrashDir()
            crashDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    file.delete()
                }
            }
        }.onFailure {
            android.util.Log.e(TAG, "清空崩溃报告失败", it)
        }
    }

    fun formatReport(report: CrashReport): String {
        val timeText = REPORT_DATE_FORMAT.format(Date(report.timestamp))
        val messageText = report.exceptionMessage ?: "N/A"
        val recentLogsText = if (report.recentLogs.isEmpty()) {
            "[No recent app logs]"
        } else {
            report.recentLogs.joinToString("\n")
        }
        val shellLogsText = report.shellLogs ?: "[No shell logs]"

        return buildString {
            appendLine("vFlow Crash Report")
            appendLine("Time: $timeText")
            appendLine("Process: ${report.processName}")
            appendLine("Thread: ${report.threadName}")
            appendLine("Exception: ${report.exceptionType}")
            appendLine("Message: $messageText")
            appendLine("App Version: ${report.appVersionName} (${report.appVersionCode})")
            appendLine("Android: ${report.androidVersion} (API ${report.apiLevel})")
            appendLine("Device: ${report.device}")
            appendLine("Fingerprint: ${report.fingerprint}")
            appendLine()
            appendLine("Stacktrace:")
            appendLine(report.stackTrace)
            appendLine()
            appendLine("Recent App Logs:")
            appendLine(recentLogsText)
            appendLine()
            appendLine("Recent Shell Logs:")
            appendLine(shellLogsText)
        }
    }

    private fun persistCrashReportSafely(thread: Thread, throwable: Throwable) {
        runCatching {
            val report = buildCrashReport(thread, throwable)
            writeReport(report)
        }.onFailure {
            android.util.Log.e(TAG, "写入崩溃报告失败", it)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): CrashReport {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return CrashReport(
            timestamp = System.currentTimeMillis(),
            processName = appContext.applicationInfo.processName ?: appContext.packageName,
            threadName = thread.name,
            exceptionType = throwable.javaClass.name,
            exceptionMessage = throwable.message,
            appVersionName = packageInfo.versionName ?: "N/A",
            appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            androidVersion = Build.VERSION.RELEASE ?: "N/A",
            apiLevel = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            fingerprint = Build.FINGERPRINT ?: "N/A",
            stackTrace = throwable.stackTraceToString(),
            recentLogs = DebugLogger.getRecentLogsForCrash(),
            shellLogs = DebugLogger.getShellLogsForCrash()
        )
    }

    private fun writeReport(report: CrashReport) {
        val crashDir = getCrashDir()
        val json = serializeReport(report)
        val archiveName = "crash-${FILE_DATE_FORMAT.format(Date(report.timestamp))}.json"
        writeAtomically(File(crashDir, archiveName), json)
        writeAtomically(File(crashDir, PENDING_CRASH_FILE), json)
        pruneArchives(crashDir)
    }

    private fun writeAtomically(file: File, contents: String) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(contents.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (file.exists()) {
            file.delete()
        }
        check(tempFile.renameTo(file)) { "Failed to move crash report into place: ${file.absolutePath}" }
    }

    private fun pruneArchives(crashDir: File) {
        val archives = crashDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: return

        archives.drop(MAX_ARCHIVES).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun getCrashDir(): File {
        val dir = File(appContext.filesDir, CRASH_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun serializeReport(report: CrashReport): String {
        val json = JSONObject()
            .put("timestamp", report.timestamp)
            .put("processName", report.processName)
            .put("threadName", report.threadName)
            .put("exceptionType", report.exceptionType)
            .put("exceptionMessage", report.exceptionMessage)
            .put("appVersionName", report.appVersionName)
            .put("appVersionCode", report.appVersionCode)
            .put("androidVersion", report.androidVersion)
            .put("apiLevel", report.apiLevel)
            .put("device", report.device)
            .put("fingerprint", report.fingerprint)
            .put("stackTrace", report.stackTrace)
            .put("shellLogs", report.shellLogs)

        val logsArray = JSONArray()
        report.recentLogs.forEach { logsArray.put(it) }
        json.put("recentLogs", logsArray)
        return json.toString()
    }

    private fun parseReport(contents: String): CrashReport {
        val json = JSONObject(contents)
        val logs = mutableListOf<String>()
        val recentLogsArray = json.optJSONArray("recentLogs") ?: JSONArray()
        for (index in 0 until recentLogsArray.length()) {
            logs += recentLogsArray.optString(index)
        }
        return CrashReport(
            timestamp = json.optLong("timestamp"),
            processName = json.optString("processName"),
            threadName = json.optString("threadName"),
            exceptionType = json.optString("exceptionType"),
            exceptionMessage = json.optString("exceptionMessage").ifBlank { null },
            appVersionName = json.optString("appVersionName"),
            appVersionCode = json.optLong("appVersionCode"),
            androidVersion = json.optString("androidVersion"),
            apiLevel = json.optInt("apiLevel"),
            device = json.optString("device"),
            fingerprint = json.optString("fingerprint"),
            stackTrace = json.optString("stackTrace"),
            recentLogs = logs,
            shellLogs = json.optString("shellLogs").ifBlank { null }
        )
    }

    private val REPORT_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val FILE_DATE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}
