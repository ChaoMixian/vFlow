// 文件: java/com/chaomixian/vflow/ui/float/DynamicFloatWindowService.kt
package com.chaomixian.vflow.ui.float

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.ui.common.DynamicUiRenderer
import com.chaomixian.vflow.ui.common.ThemeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 动态悬浮窗服务
 *
 * 用于显示工作流定义的 UI 组件悬浮窗。
 * 支持动态添加组件、事件监听、拖拽移动等功能。
 */
class DynamicFloatWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sessionId: String? = null

    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        const val EXTRA_ELEMENTS = "elements"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_ALPHA = "alpha"
        const val ACTION_CLOSE = "com.chaomixian.vflow.ACTION_CLOSE_FLOAT_WINDOW"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> closeFloatWindow()
            null -> showFloatWindow(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        closeFloatWindow()
        serviceScope.cancel()
    }

    /**
     * 显示悬浮窗
     */
    private fun showFloatWindow(intent: Intent?) {
        if (floatView != null) return // 已经显示

        val elements = intent?.getParcelableArrayListExtra<UiElement>(EXTRA_ELEMENTS)
        val newSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val title = intent?.getStringExtra(EXTRA_TITLE)
        val width = intent?.getIntExtra(EXTRA_WIDTH, 300) ?: 300
        val height = intent?.getIntExtra(EXTRA_HEIGHT, 400) ?: 400
        val alpha = intent?.getFloatExtra(EXTRA_ALPHA, 0.95f) ?: 0.95f

        if (elements == null || newSessionId == null) return

        sessionId = newSessionId

        // 创建带主题的 Context
        val themedContext = ThemeUtils.createThemedContext(this)

        // 使用布局文件创建悬浮窗视图
        floatView = LayoutInflater.from(themedContext).inflate(R.layout.dynamic_float_window, null)

        // 获取内容区域和 header
        val contentContainer = floatView?.findViewById<LinearLayout>(R.id.float_window_content)
        val headerView = floatView?.findViewById<LinearLayout>(R.id.float_window_header)
        val titleView = floatView?.findViewById<TextView>(R.id.float_window_title)
        val closeBtn = floatView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.float_window_close_btn)
        val dragIndicator = floatView?.findViewById<ImageView>(R.id.drag_indicator)

        // 设置标题
        if (!title.isNullOrEmpty()) {
            titleView?.text = title
            titleView?.visibility = View.VISIBLE
        }

        // 设置窗口参数
        val params = WindowManager.LayoutParams(
            width.dpToPx(),
            height.dpToPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.alpha = alpha
        }

        // 渲染 UI 组件到内容区域
        if (contentContainer != null) {
            DynamicUiRenderer.render(
                themedContext,
                elements,
                contentContainer,
                serviceScope,
                sessionId,
                onSubmit = { /* 悬浮窗不需要提交逻辑 */ }
            )
        }

        // Header 关闭按钮点击事件
        closeBtn?.setOnClickListener {
            closeFloatWindow()
        }

        // Header 拖拽功能 - 拖拽指示器
        dragIndicator?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                return when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        true
                    }
                    else -> false
                }
            }
        })

        // Header 拖拽功能 - 整个标题栏
        headerView?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                return when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        true
                    }
                    else -> false
                }
            }
        })

        // 添加到窗口
        windowManager.addView(floatView, params)

        // 启动命令监听
        startCommandListener()
    }

    /**
     * 关闭悬浮窗
     */
    private fun closeFloatWindow() {
        val currentSessionId = sessionId ?: return

        // 发送 "closed" 事件（与 DynamicUiActivity.onDestroy() 行为一致）
        serviceScope.launch {
            // TODO: 收集所有组件值
            val allValues = emptyMap<String, Any?>()
            UiSessionBus.emitEvent(UiEvent(currentSessionId, "system", "closed", null, allValues))
            UiSessionBus.notifyClosed(currentSessionId)
        }

        // 移除悬浮窗视图
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }

        sessionId = null
        stopSelf()
    }

    /**
     * 启动命令监听
     */
    private fun startCommandListener() {
        serviceScope.launch {
            val currentSessionId = sessionId ?: return@launch
            UiSessionBus.getCommandFlow(currentSessionId)?.collect { cmd ->
                when (cmd.type) {
                    "close" -> closeFloatWindow()
                    "update" -> handleUpdateCommand(cmd)
                }
            }
        }
    }

    /**
     * 处理更新命令
     */
    private suspend fun handleUpdateCommand(cmd: com.chaomixian.vflow.core.workflow.module.ui.UiCommand) {
        // TODO: 实现组件更新逻辑
        // 可以参考 DynamicUiActivity 中的实现
    }

    /**
     * 处理返回键
     */
    private fun handleBackPress() {
        sessionId?.let { id ->
            serviceScope.launch {
                val allValues = DynamicUiRenderer.collectAllValues()
                UiSessionBus.emitEvent(UiEvent(id, "system", "back_pressed", null, allValues))
            }
            // 发送关闭事件
            UiSessionBus.notifyClosed(id)
            closeFloatWindow()
        }
    }

    /**
     * 将 dp 转换为 px
     */
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
}
