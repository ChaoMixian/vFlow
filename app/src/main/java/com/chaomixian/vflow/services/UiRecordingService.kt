package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.recording.RecordingNodeUtils
import com.chaomixian.vflow.core.recording.UiRecording
import com.chaomixian.vflow.core.recording.UiRecordingEvent
import com.chaomixian.vflow.core.recording.UiRecordingEventBus
import com.chaomixian.vflow.core.recording.UiRecordingSegment
import com.chaomixian.vflow.core.recording.UiRecordingStore
import com.chaomixian.vflow.ui.recording.UiRecordingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * UI 录制前台服务。
 *
 * - 每 300ms 采样一次当前屏幕 UI 结构；
 * - UI 结构变化时开启新的时间段并异步保存截图；
 * - 结构未变化时仅延长当前时间段；
 * - 结束录制后弹出命名对话框并保存到 /sdcard/vFlow/recordings。
 */
class UiRecordingService : Service() {

    companion object {
        private const val TAG = "UiRecordingService"
        private const val CHANNEL_ID = "ui_recording_channel"
        private const val NOTIFICATION_ID = 3101
        private const val SAMPLE_INTERVAL_MS = 300L
        private const val MAX_NODES = 800
        private const val MAX_SCREENSHOT_WIDTH = 540
        private const val JPEG_QUALITY = 60
        private const val SCREENSHOT_TIMEOUT_MS = 3_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager

    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var startPauseButton: Button? = null
    private var endButton: Button? = null
    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val recordingId = UiRecordingStore.createId()
    private val createdAt = System.currentTimeMillis()

    private val lock = Any()
    private val segments = mutableListOf<UiRecordingSegment>()
    private val events = mutableListOf<UiRecordingEvent>()

    private var screenWidth = 0
    private var screenHeight = 0

    @Volatile private var recording = false
    @Volatile private var paused = true
    @Volatile private var saved = false
    @Volatile private var stopped = false

    @Volatile private var startedAtElapsed = 0L
    @Volatile private var pausedAccumulatedMs = 0L
    @Volatile private var pauseStartedElapsed = 0L
    @Volatile private var pausedAtMs = 0L

    @Volatile private var lastSignature: String? = null
    @Volatile private var samplingJob: Job? = null
    private var timerJob: Job? = null

    private val screenshotInFlight = AtomicBoolean(false)

    private val eventListener: (UiRecordingEvent) -> Unit = { event ->
        if (recording && !paused) {
            val relative = (event.timestampMs - startedAtElapsed - pausedAccumulatedMs).coerceAtLeast(0L)
            synchronized(lock) { events.add(event.copy(timestampMs = relative)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        val overlayShown = runCatching { showOverlay() }.getOrDefault(false)
        if (!overlayShown) {
            toast("无法显示录制悬浮窗，请检查悬浮窗权限")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务已在 onCreate 启动，这里只确保悬浮窗存在。
        if (overlayView == null) {
            val overlayShown = runCatching { showOverlay() }.getOrDefault(false)
            if (!overlayShown) {
                toast("无法显示录制悬浮窗，请检查悬浮窗权限")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // 悬浮窗
    // ---------------------------------------------------------------------

    private fun showOverlay(): Boolean {
        if (overlayView != null) return true
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            stopSelf()
            return false
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xF0202124.toInt())
                setStroke(dp(1), 0x40FFFFFF)
            }
            elevation = dp(6).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "UI 录制"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }
        val timer = TextView(this).apply {
            text = "00:00"
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 10f
            setPadding(dp(8), 0, 0, 0)
        }
        timerTextView = timer
        header.addView(title)
        header.addView(timer)
        panel.addView(header)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val startPause = Button(this).apply {
            text = if (recording && paused) "继续" else if (recording) "暂停" else "开始"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2E7D32.toInt())
            }
            setOnClickListener {
                when {
                    !recording -> startRecording()
                    paused -> resumeRecording()
                    else -> pauseRecording()
                }
            }
        }
        val end = Button(this).apply {
            text = "结束"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFFC62828.toInt())
            }
            setOnClickListener { endRecording() }
        }
        startPauseButton = startPause
        endButton = end
        buttonRow.addView(startPause)
        buttonRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })
        buttonRow.addView(end)
        panel.addView(buttonRow)
        panelView = panel

