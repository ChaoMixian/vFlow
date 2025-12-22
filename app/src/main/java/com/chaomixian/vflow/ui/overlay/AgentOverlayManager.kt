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
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

/**
 * 管理 Agent 执行时的视觉反馈（彩虹边框 + 底部控制面板）。
 */
class AgentOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // 全屏流光边框 (不可点击)
    private var borderRoot: FrameLayout? = null
    private var borderView: RainbowBorderView? = null
    private var borderAnimator: ValueAnimator? = null

    // 底部控制面板 (可点击)
    private var controlRoot: FrameLayout? = null
    private var statusText: TextView? = null
    private var actionText: TextView? = null
    private var btnPause: MaterialButton? = null
    private var btnCancel: MaterialButton? = null

    // 状态控制
    private var isPaused = false
    private var isCancelConfirming = false
    // 用于挂起 Agent 协程的锁，不为 null 时表示暂停中
    private var pauseSignal: CompletableDeferred<Unit>? = null
    // 标记任务是否被用户取消
    @Volatile var isCancelled = false

    fun show() {
        if (borderRoot != null) return

        // 创建带有应用主题的 ContextWrapper
        val themedContext = ContextThemeWrapper(context, R.style.Theme_vFlow)

        // 初始化边框窗口 (Border)
        borderRoot = FrameLayout(themedContext).apply {
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
        borderRoot?.addView(borderView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val borderParams = WindowManager.LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            getOverlayType(),
            // 边框层必须完全透传点击，不影响操作
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        // 初始化控制窗口 (Control)
        controlRoot = FrameLayout(themedContext)

        val card = MaterialCardView(themedContext).apply {
            radius = 50f
            cardElevation = 10f
            // 稍带透明的 Surface 色，适配深色模式
            setCardBackgroundColor(Color.parseColor("#F2FFFFFF")) // 95% 白
            setContentPadding(48, 32, 48, 32)
        }

        // 卡片布局参数 (底部居中)
        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 内部垂直布局
        val contentLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 状态文本
        statusText = TextView(themedContext).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            text = "AI 正在观察..."
            gravity = Gravity.CENTER
        }

        // 动作文本
        actionText = TextView(themedContext).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            text = "准备就绪"
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }

        // 按钮容器
        val buttonLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // 暂停/继续按钮
        btnPause = MaterialButton(themedContext, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "暂停"
            setIconResource(R.drawable.rounded_pause_24)
            setOnClickListener { togglePause() }
        }

        // 取消按钮 (红色警告色)
        btnCancel = MaterialButton(themedContext, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "结束"
            setTextColor(Color.RED)
            //iconTint = android.content.res.ColorStateList.valueOf(Color.RED)
            setIconResource(R.drawable.rounded_close_small_24)
            setOnClickListener { handleCancel() }
        }

        // 添加间距
        val spacer = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(32, 1)
        }

        buttonLayout.addView(btnPause)
        buttonLayout.addView(spacer)
        buttonLayout.addView(btnCancel)

        contentLayout.addView(statusText)
        contentLayout.addView(actionText)
        contentLayout.addView(buttonLayout)
        card.addView(contentLayout)
        controlRoot?.addView(card, cardParams)

        val controlWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // 宽度占满方便居中
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            // 加上 FLAG_NOT_FOCUSABLE 防止抢键盘焦点
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 120 // 底部留出 Margin，避开手势条
        }

        // 添加视图
        try {
            windowManager.addView(borderRoot, borderParams)
            windowManager.addView(controlRoot, controlWindowParams)
            startBorderAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Agent 每次循环开始时调用此方法。
     * 如果处于暂停状态，这里会挂起，直到用户点击“继续”。
     * 如果用户点击了“取消”，这里会抛出异常终止任务。
     */
    suspend fun awaitState() {
        // 如果有暂停信号，等待它完成
        pauseSignal?.await()

        // 检查是否取消
        if (isCancelled) {
            throw CancellationException("用户手动停止了任务")
        }
    }

    private fun togglePause() {
        if (isPaused) {
            //以此恢复
            isPaused = false
            btnPause?.text = "暂停"
            btnPause?.setIconResource(R.drawable.rounded_pause_24)
            actionText?.text = "任务继续..."
            borderView?.visibility = View.VISIBLE // 恢复边框

            // 解锁协程
            pauseSignal?.complete(Unit)
            pauseSignal = null
        } else {
            //以此暂停
            isPaused = true
            btnPause?.text = "继续"
            btnPause?.setIconResource(R.drawable.rounded_play_arrow_24)
            actionText?.text = "已暂停 (您可以操作屏幕)"
            statusText?.text = "等待用户指令..."
            borderView?.visibility = View.INVISIBLE // 隐藏边框，方便用户看清屏幕

            // 创建锁
            pauseSignal = CompletableDeferred()
        }
    }

    private fun handleCancel() {
        if (isCancelConfirming) {
            // 确认取消
            isCancelled = true
            actionText?.text = "正在停止..."
            // 如果当前是暂停状态，必须先解锁，让 Agent 协程能走到 awaitState 的检查逻辑
            pauseSignal?.complete(Unit)
            dismiss()
        } else {
            // 第一次点击，提示确认
            isCancelConfirming = true
            btnCancel?.text = "确定?"
            // 3秒后如果没有确认，自动恢复
            controlRoot?.postDelayed({
                if (!isCancelled) {
                    isCancelConfirming = false
                    btnCancel?.text = "结束"
                }
            }, 3000)
        }
    }

    fun updateStatus(thought: String?, action: String?) {
        if (isPaused) return // 暂停时不更新文字

        controlRoot?.post {
            if (!thought.isNullOrBlank()) {
                statusText?.text = "Thinking: ${thought.take(30)}..."
            }
            if (!action.isNullOrBlank()) {
                actionText?.text = action
            }
        }
    }

    suspend fun hideForScreenshot() = withContext(Dispatchers.Main) {
        if (borderRoot == null) return@withContext
        // 截图时隐藏所有悬浮窗
        borderRoot?.visibility = View.INVISIBLE
        controlRoot?.visibility = View.INVISIBLE
        delay(150)
    }

    suspend fun restoreAfterScreenshot() = withContext(Dispatchers.Main) {
        // 恢复显示（如果处于暂停状态，保持边框隐藏）
        if (!isPaused) borderRoot?.visibility = View.VISIBLE
        controlRoot?.visibility = View.VISIBLE
    }

    fun dismiss() {
        try {
            borderAnimator?.cancel()
            if (borderRoot != null) windowManager.removeView(borderRoot)
            if (controlRoot != null) windowManager.removeView(controlRoot)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            borderRoot = null
            controlRoot = null
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
            } catch (e: Exception) { /* Ignore */ }
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