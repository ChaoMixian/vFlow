// 文件: app/src/main/java/com/chaomixian/vflow/ui/overlay/TouchRecordOverlay.kt

package com.chaomixian.vflow.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TouchEventRecord
import com.chaomixian.vflow.core.workflow.model.TouchRecordingData
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CompletableDeferred

/**
 * 触摸录制覆盖层。
 * 提供悬浮按钮启动录制，录制时可全屏捕获触摸。
 */
class TouchRecordOverlay(
    private val context: Context,
    private val showHint: Boolean = true
) {
    private val TAG = "TouchRecordOverlay"

    // 使用 applicationContext 获取 WindowManager，避免 Activity 泄漏
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // 悬浮按钮
    private var fabRoot: FrameLayout? = null
    private var fab: FloatingActionButton? = null

    // 触摸捕获层
    private var captureView: View? = null

    // 提示悬浮窗
    private var hintView: View? = null

    private val touchEvents = mutableListOf<TouchEventRecord>()
    private var recordingStartTime = 0L
    private var isRecording = false
    private var resultDeferred: CompletableDeferred<TouchRecordingData?>? = null

    suspend fun startRecording(): TouchRecordingData? {
        resultDeferred = CompletableDeferred()
        showFloatingButton()
        return resultDeferred?.await()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (fabRoot != null) return

        // 使用 appContext 创建容器
        fabRoot = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 使用原始 context（带 Material 主题）创建 FAB
        fab = FloatingActionButton(context).apply {
            setImageResource(R.drawable.rounded_arrow_upload_ready_24)
            setOnClickListener {
                // 隐藏按钮后开始录制
                fabRoot?.visibility = View.INVISIBLE
                startRecordingInternal()
            }
        }

        val fabParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        fabRoot?.addView(fab, fabParams)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 50
        }

        // 支持拖动
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        fabRoot?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                    }
                    layoutParams.x = initialX - dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(fabRoot, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(fabRoot, layoutParams)
            DebugLogger.d(TAG, "录制悬浮按钮已显示")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "添加悬浮按钮失败", e)
            resultDeferred?.complete(null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startRecordingInternal() {
        isRecording = true
        recordingStartTime = SystemClock.uptimeMillis()
        touchEvents.clear()

        // 添加全屏触摸捕获层
        showCaptureOverlay()

        // 显示提示悬浮窗
        if (showHint) {
            showHintOverlay()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCaptureOverlay() {
        captureView = object : View(appContext) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                touchEvents.add(TouchEventRecord(
                    action = event.action,
                    x = event.rawX,
                    y = event.rawY,
                    pointerId = event.getPointerId(event.actionIndex),
                    timestamp = SystemClock.uptimeMillis() - recordingStartTime
                ))
                updateHint()
                return true // 消费事件，防止传递到底层应用
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val layoutParams = WindowManager.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        )

        try {
            windowManager.addView(captureView, layoutParams)
            DebugLogger.d(TAG, "触摸捕获层已显示")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "添加触摸捕获层失败", e)
            resultDeferred?.complete(null)
        }
    }

    private fun showHintOverlay() {
        val inflatedView = View.inflate(context, R.layout.layout_touch_record_hint, null)
        hintView = inflatedView

        hintView?.findViewById<MaterialButton>(R.id.btn_stop)?.setOnClickListener {
            finishRecording()
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        try {
            windowManager.addView(hintView, layoutParams)
            DebugLogger.d(TAG, "录制提示层已显示")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "添加录制提示层失败", e)
        }
    }

    private fun updateHint() {
        val elapsedMs = touchEvents.lastOrNull()?.timestamp ?: 0L
        val elapsedSec = elapsedMs / 1000.0
        val statusText = "录制中... ${String.format("%.1f", elapsedSec)}s | ${touchEvents.size} 事件"
        hintView?.findViewById<android.widget.TextView>(R.id.tv_status)?.text = statusText
    }

    private fun finishRecording() {
        if (!isRecording && touchEvents.isEmpty()) {
            DebugLogger.d(TAG, "没有录制任何事件")
            resultDeferred?.complete(null)
            dismiss()
            return
        }

        isRecording = false

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        val duration = touchEvents.lastOrNull()?.timestamp ?: 0L

        val data = TouchRecordingData(
            screenW = metrics.widthPixels,
            screenH = metrics.heightPixels,
            duration = duration,
            events = touchEvents.toList()
        )

        DebugLogger.d(TAG, "录制完成: ${data.events.size} 个事件, 时长 ${data.duration}ms")

        resultDeferred?.complete(data)
        dismiss()
    }

    fun dismiss() {
        try {
            if (fabRoot != null) {
                windowManager.removeView(fabRoot)
                fabRoot = null
            }
            captureView?.let { windowManager.removeView(it) }
            hintView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "移除视图失败（可能已被移除）", e)
        } finally {
            fabRoot = null
            captureView = null
            hintView = null
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}
