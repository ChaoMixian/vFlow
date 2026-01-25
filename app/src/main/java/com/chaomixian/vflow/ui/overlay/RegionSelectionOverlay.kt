// 文件: RegionSelectionOverlay.kt
package com.chaomixian.vflow.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.system.RegionSelectionResult
import com.chaomixian.vflow.services.ShellManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 区域选择覆盖层。
 * 提供悬浮按钮用于截图，截图后可框选区域并返回区域坐标。
 */
class RegionSelectionOverlay(
    private val context: Context,
    private val cacheDir: File
) {
    // 使用 applicationContext 获取 WindowManager，避免 Activity 泄漏
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 悬浮按钮
    private var fabRoot: FrameLayout? = null
    private var fab: FloatingActionButton? = null

    // 截图预览和区域选择界面
    private var cropRoot: FrameLayout? = null
    private var cropView: CropView? = null
    private var screenshotBitmap: Bitmap? = null
    private var screenshotUri: Uri? = null

    private var resultDeferred: CompletableDeferred<RegionSelectionResult?>? = null

    /**
     * 显示悬浮截图按钮，等待用户操作。
     * @return 区域选择结果，包含区域坐标和截图 URI，如果取消则返回 null
     */
    suspend fun captureAndSelectRegion(): RegionSelectionResult? {
        resultDeferred = CompletableDeferred()
        showFloatingButton()
        val result = resultDeferred?.await()
        dismiss()
        return result
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (fabRoot != null) return

        // 使用 appContext 创建容器，避免 Activity 泄漏
        fabRoot = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 使用原始 context（带 Material 主题）创建 FAB
        fab = FloatingActionButton(context).apply {
            setImageResource(R.drawable.rounded_fullscreen_portrait_24)
            size = FloatingActionButton.SIZE_NORMAL
            setOnClickListener {
                fabRoot?.visibility = View.INVISIBLE
                scope.launch {
                    delay(300) // 等待按钮隐藏
                    takeScreenshot()
                }
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
                    } catch (e: Exception) {
                        // 忽略
                    }
                    true
                }
                else -> false
            }
        }

        try {
            DebugLogger.i("RegionSelectionOverlay", "准备添加悬浮按钮，layoutParams: $layoutParams")
            windowManager.addView(fabRoot, layoutParams)
            DebugLogger.i("RegionSelectionOverlay", "悬浮按钮添加成功")
        } catch (e: Exception) {
            DebugLogger.e("RegionSelectionOverlay", "添加悬浮按钮失败", e)
            resultDeferred?.complete(null)
        }
    }

    private suspend fun takeScreenshot() {
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val fileName = "capture_region_$timestamp.png"
            val cacheFile = File(cacheDir, fileName)
            val path = cacheFile.absolutePath

            val command = "screencap -p \"$path\""
            DebugLogger.i("RegionSelectionOverlay", "执行截图命令: $command")
            val result = ShellManager.execShellCommand(appContext, command, ShellManager.ShellMode.AUTO)
            DebugLogger.i("RegionSelectionOverlay", "截图命令执行结果: $result")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                DebugLogger.i("RegionSelectionOverlay", "截图文件大小: ${cacheFile.length()} 字节")
                screenshotBitmap = BitmapFactory.decodeFile(path)
                screenshotUri = Uri.fromFile(cacheFile)
                if (screenshotBitmap != null) {
                    DebugLogger.i("RegionSelectionOverlay", "截图成功: ${screenshotBitmap!!.width}x${screenshotBitmap!!.height}")
                } else {
                    DebugLogger.e("RegionSelectionOverlay", "BitmapFactory 解码失败，文件可能损坏")
                }
            } else {
                DebugLogger.e("RegionSelectionOverlay", "截图文件不存在或为空: exists=${cacheFile.exists()}, length=${cacheFile.length()}, result=$result")
            }
        }

        if (screenshotBitmap != null) {
            withContext(Dispatchers.Main) {
                showCropView()
            }
        } else {
            DebugLogger.e("RegionSelectionOverlay", "截图失败，无法继续")
            resultDeferred?.complete(null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCropView() {
        val bitmap = screenshotBitmap ?: run {
            DebugLogger.e("RegionSelectionOverlay", "showCropView: screenshotBitmap 为 null")
            return
        }

        DebugLogger.i("RegionSelectionOverlay", "开始显示裁剪视图，bitmap: ${bitmap.width}x${bitmap.height}")

        // 移除悬浮按钮
        try {
            if (fabRoot != null) {
                windowManager.removeView(fabRoot)
                fabRoot = null
                DebugLogger.i("RegionSelectionOverlay", "悬浮按钮已移除")
            }
        } catch (e: Exception) {
            DebugLogger.e("RegionSelectionOverlay", "移除悬浮按钮失败", e)
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        DebugLogger.i("RegionSelectionOverlay", "屏幕尺寸: ${metrics.widthPixels}x${metrics.heightPixels}")

        // 使用 appContext 创建容器
        cropRoot = FrameLayout(appContext).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 背景图片（使用 appContext）
        val bgImage = ImageView(appContext).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        cropRoot?.addView(bgImage, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // 裁剪视图（使用 appContext）
        cropView = CropView(appContext, bitmap.width, bitmap.height)
        cropRoot?.addView(cropView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // 底部控制面板（使用 appContext）
        val controlPanel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(32, 24, 32, 24)
        }

        // 提示文字（使用 appContext）
        val hintText = TextView(appContext).apply {
            text = "拖动选择截图区域"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 32, 0)
        }

        // 使用原始 context（带 Material 主题）创建 Material 按钮
        val btnConfirm = MaterialButton(context).apply {
            text = "确定"
            setOnClickListener {
                val rect = cropView?.getSelectedRect()
                if (rect != null && rect.width() > 10 && rect.height() > 10) {
                    // 将屏幕坐标转换为图片坐标
                    val scaleX = bitmap.width.toFloat() / metrics.widthPixels
                    val scaleY = bitmap.height.toFloat() / metrics.heightPixels

                    val imageRect = Rect(
                        (rect.left * scaleX).toInt().coerceIn(0, bitmap.width),
                        (rect.top * scaleY).toInt().coerceIn(0, bitmap.height),
                        (rect.right * scaleX).toInt().coerceIn(0, bitmap.width),
                        (rect.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    )

                    // 返回图片坐标（相对于原始截图）
                    val regionStr = "${imageRect.left},${imageRect.top},${imageRect.right},${imageRect.bottom}"
                    resultDeferred?.complete(RegionSelectionResult(regionStr, screenshotUri))
                } else {
                    resultDeferred?.complete(null)
                }
            }
        }

        val btnCancel = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            setOnClickListener {
                resultDeferred?.complete(null)
            }
        }

        controlPanel.addView(hintText)
        controlPanel.addView(btnCancel)
        controlPanel.addView(btnConfirm)

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        cropRoot?.addView(controlPanel, panelParams)

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
            DebugLogger.i("RegionSelectionOverlay", "准备添加裁剪视图，layoutParams: $layoutParams")
            windowManager.addView(cropRoot, layoutParams)
            DebugLogger.i("RegionSelectionOverlay", "裁剪视图添加成功")
        } catch (e: Exception) {
            DebugLogger.e("RegionSelectionOverlay", "添加裁剪视图失败", e)
            resultDeferred?.complete(null)
        }
    }

    private fun dismiss() {
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
        screenshotBitmap?.recycle()
        screenshotBitmap = null
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
     * 裁剪视图
     */
    @SuppressLint("ViewConstructor")
    private class CropView(
        context: Context,
        private val imageWidth: Int,
        private val imageHeight: Int
    ) : View(context) {

        private val dimPaint = Paint().apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEB3B")  // 黄色边框
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEB3B")  // 黄色角点
            style = Paint.Style.FILL
        }

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var isDragging = false

        init {
            // 默认选择中央区域
            post {
                val centerX = width / 2f
                val centerY = height / 2f
                val defaultSize = minOf(width, height) / 3f
                startX = centerX - defaultSize / 2
                startY = centerY - defaultSize / 2
                endX = centerX + defaultSize / 2
                endY = centerY + defaultSize / 2
                invalidate()
            }
        }

        fun getSelectedRect(): Rect {
            val left = minOf(startX, endX).toInt().coerceIn(0, width)
            val top = minOf(startY, endY).toInt().coerceIn(0, height)
            val right = maxOf(startX, endX).toInt().coerceIn(0, width)
            val bottom = maxOf(startY, endY).toInt().coerceIn(0, height)
            return Rect(left, top, right, bottom)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isDragging = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        endX = event.x.coerceIn(0f, width.toFloat())
                        endY = event.y.coerceIn(0f, height.toFloat())
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val rect = getSelectedRect()

            // 绘制半透明遮罩
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), dimPaint)
            canvas.drawRect(0f, rect.bottom.toFloat(), width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), dimPaint)
            canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), width.toFloat(), rect.bottom.toFloat(), dimPaint)

            // 绘制黄色边框
            canvas.drawRect(rect, borderPaint)

            // 绘制四角黄色圆点
            val cornerRadius = 12f
            canvas.drawCircle(rect.left.toFloat(), rect.top.toFloat(), cornerRadius, cornerPaint)
            canvas.drawCircle(rect.right.toFloat(), rect.top.toFloat(), cornerRadius, cornerPaint)
            canvas.drawCircle(rect.left.toFloat(), rect.bottom.toFloat(), cornerRadius, cornerPaint)
            canvas.drawCircle(rect.right.toFloat(), rect.bottom.toFloat(), cornerRadius, cornerPaint)
        }
    }
}
