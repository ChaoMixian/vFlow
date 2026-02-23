// 文件：WorkflowsFloatPanelService.kt
// 描述：工作流快速控制悬浮面板服务

package com.chaomixian.vflow.ui.float

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.ContextThemeWrapper
import com.chaomixian.vflow.ui.common.ThemeUtils

/**
 * 工作流快速控制悬浮面板服务
 *
 * 功能：
 * - 显示所有收藏的工作流
 * - 支持拖拽移动位置
 * - 快速执行工作流
 * - 显示执行状态
 */
class WorkflowsFloatPanelService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var collapsedView: View? = null
    private lateinit var workflowManager: WorkflowManager
    private lateinit var adapter: WorkflowFloatPanelAdapter
    private var favoriteWorkflows = mutableListOf<Workflow>()

    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 自动收缩相关
    private var isCollapsed = false
    private var isAutoCollapsing = true // 是否启用自动收缩
    private val autoCollapseDelay = 3000L // 3秒
    private val idleTimer = Handler(Looper.getMainLooper())
    private var isUserInteracting = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var params: WindowManager.LayoutParams? = null
    private var collapsedParams: WindowManager.LayoutParams? = null
    private var isFirstPositionUpdate = true

    companion object {
        const val ACTION_SHOW = "com.chaomixian.vflow.ACTION_SHOW_FLOAT_PANEL"
        const val ACTION_HIDE = "com.chaomixian.vflow.ACTION_HIDE_FLOAT_PANEL"
        private const val COLLAPSED_SIZE = 36 // dp (圆形图标)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        workflowManager = WorkflowManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatWindow()
            ACTION_HIDE -> hideFloatWindow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 显示悬浮窗
     */
    private fun showFloatWindow() {
        if (floatView != null) return // 已经显示

        // 获取屏幕宽度
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        // 创建带主题的 Context（根据用户设置选择动态取色或默认主题）
        val themedContext = ThemeUtils.createThemedContext(this)

        // 创建悬浮窗视图
        floatView = LayoutInflater.from(themedContext).inflate(R.layout.workflows_float_panel, null)

        // 设置窗口参数
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (100 * displayMetrics.density).toInt()
            y = (200 * displayMetrics.density).toInt()
        }

        // 设置拖拽
        setupDragBehavior(floatView!!, params!!)

        // 设置关闭按钮
        floatView?.findViewById<MaterialButton>(R.id.btn_close)?.setOnClickListener {
            hideFloatWindow()
        }

        // 设置 RecyclerView
        val recyclerView = floatView?.findViewById<RecyclerView>(R.id.recycler_view_workflows)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        // 创建并设置适配器
        adapter = WorkflowFloatPanelAdapter(
            workflows = favoriteWorkflows,
            onExecute = { workflow -> executeWorkflow(workflow) },
            onStop = { workflow -> stopWorkflow(workflow) }
        )
        recyclerView?.adapter = adapter

        // 加载收藏的工作流
        serviceScope.launch {
            loadFavoriteWorkflows()
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }

        // 添加到窗口
        windowManager.addView(floatView, params)

        // 订阅执行状态更新
        observeExecutionState()

        // 启动自动收缩计时器
        startAutoCollapseTimer()

        // 监听位置变化（用于边缘吸附和自动收缩）
        observeViewPosition()
    }

    /**
     * 创建收缩后的侧边栏视图
     */
    private fun createCollapsedView(): View {
        val themedContext = ThemeUtils.createThemedContext(this)
        val collapsedView = LayoutInflater.from(themedContext).inflate(R.layout.workflows_float_panel_collapsed, null)

        // 设置点击展开
        collapsedView.setOnClickListener {
            expandFromCollapsed()
        }

        // 设置悬停展开
        collapsedView.setOnHoverListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    expandFromCollapsed()
                    true
                }
                else -> false
            }
        }

        // 设置拖动行为
        setupCollapsedDragBehavior(collapsedView)

        return collapsedView
    }

    /**
     * 设置收缩视图的拖动行为
     */
    private fun setupCollapsedDragBehavior(view: View) {
        val collapsedParams = collapsedParams ?: return
        var isDragging = false
        val displayMetrics = resources.displayMetrics
        val viewSize = (COLLAPSED_SIZE * displayMetrics.density).toInt()
        val margin = (0 * displayMetrics.density).toInt() // 允许超出屏幕 0dp

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = collapsedParams.x
                    initialY = collapsedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isUserInteracting = true
                    idleTimer.removeCallbacksAndMessages(null)
                    false // 不消耗事件，让 click 事件也能触发
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    // 移动超过阈值才视为拖动
                    if (deltaX > 10 || deltaY > 10) {
                        isDragging = true
                        var newX = initialX + (event.rawX - initialTouchX).toInt()
                        var newY = initialY + (event.rawY - initialTouchY).toInt()

                        // 限制在屏幕范围内，允许超出 10dp
                        newX = newX.coerceIn(-margin, screenWidth - viewSize + margin)
                        newY = newY.coerceIn(0, screenHeight - viewSize) // Y 轴不超出屏幕

                        collapsedParams.x = newX
                        collapsedParams.y = newY
                        windowManager.updateViewLayout(view, collapsedParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserInteracting = false
                    if (isDragging) {
                        // 拖动释放后吸附边缘
                        snapCollapsedToEdge(collapsedParams, margin)
                    } else {
                        // 点击展开面板
                        expandFromCollapsed()
                    }
                    // 重新启动计时器
                    startAutoCollapseTimer()
                    true
                }
                else -> false
            }
        }

        // 移除原来的点击监听器
        view.setOnClickListener(null)
        view.setOnHoverListener(null)
    }

    /**
     * 收缩视图边缘吸附
     */
    private fun snapCollapsedToEdge(params: WindowManager.LayoutParams, margin: Int = 0) {
        val displayMetrics = resources.displayMetrics
        val halfScreen = screenWidth / 2
        val viewSize = (COLLAPSED_SIZE * displayMetrics.density).toInt()

        val targetX = if (params.x < halfScreen) {
            0
        } else {
            screenWidth - viewSize
        }

        if (Math.abs(params.x - targetX) < 50 * displayMetrics.density) {
            params.x = targetX
            windowManager.updateViewLayout(collapsedView, params)
        }
    }

    /**
     * 收缩为侧边栏
     */
    private fun collapseToSidebar() {
        val currentParams = params ?: return
        if (isCollapsed) return

        // 获取当前视图的测量宽度和Y位置
        floatView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val panelWidth = floatView?.measuredWidth ?: (200 * resources.displayMetrics.density).toInt()
        val currentY = currentParams.y

        // 计算贴边位置（左边贴边或右边贴边）
        val displayMetrics = resources.displayMetrics
        val collapsedSizePx = (COLLAPSED_SIZE * displayMetrics.density).toInt()

        // 判断应该贴哪边
        val attachToRight = currentParams.x > screenWidth / 2

        // 创建侧边栏窗口参数
        collapsedParams = WindowManager.LayoutParams(
            collapsedSizePx,
            collapsedSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 右侧停靠时，x = screenWidth - viewSize
            x = if (attachToRight) screenWidth - collapsedSizePx else 0
            y = currentY
        }

        // 创建侧边栏视图
        collapsedView = createCollapsedView()

        // 先添加侧边栏
        windowManager.addView(collapsedView, collapsedParams)

        // 移除展开的视图
        windowManager.removeView(floatView)

        isCollapsed = true
        idleTimer.removeCallbacksAndMessages(null)
    }

    /**
     * 从侧边栏展开
     */
    private fun expandFromCollapsed() {
        if (!isCollapsed) return
        isCollapsed = false

        // 移除侧边栏
        collapsedView?.let {
            windowManager.removeView(it)
            collapsedView = null
        }

        // 恢复展开视图的位置
        val displayMetrics = resources.displayMetrics
        params?.apply {
            x = (100 * displayMetrics.density).toInt()
            y = (200 * displayMetrics.density).toInt()
        }

        // 重新添加展开视图
        windowManager.addView(floatView, params)

        // 重新启动自动收缩计时器
        startAutoCollapseTimer()
    }

    /**
     * 启动自动收缩计时器
     */
    private fun startAutoCollapseTimer() {
        if (!isAutoCollapsing) return
        // 只有贴边时才启动自动收缩计时器
        if (!isDocked()) return
        idleTimer.removeCallbacksAndMessages(null)
        idleTimer.postDelayed({
            if (!isUserInteracting && !isCollapsed && isDocked()) {
                collapseToSidebar()
            }
        }, autoCollapseDelay)
    }

    /**
     * 检查是否贴边（左侧贴左边缘或右侧贴右边缘）
     */
    private fun isDocked(): Boolean {
        val currentParams = params ?: return false
        floatView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val panelWidth = floatView?.measuredWidth ?: (200 * resources.displayMetrics.density).toInt()
        val edgeThreshold = (16 * resources.displayMetrics.density).toInt() // 16dp容差

        val dockedToLeft = currentParams.x <= edgeThreshold
        val dockedToRight = currentParams.x >= screenWidth - panelWidth - edgeThreshold
        return dockedToLeft || dockedToRight
    }

    /**
     * 监听视图位置变化
     */
    private fun observeViewPosition() {
        val view = floatView ?: return

        view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (isFirstPositionUpdate && !isCollapsed) {
                    isFirstPositionUpdate = false
                    // 初始位置检查，贴边时启动5秒后自动收缩
                    startAutoCollapseTimer()
                }
            }
        })
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatWindow() {
        // 清除计时器
        idleTimer.removeCallbacksAndMessages(null)

        // 移除展开视图
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }

        // 移除侧边栏视图
        collapsedView?.let {
            windowManager.removeView(it)
            collapsedView = null
        }

        isCollapsed = false
        stopSelf()
    }

    /**
     * 加载收藏的工作流
     */
    private suspend fun loadFavoriteWorkflows() {
        withContext(Dispatchers.IO) {
            favoriteWorkflows.clear()
            favoriteWorkflows.addAll(
                workflowManager.getAllWorkflows().filter { it.isFavorite }
            )
        }
    }

    /**
     * 更新空状态显示
     */
    private fun updateEmptyState() {
        val emptyState = floatView?.findViewById<LinearLayout>(R.id.empty_state)
        val recyclerView = floatView?.findViewById<RecyclerView>(R.id.recycler_view_workflows)

        if (favoriteWorkflows.isEmpty()) {
            emptyState?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyState?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    /**
     * 设置拖拽行为
     */
    private fun setupDragBehavior(view: View, params: WindowManager.LayoutParams) {
        val dragHandle = view.findViewById<LinearLayout>(R.id.drag_handle)
        val dragIndicator = view.findViewById<ImageView>(R.id.drag_indicator)

        val touchListener = object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isUserInteracting = true
                        idleTimer.removeCallbacksAndMessages(null)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isUserInteracting = false
                        // 边缘吸附
                        snapToEdge(params, view)
                        // 重新启动计时器
                        startAutoCollapseTimer()
                        return true
                    }
                    else -> return false
                }
            }
        }

        dragIndicator.setOnTouchListener(touchListener)
        dragHandle.setOnTouchListener(touchListener)
    }

    /**
     * 边缘吸附
     */
    private fun snapToEdge(params: WindowManager.LayoutParams, view: View) {
        val displayMetrics = resources.displayMetrics
        val halfScreen = screenWidth / 2
        val panelWidth = view.measuredWidth

        // 判断贴左边还是右边
        val targetX = if (params.x < halfScreen) {
            0 // 贴左边
        } else {
            screenWidth - panelWidth // 贴右边
        }

        // 如果已经接近边缘，不需要移动
        if (Math.abs(params.x - targetX) < 50 * displayMetrics.density) {
            params.x = targetX
            windowManager.updateViewLayout(view, params)
        }
    }

    /**
     * 执行工作流
     */
    private fun executeWorkflow(workflow: Workflow) {
        // 重新加载工作流最新版本，避免使用旧缓存
        val latestWorkflow = workflowManager.getWorkflow(workflow.id)
        if (latestWorkflow == null) {
            Toast.makeText(this, "工作流不存在", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "执行: ${latestWorkflow.name}", Toast.LENGTH_SHORT).show()
        WorkflowExecutor.execute(latestWorkflow, this)
        adapter.notifyDataSetChanged()
    }

    /**
     * 停止工作流
     */
    private fun stopWorkflow(workflow: Workflow) {
        Toast.makeText(this, "停止: ${workflow.name}", Toast.LENGTH_SHORT).show()
        WorkflowExecutor.stopExecution(workflow.id)
        adapter.notifyDataSetChanged()
    }

    /**
     * 订阅执行状态更新
     */
    private fun observeExecutionState() {
        serviceScope.launch {
            com.chaomixian.vflow.core.execution.ExecutionStateBus.stateFlow.collectLatest { state ->
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatWindow()
    }
}
