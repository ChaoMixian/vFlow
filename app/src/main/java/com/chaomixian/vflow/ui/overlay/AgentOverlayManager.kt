// 文件: main/java/com/chaomixian/vflow/ui/overlay/AgentOverlayManager.kt
package com.chaomixian.vflow.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 管理 Agent 执行时的视觉反馈（彩虹边框 + 状态悬浮窗）。
 */
class AgentOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var borderView: RainbowBorderView? = null
    private var statusCard: MaterialCardView? = null
    private var statusText: TextView? = null
    private var actionText: TextView? = null

    // 状态更新动画
    private var borderAnimator: ValueAnimator? = null

    fun show() {
        if (overlayView != null) return

        // 创建一个带有应用主题的 ContextWrapper
        val themedContext = ContextThemeWrapper(context, R.style.Theme_vFlow)

        // 创建根布局
        // 显式禁用 fitsSystemWindows，防止 View 自动给导航栏留出 Padding
        overlayView = FrameLayout(themedContext).apply {
            fitsSystemWindows = false
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        // 创建彩虹边框 View
        borderView = RainbowBorderView(themedContext).apply {
            val cornerRadius = getScreenCornerRadius(themedContext)
            this.setCornerRadius(cornerRadius)
        }
        overlayView?.addView(borderView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 创建状态悬浮卡片
        val card = MaterialCardView(themedContext).apply {
            radius = 24f
            cardElevation = 10f
            setCardBackgroundColor(Color.parseColor("#E6FFFFFF"))
            setContentPadding(32, 24, 32, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 120 // 避开状态栏
                leftMargin = 32
                rightMargin = 32
            }
        }

        val textContainer = android.widget.LinearLayout(themedContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        statusText = TextView(themedContext).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            text = "AI 正在观察屏幕..."
        }

        actionText = TextView(themedContext).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            text = "准备中"
            setPadding(0, 8, 0, 0)
        }

        textContainer.addView(statusText)
        textContainer.addView(actionText)
        card.addView(textContainer)
        overlayView?.addView(card)
        statusCard = card

        // 获取屏幕的真实物理尺寸（包含导航栏和状态栏）
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // 添加到 WindowManager
        val params = WindowManager.LayoutParams(
            metrics.widthPixels, // 使用真实宽度
            metrics.heightPixels, // 使用真实高度，强制覆盖导航栏
            getOverlayType(),
            // 关键 Flags
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // 允许超出屏幕限制
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        )

        // 适配刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        try {
            windowManager.addView(overlayView, params)
            startBorderAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateStatus(thought: String?, action: String?) {
        overlayView?.post {
            if (!thought.isNullOrBlank()) {
                statusText?.text = "状态: ${thought.take(30)}..."
                statusText?.visibility = View.VISIBLE
            }
            if (!action.isNullOrBlank()) {
                actionText?.text = action
                actionText?.visibility = View.VISIBLE
            }
        }
    }

    suspend fun hideForScreenshot() = withContext(Dispatchers.Main) {
        if (overlayView == null || overlayView?.visibility == View.GONE) return@withContext
        overlayView?.visibility = View.INVISIBLE
        delay(150)
    }

    suspend fun restoreAfterScreenshot() = withContext(Dispatchers.Main) {
        if (overlayView == null) return@withContext
        overlayView?.visibility = View.VISIBLE
    }

    fun dismiss() {
        if (overlayView != null) {
            try {
                borderAnimator?.cancel()
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private fun startBorderAnimation() {
        borderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                borderView?.setPhase(it.animatedValue as Float)
            }
            start()
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun getScreenCornerRadius(context: Context): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val display = context.display
                val corner = display?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                if (corner != null) {
                    return corner.radius.toFloat()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return 125f
    }

    private class RainbowBorderView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
        }
        private var cornerRadius = 0f
        private var phase = 0f
        private val colors = intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        )

        fun setCornerRadius(radius: Float) {
            this.cornerRadius = radius
            invalidate()
        }

        fun setPhase(p: Float) {
            this.phase = p
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 获取全屏尺寸，此时 width/height 应该是真实的屏幕物理尺寸
            val w = width.toFloat()
            val h = height.toFloat()
            val strokeHalf = paint.strokeWidth / 2

            val shader = SweepGradient(w / 2, h / 2, colors, null)
            val matrix = Matrix()
            matrix.postRotate(phase * 360f, w / 2, h / 2)
            shader.setLocalMatrix(matrix)
            paint.shader = shader

            val rect = RectF(strokeHalf, strokeHalf, w - strokeHalf, h - strokeHalf)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}