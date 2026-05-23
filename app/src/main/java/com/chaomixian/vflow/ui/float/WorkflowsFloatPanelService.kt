package com.chaomixian.vflow.ui.float

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowsFloatPanelService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var workflowManager: WorkflowManager
    private lateinit var adapter: WorkflowFloatPanelAdapter
    private val favoriteWorkflows = mutableListOf<Workflow>()

    private var floatView: View? = null
    private var collapsedView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isCollapsed = false
    private var isAutoCollapsing = true
    private val autoCollapseDelay = 3000L
    private val idleTimer = Handler(Looper.getMainLooper())
    private var isUserInteracting = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var params: WindowManager.LayoutParams? = null
    private var collapsedParams: WindowManager.LayoutParams? = null
    private var isFirstPositionUpdate = true

    private var closeHoldAnimator: ValueAnimator? = null
    private var isCloseHoldActive = false

    companion object {
        const val ACTION_SHOW = "com.chaomixian.vflow.ACTION_SHOW_FLOAT_PANEL"
        const val ACTION_HIDE = "com.chaomixian.vflow.ACTION_HIDE_FLOAT_PANEL"
        private const val COLLAPSED_SIZE = 36
        private const val CLOSE_HOLD_DURATION_MS = 2000L
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
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

    private fun showFloatWindow() {
        if (floatView != null) return

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val themedContext = ThemeUtils.createThemedContext(this)
        floatView = LayoutInflater.from(themedContext).inflate(R.layout.workflows_float_panel, null)

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

        setupDragBehavior(floatView!!, params!!)

        floatView?.findViewById<MaterialButton>(R.id.btn_minimize)?.setOnClickListener {
            collapseToSidebar()
        }

        setupCloseHoldBehavior()

        val recyclerView = floatView?.findViewById<RecyclerView>(R.id.recycler_view_workflows)
        recyclerView?.layoutManager = LinearLayoutManager(this)
        adapter = WorkflowFloatPanelAdapter(
            workflows = favoriteWorkflows,
            onExecute = { workflow -> executeWorkflow(workflow) },
            onStop = { workflow -> stopWorkflow(workflow) }
        )
        recyclerView?.adapter = adapter

        serviceScope.launch {
            loadFavoriteWorkflows()
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }

        windowManager.addView(floatView, params)
        observeExecutionState()
        startAutoCollapseTimer()
        observeViewPosition()
    }

    private fun setupCloseHoldBehavior() {
        val closeButton = floatView?.findViewById<MaterialButton>(R.id.btn_close) ?: return
        val closeOverlay = floatView?.findViewById<View>(R.id.close_hold_overlay) ?: return
        val closeProgress = floatView?.findViewById<CircularProgressIndicator>(R.id.close_hold_progress) ?: return

        closeButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    startCloseHold(closeOverlay, closeProgress)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val inside = event.x >= 0f &&
                        event.y >= 0f &&
                        event.x <= v.width &&
                        event.y <= v.height
                    if (!inside && isCloseHoldActive) {
                        cancelCloseHold(closeOverlay, closeProgress)
                    } else if (inside && !isCloseHoldActive) {
                        startCloseHold(closeOverlay, closeProgress)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelCloseHold(closeOverlay, closeProgress)
                    true
                }
                else -> false
            }
        }
    }

    private fun startCloseHold(overlay: View, progressIndicator: CircularProgressIndicator) {
        if (isCloseHoldActive) return
        isCloseHoldActive = true
        overlay.isVisible = true
        progressIndicator.progress = 0
        closeHoldAnimator?.cancel()
        closeHoldAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = CLOSE_HOLD_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                progressIndicator.progress = animator.animatedValue as Int
            }
            doOnEnd {
                if (isCloseHoldActive) {
                    hideFloatWindow()
                }
            }
            start()
        }
    }

    private fun cancelCloseHold(overlay: View, progressIndicator: CircularProgressIndicator) {
        isCloseHoldActive = false
        closeHoldAnimator?.cancel()
        closeHoldAnimator = null
        overlay.isVisible = false
        progressIndicator.progress = 0
    }

    private fun createCollapsedView(): View {
        val themedContext = ThemeUtils.createThemedContext(this)
        val collapsed = LayoutInflater.from(themedContext).inflate(R.layout.workflows_float_panel_collapsed, null)
        collapsed.setOnClickListener { expandFromCollapsed() }
        setupCollapsedDragBehavior(collapsed)
        return collapsed
    }

    private fun setupCollapsedDragBehavior(view: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var isDragging = false
        val collapsedLayoutParams = collapsedParams ?: return
        val displayMetrics = resources.displayMetrics
        val viewSize = (COLLAPSED_SIZE * displayMetrics.density).toInt()

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = collapsedLayoutParams.x
                    initialY = collapsedLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isUserInteracting = true
                    idleTimer.removeCallbacksAndMessages(null)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        collapsedLayoutParams.x = (initialX + deltaX.toInt()).coerceIn(0, screenWidth - viewSize)
                        collapsedLayoutParams.y = (initialY + deltaY.toInt()).coerceIn(0, screenHeight - viewSize)
                        windowManager.updateViewLayout(view, collapsedLayoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserInteracting = false
                    if (isDragging) {
                        snapCollapsedToEdge(collapsedLayoutParams)
                    } else {
                        expandFromCollapsed()
                    }
                    startAutoCollapseTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun collapseToSidebar() {
        val currentParams = params ?: return
        if (isCollapsed) return

        val currentY = currentParams.y
        val displayMetrics = resources.displayMetrics
        val collapsedSizePx = (COLLAPSED_SIZE * displayMetrics.density).toInt()
        val attachToRight = currentParams.x > screenWidth / 2

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
            x = if (attachToRight) screenWidth - collapsedSizePx else 0
            y = currentY
        }

        collapsedView = createCollapsedView()
        windowManager.addView(collapsedView, collapsedParams)
        floatView?.let { windowManager.removeView(it) }

        isCollapsed = true
        idleTimer.removeCallbacksAndMessages(null)
    }

    private fun expandFromCollapsed() {
        if (!isCollapsed) return
        isCollapsed = false

        collapsedView?.let {
            windowManager.removeView(it)
            collapsedView = null
        }

        val displayMetrics = resources.displayMetrics
        params?.apply {
            x = (100 * displayMetrics.density).toInt()
            y = (200 * displayMetrics.density).toInt()
        }

        floatView?.let { windowManager.addView(it, params) }
        startAutoCollapseTimer()
    }

    private fun startAutoCollapseTimer() {
        if (!isAutoCollapsing) return
        if (!isDocked()) return
        idleTimer.removeCallbacksAndMessages(null)
        idleTimer.postDelayed({
            if (!isUserInteracting && !isCollapsed && isDocked()) {
                collapseToSidebar()
            }
        }, autoCollapseDelay)
    }

    private fun isDocked(): Boolean {
        val currentParams = params ?: return false
        floatView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val panelWidth = floatView?.measuredWidth ?: (200 * resources.displayMetrics.density).toInt()
        val edgeThreshold = (16 * resources.displayMetrics.density).toInt()
        val dockedToLeft = currentParams.x <= edgeThreshold
        val dockedToRight = currentParams.x >= screenWidth - panelWidth - edgeThreshold
        return dockedToLeft || dockedToRight
    }

    private fun observeViewPosition() {
        val view = floatView ?: return
        view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (isFirstPositionUpdate && !isCollapsed) {
                    isFirstPositionUpdate = false
                    startAutoCollapseTimer()
                }
            }
        })
    }

    private fun hideFloatWindow() {
        idleTimer.removeCallbacksAndMessages(null)

        floatView?.findViewById<View>(R.id.close_hold_overlay)?.let { overlay ->
            floatView?.findViewById<CircularProgressIndicator>(R.id.close_hold_progress)?.let { progress ->
                cancelCloseHold(overlay, progress)
            }
        }

        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
        collapsedView?.let {
            windowManager.removeView(it)
            collapsedView = null
        }

        isCollapsed = false
        stopSelf()
    }

    private suspend fun loadFavoriteWorkflows() {
        withContext(Dispatchers.IO) {
            favoriteWorkflows.clear()
            favoriteWorkflows.addAll(workflowManager.getAllWorkflows().filter { it.isFavorite })
        }
    }

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

    private fun setupDragBehavior(view: View, layoutParams: WindowManager.LayoutParams) {
        val dragHandle = view.findViewById<LinearLayout>(R.id.drag_handle)
        val dragIndicator = view.findViewById<ImageView>(R.id.drag_indicator)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var isDragging = false

        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isUserInteracting = true
                    idleTimer.removeCallbacksAndMessages(null)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserInteracting = false
                    if (isDragging) {
                        snapToEdge(layoutParams, view)
                    }
                    startAutoCollapseTimer()
                    true
                }
                else -> false
            }
        }

        dragIndicator.setOnTouchListener(touchListener)
        dragHandle.setOnTouchListener(touchListener)
    }

    private fun snapToEdge(layoutParams: WindowManager.LayoutParams, view: View) {
        val displayMetrics = resources.displayMetrics
        val halfScreen = screenWidth / 2
        val panelWidth = view.measuredWidth
        val targetX = if (layoutParams.x < halfScreen) 0 else screenWidth - panelWidth
        if (kotlin.math.abs(layoutParams.x - targetX) < 50 * displayMetrics.density) {
            layoutParams.x = targetX
            windowManager.updateViewLayout(view, layoutParams)
        }
    }

    private fun snapCollapsedToEdge(layoutParams: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val halfScreen = screenWidth / 2
        val viewSize = (COLLAPSED_SIZE * displayMetrics.density).toInt()
        val targetX = if (layoutParams.x < halfScreen) 0 else screenWidth - viewSize
        if (kotlin.math.abs(layoutParams.x - targetX) < 50 * displayMetrics.density) {
            layoutParams.x = targetX
            collapsedView?.let { windowManager.updateViewLayout(it, layoutParams) }
        }
    }

    private fun executeWorkflow(workflow: Workflow) {
        val latestWorkflow = workflowManager.getWorkflow(workflow.id)
        if (latestWorkflow == null) {
            Toast.makeText(this, getString(R.string.workflow_not_exists), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.toast_starting_workflow, latestWorkflow.name), Toast.LENGTH_SHORT).show()
        WorkflowExecutor.execute(
            workflow = latestWorkflow,
            context = this,
            triggerStepId = latestWorkflow.manualTrigger()?.id
        )
        adapter.notifyDataSetChanged()
    }

    private fun stopWorkflow(workflow: Workflow) {
        Toast.makeText(this, getString(R.string.home_stopped_execution, workflow.name), Toast.LENGTH_SHORT).show()
        WorkflowExecutor.stopExecution(workflow.id)
        adapter.notifyDataSetChanged()
    }

    private fun observeExecutionState() {
        serviceScope.launch {
            ExecutionStateBus.stateFlow.collectLatest {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        idleTimer.removeCallbacksAndMessages(null)
        closeHoldAnimator?.cancel()
        super.onDestroy()
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
            override fun onAnimationCancel(animation: android.animation.Animator) = Unit
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
        })
    }
}
