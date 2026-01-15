// 文件：WorkflowsFloatPanelService.kt
// 描述：工作流快速控制悬浮面板服务

package com.chaomixian.vflow.ui.float

import android.app.Service
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
    private lateinit var workflowManager: WorkflowManager
    private lateinit var adapter: WorkflowFloatPanelAdapter
    private var favoriteWorkflows = mutableListOf<Workflow>()

    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        const val ACTION_SHOW = "com.chaomixian.vflow.ACTION_SHOW_FLOAT_PANEL"
        const val ACTION_HIDE = "com.chaomixian.vflow.ACTION_HIDE_FLOAT_PANEL"
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

        // 创建带主题的 Context（根据用户设置选择动态取色或默认主题）
        val themedContext = ThemeUtils.createThemedContext(this)

        // 创建悬浮窗视图
        floatView = LayoutInflater.from(themedContext).inflate(R.layout.workflows_float_panel, null)

        // 设置窗口参数
        val params = WindowManager.LayoutParams(
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
            x = 100
            y = 200
        }

        // 设置拖拽
        setupDragBehavior(floatView!!, params)

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
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatWindow() {
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
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

        dragIndicator.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    else -> return false
                }
            }
        })

        // 整个标题栏也可以拖拽
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    else -> return false
                }
            }
        })
    }

    /**
     * 执行工作流
     */
    private fun executeWorkflow(workflow: Workflow) {
        Toast.makeText(this, "执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
        WorkflowExecutor.execute(workflow, this)
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
