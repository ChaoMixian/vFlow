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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 触摸录制覆盖层。
 * 提供全屏触摸捕获和录制提示界面。
 */
class TouchRecordOverlay(
    private val context: Context,
    private val showHint: Boolean = true
) {
    private val TAG = "TouchRecordOverlay"

    // 使用 applicationContext 获取 WindowManager，避免 Activity 泄漏
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var captureView: View? = null
    private var hintView: View? = null

    private val touchEvents = mutableListOf<TouchEventRecord>()
    private var recordingStartTime = 0L
    private var isRecording = false
    private var resultDeferred: CompletableDeferred<TouchRecordingData?>? = null

    suspend fun startRecording(): TouchRecordingData? {
        resultDeferred = CompletableDeferred()
        showCaptureOverlay()
        if (showHint) showHintOverlay()
        return resultDeferred?.await()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCaptureOverlay() {
        captureView = object : View(appContext) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (!isRecording && event.action == MotionEvent.ACTION_DOWN) {
                    isRecording = true
                    recordingStartTime = SystemClock.uptimeMillis()
                    touchEvents.clear()
                    updateHint()
                }

                if (isRecording) {
                    touchEvents.add(TouchEventRecord(
                        action = event.action,
                        x = event.rawX,
                        y = event.rawY,
                        pointerId = event.getPointerId(event.actionIndex),
                        timestamp = SystemClock.uptimeMillis() - recordingStartTime
                    ))
                    updateHint()
                }
                return true  // 消费事件，防止传递到底层应用
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
        // 使用原始 context（带 Material 主题）来布局，避免 MaterialButton 主题问题
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
            // 如果提示层添加失败，不应该影响录制功能，继续执行
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
            captureView?.let { windowManager.removeView(it) }
            hintView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "移除视图失败（可能已被移除）", e)
        } finally {
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