        val params = createOverlayParams().apply {
            x = (screenWidth - dp(190)).coerceAtLeast(0)
            y = (screenHeight / 3).coerceAtLeast(dp(80))
        }
        panelParams = params
        attachDragToAll(panel, params)
        windowManager.addView(panel, params)
        overlayView = panel
        panel.post {
            params.x = (screenWidth - panel.width).coerceAtLeast(0)
            params.y = params.y.coerceIn(0, (screenHeight - panel.height).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(panel, params) }
        }
        return true
    }

    private fun attachDragToAll(view: View, params: WindowManager.LayoutParams) {
        attachDrag(view, params)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                attachDragToAll(view.getChildAt(index), params)
            }
        }
    }

    private fun attachDrag(handle: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        handle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (view.isClickable) view.performClick()
                    } else {
                        snapOverlayToEdge(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapOverlayToEdge(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapOverlayToEdge(view: View, params: WindowManager.LayoutParams) {
        val width = overlayView?.width?.takeIf { it > 0 } ?: view.width.takeIf { it > 0 } ?: 0
        val height = overlayView?.height?.takeIf { it > 0 } ?: view.height.takeIf { it > 0 } ?: 0
        params.x = if (params.x + width / 2 < screenWidth / 2) 0 else (screenWidth - width).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun createOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        panelView = null
        panelParams = null
        timerTextView = null
        startPauseButton = null
        endButton = null
    }

    private fun startRecording() {
        if (recording) return
        recording = true
        paused = false
        saved = false
        stopped = false
        startedAtElapsed = SystemClock.elapsedRealtime()
        pausedAccumulatedMs = 0L
        pauseStartedElapsed = 0L
        pausedAtMs = 0L
        lastSignature = null
        synchronized(lock) {
            segments.clear()
            events.clear()
        }
        UiRecordingEventBus.listener = eventListener
        startPauseButton?.text = "暂停"
        startTimerUpdates()
        startSampling()
        toast("开始录制")
    }

    private fun pauseRecording() {
        if (!recording || paused) return
        val now = nowMs()
        pausedAtMs = now
        paused = true
        pauseStartedElapsed = SystemClock.elapsedRealtime()
        samplingJob?.cancel()
        samplingJob = null
        closeCurrentSegment(now)
        startPauseButton?.text = "继续"
    }

    private fun resumeRecording() {
        if (!recording || !paused) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (pauseStartedElapsed > 0L) {
            pausedAccumulatedMs += (nowElapsed - pauseStartedElapsed).coerceAtLeast(0L)
        }
        pauseStartedElapsed = 0L
        pausedAtMs = 0L
        paused = false
        // 恢复后强制开启新的时间段，避免把暂停前后的界面合并
        lastSignature = null
        startPauseButton?.text = "暂停"
        startSampling()
    }

    private fun endRecording() {
        if (!recording) {
            stopSelf()
            return
        }
        val endMs = nowMs()
        pausedAtMs = endMs
        paused = true
        recording = false
        samplingJob?.cancel()
        samplingJob = null
        timerJob?.cancel()
        timerJob = null
        UiRecordingEventBus.listener = null
        closeCurrentSegment(endMs)
        removeOverlay()

        val hasSegments = synchronized(lock) { segments.isNotEmpty() }
        if (!hasSegments) {
            toast("没有可保存的录制内容")
            deleteRecordingDir()
            stopSelf()
            return
        }
        showNamingDialog(endMs)
    }

    private fun nowMs(): Long {
        if (startedAtElapsed == 0L) return 0L
        if (paused) return pausedAtMs
        return (SystemClock.elapsedRealtime() - startedAtElapsed - pausedAccumulatedMs).coerceAtLeast(0L)
    }

    private fun closeCurrentSegment(endMs: Long) {
        synchronized(lock) {
            val last = segments.lastOrNull() ?: return
            segments[segments.size - 1] = last.copy(endMs = max(last.endMs, endMs))
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            while (isActive && recording && !paused) {
                runCatching { sampleOnce() }
                    .onFailure { DebugLogger.w(TAG, "采样失败: ${it.message}") }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun sampleOnce() {
        val service = ServiceStateBus.getAccessibilityService() ?: return
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return
        val nodes = RecordingNodeUtils.collectNodes(root, MAX_NODES)
        if (nodes.isEmpty()) return

        val packageName = ServiceStateBus.lastWindowPackageName
            ?: nodes.firstOrNull()?.packageName.orEmpty()
        val activityName = ServiceStateBus.lastActivityClassName
            ?: ServiceStateBus.lastWindowClassName.orEmpty()
        val signature = RecordingNodeUtils.snapshotSignature(nodes)
        val now = nowMs()

        var newSegmentIndex = -1
        synchronized(lock) {
            val last = segments.lastOrNull()
            if (last == null || signature != lastSignature) {
                if (last != null) {
                    segments[segments.size - 1] = last.copy(endMs = max(last.endMs, now))
                }
                segments.add(
                    UiRecordingSegment(
                        startMs = now,
                        endMs = now,
                        packageName = packageName,
                        activityName = activityName,
                        nodes = nodes,
                        screenshot = null,
                    )
                )
                newSegmentIndex = segments.size - 1
            } else {
                segments[segments.size - 1] = last.copy(endMs = max(last.endMs, now))
            }
        }
        lastSignature = signature
        if (newSegmentIndex >= 0) {
            captureScreenshotAsync(newSegmentIndex)
        }
    }

    private fun startTimerUpdates() {
        timerJob?.cancel()
        timerJob = mainScope.launch {
            while (isActive) {
                timerTextView?.text = formatDuration(nowMs())
                delay(200)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 截图
    // ---------------------------------------------------------------------

    private fun captureScreenshotAsync(segmentIndex: Int) {
        if (!screenshotInFlight.compareAndSet(false, true)) return
        serviceScope.launch {
            try {
                val bitmap = captureAccessibilityBitmap() ?: captureShellBitmap(segmentIndex)
                if (bitmap != null) {
                    if (stopped) {
                        runCatching { bitmap.recycle() }
                        return@launch
                    }
                    val file = File(UiRecordingStore.recordingDir(recordingId), "seg_$segmentIndex.jpg")
                    if (writeJpeg(bitmap, file)) {
                        val relativePath = "$recordingId/seg_$segmentIndex.jpg"
                        synchronized(lock) {
                            val current = segments.getOrNull(segmentIndex)
                            if (current != null) {
                                segments[segmentIndex] = current.copy(screenshot = relativePath)
                            }
                        }
                    }
                    runCatching { bitmap.recycle() }
                }
            } catch (throwable: Throwable) {
                DebugLogger.w(TAG, "截图失败: ${throwable.message}")
            } finally {
                screenshotInFlight.set(false)
            }
        }
    }

    private suspend fun captureAccessibilityBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = ServiceStateBus.getAccessibilityService() ?: return null

        return withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val delivered = AtomicBoolean(false)
                val callback = object : AndroidAccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AndroidAccessibilityService.ScreenshotResult) {
                        if (!delivered.compareAndSet(false, true)) {
                            runCatching { result.hardwareBuffer.close() }
                            return
                        }
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        } finally {
                            runCatching { result.hardwareBuffer.close() }
                        }
                        if (continuation.isActive) continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (delivered.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
                val started = runCatching {
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
                }.isSuccess
                if (!started && delivered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private suspend fun captureShellBitmap(segmentIndex: Int): Bitmap? {
        val dir = UiRecordingStore.recordingDir(recordingId)
        val rawFile = File(dir, "seg_${segmentIndex}_raw.png")
        return try {
            val result = ShellManager.execShellCommand(
                this,
                "screencap -p \"${rawFile.absolutePath}\"",
            )
            if (result.startsWith("Error") || !rawFile.isFile) {
                null
            } else {
                BitmapFactory.decodeFile(rawFile.absolutePath)
            }
        } catch (throwable: Throwable) {
            DebugLogger.w(TAG, "Shell 截图失败: ${throwable.message}")
            null
        } finally {
            runCatching { rawFile.delete() }
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File): Boolean {
        return runCatching {
            file.parentFile?.mkdirs()
            val targetWidth = minOf(bitmap.width, MAX_SCREENSHOT_WIDTH)
            val scale = targetWidth.toFloat() / bitmap.width.coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    targetWidth,
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            try {
                FileOutputStream(file).use { output ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            true
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------------
    // 保存
    // ---------------------------------------------------------------------

    private fun showNamingDialog(durationMs: Long) {
        val input = EditText(this).apply {
            setText(UiRecordingStore.defaultName())
            setSelection(text.length)
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFB0B0B0.toInt())
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("保存 UI 录制")
            .setMessage("为本次录制命名（留空使用默认名称）")
            .setView(container)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .create()

        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setBackgroundDrawable(ColorDrawable(0xFF202124.toInt()))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        dialog.setOnShowListener {
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<TextView>(titleId)?.setTextColor(Color.WHITE)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(0xFFE0E0E0.toInt())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: UiRecordingStore.defaultName()
                runCatching { dialog.dismiss() }
                saveRecording(name, durationMs)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                cancelRecording()
            }
        }

        runCatching { dialog.show() }.onFailure { throwable ->
            DebugLogger.w(TAG, "无法显示命名对话框: ${throwable.message}")
            saveRecording(UiRecordingStore.defaultName(), durationMs)
        }
    }

    private fun saveRecording(name: String, durationMs: Long) {
        val recordingData = runCatching {
            val segmentSnapshot = synchronized(lock) { segments.toList() }
            val eventSnapshot = synchronized(lock) { events.sortedBy { it.timestampMs } }
            stopped = true
            UiRecording(
                id = recordingId,
                name = name,
                createdAt = createdAt,
                durationMs = durationMs,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                segments = segmentSnapshot,
                events = eventSnapshot,
            )
        }.getOrElse { throwable ->
            DebugLogger.e(TAG, "构建录制数据失败: ${throwable.message}", throwable)
            toast("保存失败: ${throwable.message ?: "未知错误"}")
            stopSelf()
            return
        }

        mainScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { UiRecordingStore.save(recordingData) }
            }
            result.onSuccess {
                saved = true
                toast("已保存到录制UI管理")
            }.onFailure { throwable ->
                DebugLogger.e(TAG, "保存录制失败: ${throwable.message}", throwable)
                toast("保存失败: ${throwable.message ?: "未知错误"}")
                deleteRecordingDir()
            }
            stopSelf()
        }
    }

    private fun cancelRecording() {
        stopped = true
        deleteRecordingDir()
        toast("已取消录制")
        stopSelf()
    }

    private fun deleteRecordingDir() {
        runCatching { File(UiRecordingStore.recordingsDir, recordingId).deleteRecursively() }
    }

    // ---------------------------------------------------------------------
    // 通知与清理
    // ---------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "UI 录制",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "UI 录制运行状态"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("UI 录制运行中")
        .setContentText("通过悬浮窗开始、暂停或结束录制")
        .setSmallIcon(R.drawable.ic_workflows)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, UiRecordingsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

    private fun cleanup() {
        recording = false
        paused = true
        stopped = true
        UiRecordingEventBus.listener = null
        samplingJob?.cancel()
        samplingJob = null
        timerJob?.cancel()
        timerJob = null
        removeOverlay()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        serviceScope.cancel()
        mainScope.cancel()
        if (!saved) {
            deleteRecordingDir()
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        return String.format(
            Locale.US,
            "%02d:%02d",
            totalSeconds / 60,
            totalSeconds % 60,
        )
    }

    private fun toast(message: String) {
        runCatching {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
