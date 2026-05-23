// 文件: ScreenCaptureOverlay.kt
package com.chaomixian.vflow.ui.overlay

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.animation.DecelerateInterpolator
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.services.ShellManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 悬浮截图覆盖层。
 * 提供悬浮按钮用于截图，截图后可框选区域。
 */
class ScreenCaptureOverlay(
    private val context: Context,
    private val cacheDir: File
) {
    companion object {
        @Volatile
        private var activeOverlay: ScreenCaptureOverlay? = null
    }

    // 使用 applicationContext 获取 WindowManager，避免 Activity 泄漏
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 悬浮按钮
    private var fabRoot: FrameLayout? = null
    private var fab: ImageView? = null

    // 截图预览和裁剪界面
    private var cropRoot: FrameLayout? = null
    private var cropView: ScreenshotCropView? = null
    private var cropToolbar: MaterialCardView? = null
    private var screenshotBitmap: Bitmap? = null

    private var resultDeferred: CompletableDeferred<Uri?>? = null

    /**
     * 显示悬浮截图按钮，等待用户操作。
     * @return 裁剪后的图片URI，如果取消则返回null
     */
    suspend fun captureAndCrop(): Uri? {
        activeOverlay?.takeIf { it !== this }?.cancelActive()
        activeOverlay = this
        resultDeferred = CompletableDeferred()
        showFloatingButton()
        val result = resultDeferred?.await()
        dismiss()
        return result
    }

    private fun cancelActive() {
        resultDeferred?.complete(null)
        dismiss()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (fabRoot != null) return

        // 使用 appContext 创建容器，避免 Activity 泄漏
        fabRoot = FrameLayout(appContext).apply {
            // 设置透明背景，解决灰色底的问题
            setBackgroundColor(Color.TRANSPARENT)
        }

        val density = appContext.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        val containerColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.WHITE
        )
        val iconColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.BLACK
        )

        // 和 UI 检查器一致：普通 ImageView + 圆角背景，不使用 FAB，避免默认阴影。
        fab = ImageView(context).apply {
            setImageResource(R.drawable.rounded_fullscreen_portrait_24)
            setBackgroundResource(R.drawable.bg_widget_rounded)
            background.setTint(containerColor)
            setColorFilter(iconColor)
            setPadding(dp(13f), dp(13f), dp(13f), dp(13f))
            elevation = 0f
            translationZ = 0f
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

        // 支持拖动，点击时截图。
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        fab?.setOnTouchListener { _, event ->
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
                    } catch (e: Exception) {
                        // 忽略
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        fabRoot?.visibility = View.INVISIBLE
                        scope.launch {
                            delay(300)
                            takeScreenshot()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }

        try {
            DebugLogger.i("ScreenCaptureOverlay", "准备添加悬浮按钮，layoutParams: $layoutParams")
            windowManager.addView(fabRoot, layoutParams)
            DebugLogger.i("ScreenCaptureOverlay", "悬浮按钮添加成功")
        } catch (e: Exception) {
            DebugLogger.e("ScreenCaptureOverlay", "添加悬浮按钮失败", e)
            resultDeferred?.complete(null)
        }
    }

    private suspend fun takeScreenshot() {
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val fileName = "capture_temp_$timestamp.png"
            val cacheFile = File(cacheDir, fileName)
            val path = cacheFile.absolutePath

            val command = "screencap -p \"$path\""
            DebugLogger.i("ScreenCaptureOverlay", "执行截图命令: $command")
            // 使用 appContext 执行 shell 命令
            val result = ShellManager.execShellCommand(appContext, command, ShellManager.ShellMode.AUTO)
            DebugLogger.i("ScreenCaptureOverlay", "截图命令执行结果: $result")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                DebugLogger.i("ScreenCaptureOverlay", "截图文件大小: ${cacheFile.length()} 字节")
                screenshotBitmap = BitmapFactory.decodeFile(path)
                cacheFile.delete() // 删除临时文件
                if (screenshotBitmap != null) {
                    DebugLogger.i("ScreenCaptureOverlay", "截图成功: ${screenshotBitmap!!.width}x${screenshotBitmap!!.height}")
                } else {
                    DebugLogger.e("ScreenCaptureOverlay", "BitmapFactory 解码失败，文件可能损坏")
                }
            } else {
                DebugLogger.e("ScreenCaptureOverlay", "截图文件不存在或为空: exists=${cacheFile.exists()}, length=${cacheFile.length()}")
            }
        }

        if (screenshotBitmap != null) {
            withContext(Dispatchers.Main) {
                showCropView()
            }
        } else {
            DebugLogger.e("ScreenCaptureOverlay", "截图失败，无法继续")
            resultDeferred?.complete(null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCropView() {
        val bitmap = screenshotBitmap ?: run {
            DebugLogger.e("ScreenCaptureOverlay", "showCropView: screenshotBitmap 为 null")
            return
        }

        DebugLogger.i("ScreenCaptureOverlay", "开始显示裁剪视图，bitmap: ${bitmap.width}x${bitmap.height}")

        // 移除悬浮按钮
        try {
            if (fabRoot != null) {
                windowManager.removeView(fabRoot)
                fabRoot = null
                DebugLogger.i("ScreenCaptureOverlay", "悬浮按钮已移除")
            }
        } catch (e: Exception) {
            DebugLogger.e("ScreenCaptureOverlay", "移除悬浮按钮失败", e)
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        DebugLogger.i("ScreenCaptureOverlay", "屏幕尺寸: ${metrics.widthPixels}x${metrics.heightPixels}")

        // 使用 appContext 创建容器
        cropRoot = FrameLayout(appContext).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 截图与选择框绘制在同一个 View 中，便于实现“拖框调区域，拖外部移动截图”。
        cropView = ScreenshotCropView(
            context = appContext,
            bitmap = bitmap,
            onFrameChanged = { selectionRect, contentAlpha ->
                cropToolbar?.let { toolbar ->
                    updateToolbarPosition(toolbar, selectionRect, contentAlpha)
                }
            }
        )
        cropRoot?.addView(cropView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val toolbar = createSelectionToolbar(bitmap)
        cropToolbar = toolbar
        cropRoot?.addView(
            toolbar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val layoutParams = WindowManager.LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            DebugLogger.i("ScreenCaptureOverlay", "准备添加裁剪视图，layoutParams: $layoutParams")
            windowManager.addView(cropRoot, layoutParams)
            DebugLogger.i("ScreenCaptureOverlay", "裁剪视图添加成功")
        } catch (e: Exception) {
            DebugLogger.e("ScreenCaptureOverlay", "添加裁剪视图失败", e)
            resultDeferred?.complete(null)
        }
    }

    private fun createSelectionToolbar(bitmap: Bitmap): MaterialCardView {
        val density = appContext.resources.displayMetrics.density

        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            )
            radius = dp(26f).toFloat()
            cardElevation = dp(8f).toFloat()
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            strokeWidth = dp(1f)
            setContentPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            alpha = 0f
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val errorContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorErrorContainer)
        val onErrorContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnErrorContainer)
        val primaryContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimaryContainer)
        val onPrimaryContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val tertiaryContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorTertiaryContainer)
        val onTertiaryContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnTertiaryContainer)

        val cancelButton = createToolbarButton(
            iconRes = R.drawable.rounded_close_24,
            backgroundColor = errorContainer,
            iconColor = onErrorContainer
        ).apply {
            contentDescription = context.getString(R.string.common_cancel)
            setOnClickListener {
                finishSelectionAnimated {
                    resultDeferred?.complete(null)
                }
            }
        }
        row.addView(cancelButton)

        val resetButton = createToolbarButton(
            iconRes = R.drawable.rounded_reset_iso_24,
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer
        ).apply {
            contentDescription = context.getString(R.string.workflow_editor_undo)
            setOnClickListener {
                cropView?.resetAnimated()
            }
        }
        row.addView(resetButton, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
            marginStart = dp(8f)
        })

        val confirmButton = createToolbarButton(
            iconRes = R.drawable.rounded_check_24,
            backgroundColor = tertiaryContainer,
            iconColor = onTertiaryContainer
        ).apply {
            contentDescription = context.getString(R.string.common_confirm)
            setOnClickListener {
                val rect = cropView?.getSelectedImageRect()
                if (rect != null && rect.width() > 10 && rect.height() > 10) {
                    setSelectionToolbarEnabled(false)
                    scope.launch {
                        val croppedUri = cropAndSave(bitmap, rect)
                        withContext(Dispatchers.Main) {
                            finishSelectionAnimated {
                                resultDeferred?.complete(croppedUri)
                            }
                        }
                    }
                } else {
                    finishSelectionAnimated {
                        resultDeferred?.complete(null)
                    }
                }
            }
        }
        row.addView(confirmButton, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
            marginStart = dp(8f)
        })

        card.addView(row)
        return card
    }

    private fun finishSelectionAnimated(onFinished: () -> Unit) {
        setSelectionToolbarEnabled(false)
        cropView?.finishAnimated(onFinished) ?: onFinished()
    }

    private fun setSelectionToolbarEnabled(enabled: Boolean) {
        cropToolbar?.isEnabled = enabled
        val row = cropToolbar?.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until row.childCount) {
            row.getChildAt(i).isEnabled = enabled
        }
    }

    private fun createToolbarButton(
        iconRes: Int,
        backgroundColor: Int,
        iconColor: Int
    ): ImageButton {
        val density = appContext.resources.displayMetrics.density
        val size = (40f * density + 0.5f).toInt()
        return ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageResource(iconRes)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backgroundColor)
            }
            setColorFilter(iconColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
    }

    private fun updateToolbarPosition(
        toolbar: MaterialCardView,
        selectionRect: RectF,
        contentAlpha: Int
    ) {
        val density = appContext.resources.displayMetrics.density
        val margin = 8f * density
        val gap = 12f * density

        if (toolbar.measuredWidth == 0 || toolbar.measuredHeight == 0) {
            toolbar.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }

        val toolbarWidth = toolbar.measuredWidth.toFloat()
        val toolbarHeight = toolbar.measuredHeight.toFloat()
        val parentWidth = cropRoot?.width?.takeIf { it > 0 }?.toFloat() ?: return
        val parentHeight = cropRoot?.height?.takeIf { it > 0 }?.toFloat() ?: return

        toolbar.x = (selectionRect.centerX() - toolbarWidth / 2f)
            .coerceIn(margin, (parentWidth - toolbarWidth - margin).coerceAtLeast(margin))

        val belowTop = selectionRect.bottom + gap
        val aboveTop = selectionRect.top - gap - toolbarHeight
        toolbar.y = when {
            belowTop + toolbarHeight <= parentHeight - margin -> belowTop
            aboveTop >= margin -> aboveTop
            else -> (parentHeight - toolbarHeight - margin).coerceAtLeast(margin)
        }
        toolbar.alpha = contentAlpha / 255f
    }

    private suspend fun cropAndSave(source: Bitmap, rect: Rect): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val imageRect = Rect(
                    rect.left.coerceIn(0, source.width),
                    rect.top.coerceIn(0, source.height),
                    rect.right.coerceIn(0, source.width),
                    rect.bottom.coerceIn(0, source.height)
                )

                if (imageRect.width() <= 0 || imageRect.height() <= 0) {
                    return@withContext null
                }

                val cropped = Bitmap.createBitmap(
                    source,
                    imageRect.left,
                    imageRect.top,
                    imageRect.width(),
                    imageRect.height()
                )

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                val outputFile = File(cacheDir, "template_$timestamp.png")
                FileOutputStream(outputFile).use { fos ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                // 只回收新创建的 bitmap，避免回收源 bitmap (screenshotBitmap)
                if (cropped != source) {
                    cropped.recycle()
                }

                Uri.fromFile(outputFile)
            } catch (e: Exception) {
                DebugLogger.e("ScreenCaptureOverlay", "裁剪保存失败", e)
                null
            }
        }
    }

    private fun dismiss() {
        if (activeOverlay === this) {
            activeOverlay = null
        }
        scope.cancel()
        try {
            if (fabRoot != null) {
                windowManager.removeView(fabRoot)
                fabRoot = null
            }
            if (cropRoot != null) {
                windowManager.removeView(cropRoot)
                cropRoot = null
            }
        } catch (e: Exception) {
            // 忽略
        }

        cropView = null
        cropToolbar = null

        // 延迟回收 bitmap，确保视图已经完全移除且没有正在进行的绘制操作
        val bitmapToRecycle = screenshotBitmap
        screenshotBitmap = null
        if (bitmapToRecycle != null) {
            Handler(Looper.getMainLooper()).post {
                try {
                    if (!bitmapToRecycle.isRecycled) {
                        bitmapToRecycle.recycle()
                    }
                } catch (e: Exception) {
                    DebugLogger.e("ScreenCaptureOverlay", "回收 bitmap 失败", e)
                }
            }
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

    /**
     * 截图裁剪视图。
     *
     * 初始状态会将截图收缩到屏幕中间；选择框保持在视图中央。触摸选择框边缘可以调整大小，
     * 触摸框内可以移动选择框，触摸其它位置则移动截图本身。
     */
    @SuppressLint("ViewConstructor")
    private class ScreenshotCropView(
        context: Context,
        private val bitmap: Bitmap,
        private val onFrameChanged: (selectionRect: RectF, contentAlpha: Int) -> Unit
    ) : View(context) {

        companion object {
            private const val INITIAL_IMAGE_SCALE = 0.86f
            private const val EDGE_TOUCH_SIZE = 44f
            private const val MIN_CROP_SIZE = 50f
            private const val MIN_IMAGE_SCALE = 1f
            private const val MAX_IMAGE_SCALE = 6f
            private const val SHOW_ANIMATION_DURATION = 750L
            private const val HIDE_ANIMATION_DURATION = 520L
            private const val RESET_ANIMATION_DURATION = 260L
        }

        private enum class DragMode {
            NONE,
            LEFT,
            TOP,
            RIGHT,
            BOTTOM,
            TOP_LEFT,
            TOP_RIGHT,
            BOTTOM_LEFT,
            BOTTOM_RIGHT,
            MOVE_SELECTION,
            PAN_IMAGE
        }

        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dimPaint = Paint().apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEB3B")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEB3B")
            style = Paint.Style.FILL
        }

        private val cornerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        private val imageRect = RectF()
        private val initialImageRect = RectF()
        private val targetImageRect = RectF()
        private val selectionRect = RectF()
        private val defaultSelectionRect = RectF()
        private val bitmapSrcRect = Rect(0, 0, bitmap.width, bitmap.height)

        private var contentAlpha = 0
        private var dragMode = DragMode.NONE
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var isScaling = false
        private var showAnimator: ValueAnimator? = null
        private var resetAnimator: ValueAnimator? = null
        private var finishAnimator: ValueAnimator? = null
        private var isFinishing = false
        private val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    dragMode = DragMode.NONE
                    isScaling = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleImage(detector.scaleFactor, detector.focusX, detector.focusY)
                    notifyFrameChanged()
                    invalidate()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                }
            }
        )

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return

            val fitScale = min(w / bitmap.width.toFloat(), h / bitmap.height.toFloat()) * INITIAL_IMAGE_SCALE
            val targetWidth = bitmap.width * fitScale
            val targetHeight = bitmap.height * fitScale
            val targetLeft = (w - targetWidth) / 2f
            val targetTop = (h - targetHeight) / 2f
            targetImageRect.set(targetLeft, targetTop, targetLeft + targetWidth, targetTop + targetHeight)
            initialImageRect.set(0f, 0f, w.toFloat(), h.toFloat())
            imageRect.set(initialImageRect)

            val defaultSize = min(w, h) / 3f
            val centerX = w / 2f
            val centerY = h / 2f
            defaultSelectionRect.set(
                centerX - defaultSize / 2f,
                centerY - defaultSize / 2f,
                centerX + defaultSize / 2f,
                centerY + defaultSize / 2f
            )
            selectionRect.set(defaultSelectionRect)
            constrainSelectionToView()
            notifyFrameChanged()
            startShowAnimation()
        }

        override fun onDetachedFromWindow() {
            showAnimator?.cancel()
            resetAnimator?.cancel()
            finishAnimator?.cancel()
            super.onDetachedFromWindow()
        }

        fun resetAnimated() {
            if (width <= 0 || height <= 0 || isFinishing) return

            showAnimator?.cancel()
            resetAnimator?.cancel()
            finishAnimator?.cancel()

            val startImageRect = RectF(imageRect)
            val startSelectionRect = RectF(selectionRect)
            resetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RESET_ANIMATION_DURATION
                interpolator = DecelerateInterpolator(1.8f)
                addUpdateListener { animator ->
                    val t = animator.animatedValue as Float
                    lerpRect(startImageRect, targetImageRect, t, imageRect)
                    lerpRect(startSelectionRect, defaultSelectionRect, t, selectionRect)
                    notifyFrameChanged()
                    invalidate()
                }
                start()
            }
        }

        fun finishAnimated(onFinished: () -> Unit) {
            if (width <= 0 || height <= 0) {
                onFinished()
                return
            }
            if (isFinishing) return

            isFinishing = true
            showAnimator?.cancel()
            resetAnimator?.cancel()
            finishAnimator?.cancel()

            val startImageRect = RectF(imageRect)
            val startAlpha = contentAlpha
            finishAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = HIDE_ANIMATION_DURATION
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { animator ->
                    val t = animator.animatedValue as Float
                    lerpRect(startImageRect, initialImageRect, t, imageRect)
                    contentAlpha = (startAlpha * (1f - t)).toInt().coerceIn(0, 255)
                    notifyFrameChanged()
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onFinished()
                    }
                })
                start()
            }
        }

        fun getSelectedImageRect(): Rect? {
            val visibleSelection = RectF(selectionRect)
            if (!visibleSelection.intersect(imageRect)) return null

            val scaleX = bitmap.width / imageRect.width()
            val scaleY = bitmap.height / imageRect.height()
            val left = ((visibleSelection.left - imageRect.left) * scaleX).toInt().coerceIn(0, bitmap.width)
            val top = ((visibleSelection.top - imageRect.top) * scaleY).toInt().coerceIn(0, bitmap.height)
            val right = ((visibleSelection.right - imageRect.left) * scaleX).toInt().coerceIn(0, bitmap.width)
            val bottom = ((visibleSelection.bottom - imageRect.top) * scaleY).toInt().coerceIn(0, bitmap.height)
            return Rect(left, top, right, bottom)
        }

        private fun startShowAnimation() {
            showAnimator?.cancel()
            contentAlpha = 0
            showAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SHOW_ANIMATION_DURATION
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { animator ->
                    val t = animator.animatedValue as Float
                    lerpRect(initialImageRect, targetImageRect, t, imageRect)
                    contentAlpha = (255 * t).toInt().coerceIn(0, 255)
                    notifyFrameChanged()
                    invalidate()
                }
                start()
            }
        }

        private fun lerpRect(from: RectF, to: RectF, fraction: Float, out: RectF) {
            out.set(
                from.left + (to.left - from.left) * fraction,
                from.top + (to.top - from.top) * fraction,
                from.right + (to.right - from.right) * fraction,
                from.bottom + (to.bottom - from.bottom) * fraction
            )
        }

        private fun hitTestEdge(x: Float, y: Float): DragMode {
            val expanded = RectF(selectionRect).apply { inset(-EDGE_TOUCH_SIZE, -EDGE_TOUCH_SIZE) }
            if (!expanded.contains(x, y)) return DragMode.NONE

            val nearLeft = abs(x - selectionRect.left) <= EDGE_TOUCH_SIZE && y in selectionRect.top..selectionRect.bottom
            val nearTop = abs(y - selectionRect.top) <= EDGE_TOUCH_SIZE && x in selectionRect.left..selectionRect.right
            val nearRight = abs(x - selectionRect.right) <= EDGE_TOUCH_SIZE && y in selectionRect.top..selectionRect.bottom
            val nearBottom = abs(y - selectionRect.bottom) <= EDGE_TOUCH_SIZE && x in selectionRect.left..selectionRect.right

            return when {
                nearLeft && nearTop -> DragMode.TOP_LEFT
                nearRight && nearTop -> DragMode.TOP_RIGHT
                nearLeft && nearBottom -> DragMode.BOTTOM_LEFT
                nearRight && nearBottom -> DragMode.BOTTOM_RIGHT
                nearLeft -> DragMode.LEFT
                nearTop -> DragMode.TOP
                nearRight -> DragMode.RIGHT
                nearBottom -> DragMode.BOTTOM
                selectionRect.contains(x, y) -> DragMode.MOVE_SELECTION
                else -> DragMode.NONE
            }
        }

        private fun constrainSelectionToView() {
            if (selectionRect.width() < MIN_CROP_SIZE) {
                selectionRect.right = selectionRect.left + MIN_CROP_SIZE
            }
            if (selectionRect.height() < MIN_CROP_SIZE) {
                selectionRect.bottom = selectionRect.top + MIN_CROP_SIZE
            }

            val dx = when {
                selectionRect.left < 0f -> -selectionRect.left
                selectionRect.right > width -> width - selectionRect.right
                else -> 0f
            }
            val dy = when {
                selectionRect.top < 0f -> -selectionRect.top
                selectionRect.bottom > height -> height - selectionRect.bottom
                else -> 0f
            }
            selectionRect.offset(dx, dy)
        }

        private fun moveSelection(dx: Float, dy: Float) {
            selectionRect.offset(dx, dy)
            constrainSelectionToView()
        }

        private fun panImage(dx: Float, dy: Float) {
            imageRect.offset(dx, dy)
            constrainImagePosition()
        }

        private fun scaleImage(scaleFactor: Float, focusX: Float, focusY: Float) {
            val baseWidth = targetImageRect.width().takeIf { it > 0f } ?: return
            val baseHeight = targetImageRect.height().takeIf { it > 0f } ?: return
            val currentScale = imageRect.width() / baseWidth
            val targetScale = (currentScale * scaleFactor).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
            val appliedScale = targetScale / currentScale
            if (!appliedScale.isFinite() || abs(appliedScale - 1f) < 0.001f) return

            val newWidth = (baseWidth * targetScale).coerceAtLeast(baseWidth)
            val newHeight = (baseHeight * targetScale).coerceAtLeast(baseHeight)
            val focusRatioX = ((focusX - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
            val focusRatioY = ((focusY - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
            val newLeft = focusX - newWidth * focusRatioX
            val newTop = focusY - newHeight * focusRatioY

            imageRect.set(newLeft, newTop, newLeft + newWidth, newTop + newHeight)
            constrainImagePosition()
        }

        private fun constrainImagePosition() {
            val horizontalMargin = width * 0.2f
            val verticalMargin = height * 0.2f
            var safeDx = 0f
            var safeDy = 0f

            if (imageRect.left > width - horizontalMargin) {
                safeDx = width - horizontalMargin - imageRect.left
            } else if (imageRect.right < horizontalMargin) {
                safeDx = horizontalMargin - imageRect.right
            }

            if (imageRect.top > height - verticalMargin) {
                safeDy = height - verticalMargin - imageRect.top
            } else if (imageRect.bottom < verticalMargin) {
                safeDy = verticalMargin - imageRect.bottom
            }

            imageRect.offset(safeDx, safeDy)
        }

        private fun notifyFrameChanged() {
            onFrameChanged(RectF(selectionRect), contentAlpha)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isFinishing) return true

            scaleDetector.onTouchEvent(event)

            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = x
                    lastTouchY = y
                    dragMode = hitTestEdge(x, y).let { mode ->
                        if (mode == DragMode.NONE) DragMode.PAN_IMAGE else mode
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    dragMode = DragMode.NONE
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isScaling || event.pointerCount > 1) {
                        return true
                    }
                    if (dragMode != DragMode.NONE) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        when (dragMode) {
                            DragMode.LEFT -> {
                                selectionRect.left = min(selectionRect.left + dx, selectionRect.right - MIN_CROP_SIZE)
                            }
                            DragMode.TOP -> {
                                selectionRect.top = min(selectionRect.top + dy, selectionRect.bottom - MIN_CROP_SIZE)
                            }
                            DragMode.RIGHT -> {
                                selectionRect.right = max(selectionRect.right + dx, selectionRect.left + MIN_CROP_SIZE)
                            }
                            DragMode.BOTTOM -> {
                                selectionRect.bottom = max(selectionRect.bottom + dy, selectionRect.top + MIN_CROP_SIZE)
                            }
                            DragMode.TOP_LEFT -> {
                                selectionRect.left = min(selectionRect.left + dx, selectionRect.right - MIN_CROP_SIZE)
                                selectionRect.top = min(selectionRect.top + dy, selectionRect.bottom - MIN_CROP_SIZE)
                            }
                            DragMode.TOP_RIGHT -> {
                                selectionRect.right = max(selectionRect.right + dx, selectionRect.left + MIN_CROP_SIZE)
                                selectionRect.top = min(selectionRect.top + dy, selectionRect.bottom - MIN_CROP_SIZE)
                            }
                            DragMode.BOTTOM_LEFT -> {
                                selectionRect.left = min(selectionRect.left + dx, selectionRect.right - MIN_CROP_SIZE)
                                selectionRect.bottom = max(selectionRect.bottom + dy, selectionRect.top + MIN_CROP_SIZE)
                            }
                            DragMode.BOTTOM_RIGHT -> {
                                selectionRect.right = max(selectionRect.right + dx, selectionRect.left + MIN_CROP_SIZE)
                                selectionRect.bottom = max(selectionRect.bottom + dy, selectionRect.top + MIN_CROP_SIZE)
                            }
                            DragMode.MOVE_SELECTION -> moveSelection(dx, dy)
                            DragMode.PAN_IMAGE -> panImage(dx, dy)
                            DragMode.NONE -> Unit
                        }
                        constrainSelectionToView()
                        notifyFrameChanged()

                        lastTouchX = x
                        lastTouchY = y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragMode = DragMode.NONE
                    isScaling = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, bitmapSrcRect, imageRect, imagePaint)

            dimPaint.alpha = (0x80 * (contentAlpha / 255f)).toInt()
            borderPaint.alpha = contentAlpha
            cornerPaint.alpha = contentAlpha
            cornerStrokePaint.alpha = contentAlpha
            gridPaint.alpha = (0x40 * (contentAlpha / 255f)).toInt()

            // 绘制半透明遮罩
            val rect = selectionRect
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dimPaint)
            canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dimPaint)
            canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dimPaint)

            // 绘制边框
            canvas.drawRect(rect, borderPaint)

            // 绘制角点
            val cornerSize = 24f
            val halfSize = cornerSize / 2f

            // 左上角
            canvas.drawRoundRect(
                rect.left - halfSize, rect.top - halfSize,
                rect.left + halfSize, rect.top + halfSize,
                4f, 4f, cornerPaint
            )
            canvas.drawRoundRect(
                rect.left - halfSize, rect.top - halfSize,
                rect.left + halfSize, rect.top + halfSize,
                4f, 4f, cornerStrokePaint
            )

            // 右上角
            canvas.drawRoundRect(
                rect.right - halfSize, rect.top - halfSize,
                rect.right + halfSize, rect.top + halfSize,
                4f, 4f, cornerPaint
            )
            canvas.drawRoundRect(
                rect.right - halfSize, rect.top - halfSize,
                rect.right + halfSize, rect.top + halfSize,
                4f, 4f, cornerStrokePaint
            )

            // 左下角
            canvas.drawRoundRect(
                rect.left - halfSize, rect.bottom - halfSize,
                rect.left + halfSize, rect.bottom + halfSize,
                4f, 4f, cornerPaint
            )
            canvas.drawRoundRect(
                rect.left - halfSize, rect.bottom - halfSize,
                rect.left + halfSize, rect.bottom + halfSize,
                4f, 4f, cornerStrokePaint
            )

            // 右下角
            canvas.drawRoundRect(
                rect.right - halfSize, rect.bottom - halfSize,
                rect.right + halfSize, rect.bottom + halfSize,
                4f, 4f, cornerPaint
            )
            canvas.drawRoundRect(
                rect.right - halfSize, rect.bottom - halfSize,
                rect.right + halfSize, rect.bottom + halfSize,
                4f, 4f, cornerStrokePaint
            )

            val centerX = rect.centerX()
            val centerY = rect.centerY()
            canvas.drawLine(centerX, rect.top, centerX, rect.bottom, gridPaint)
            canvas.drawLine(rect.left, centerY, rect.right, centerY, gridPaint)
        }
    }
}
