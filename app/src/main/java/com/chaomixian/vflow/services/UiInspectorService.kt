// 文件: main/java/com/chaomixian/vflow/services/UiInspectorService.kt
package com.chaomixian.vflow.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.chaomixian.vflow.ui.workflow_editor.inspector.WorkflowInspectorInsertRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * UI 检查器服务
 */
class UiInspectorService : Service() {
    companion object {
        private const val INSPECT_THROTTLE_MS = 32L
        private const val COORD_LABEL_MARGIN_DP = 8
    }

    private lateinit var windowManager: WindowManager
    private var floatIconView: ImageView? = null
    private var coordinateLabelView: TextView? = null
    private var selectionFrameView: View? = null

    private lateinit var iconParams: WindowManager.LayoutParams
    private lateinit var coordinateLabelParams: WindowManager.LayoutParams
    private lateinit var frameParams: WindowManager.LayoutParams

    private var currentNodeInfo: AccessibilityNodeInfo? = null
    private var currentRootNode: AccessibilityNodeInfo? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var currentProbeX = 0
    private var currentProbeY = 0
    private var pendingInspectX = 0
    private var pendingInspectY = 0
    private var isInspectScheduled = false
    private var enableWorkflowInsert = false
    private val inspectRunnable = Runnable {
        isInspectScheduled = false
        performNodeInspection(pendingInspectX, pendingInspectY)
    }

    // 用于列表展示的数据结构
    private sealed class InspectorItem {
        data class Header(val title: String) : InspectorItem()
        data class Property(
            val label: String,
            val value: String,
            val insertRequest: WorkflowInspectorInsertRequest? = null
        ) : InspectorItem()
    }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val context = LocaleManager.applyLanguage(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enableWorkflowInsert = intent?.getBooleanExtra(
            WorkflowInspectorInsertRequest.EXTRA_ENABLE_WORKFLOW_INSERT,
            false
        ) == true
        Toast.makeText(this, getString(R.string.ui_inspector_service_started), Toast.LENGTH_SHORT).show()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            updateScreenMetrics()
            createFloatIcon()
            createSelectionFrame()
            DebugLogger.d("UiInspector", "UI Inspector Created")
        } catch (e: Exception) {
            DebugLogger.e("UiInspector", "Error creating UI Inspector", e)
            Toast.makeText(this, getString(R.string.ui_inspector_start_failed, e.message), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun updateScreenMetrics() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun createSelectionFrame() {
        val themedContext = ThemeUtils.createThemedContext(this)
        val strokeColor = resolveThemeColor(themedContext, android.R.attr.colorError)

        selectionFrameView = View(this).apply {
            background = GradientDrawable().apply {
                setStroke(8, strokeColor)
                setColor(Color.TRANSPARENT)
                cornerRadius = 24f
            }
            visibility = View.GONE
        }

        frameParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = 0
            height = 0
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(selectionFrameView, frameParams)
        } catch (e: Exception) { }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatIcon() {
        // 使用 ThemeUtils 确保能获取到 Material 3 动态取色
        val themedContext = ThemeUtils.createThemedContext(this)

        val containerColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorPrimaryContainer)
        val iconColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val surfaceColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorOnSurface)

        coordinateLabelView = TextView(themedContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextColor(onSurfaceColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1), containerColor)
            }
            elevation = 22f

            val maxCoordLabel = "$screenWidth,$screenHeight"
            minWidth = ceil(paint.measureText(maxCoordLabel)).toInt() + paddingLeft + paddingRight

            isClickable = true
            isFocusable = false
            setOnClickListener {
                copyToClipboard(
                    getString(R.string.ui_inspector_current_coordinate),
                    "$currentProbeX,$currentProbeY"
                )
            }
        }

        coordinateLabelParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        floatIconView = ImageView(themedContext).apply {
            setImageResource(R.drawable.rounded_architecture_24)
            setBackgroundResource(R.drawable.bg_widget_rounded)
            background.setTint(containerColor)
            setColorFilter(iconColor)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            elevation = 20f
        }

        iconParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L
        val LONG_PRESS_TIMEOUT = 500L // 长按阈值（毫秒）

        floatIconView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = iconParams.x
                    initialY = iconParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()

                    val service = ServiceStateBus.getAccessibilityService()
                    currentRootNode = service?.rootInActiveWindow
                    if (service == null) {
                        Toast.makeText(this, getString(R.string.ui_inspector_enable_accessibility), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }

                    iconParams.x = initialX + dx
                    iconParams.y = initialY + dy
                    windowManager.updateViewLayout(floatIconView, iconParams)

                    scheduleInspectionAtCurrentIconCenter()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flushPendingInspection()
                    val pressDuration = System.currentTimeMillis() - downTime
                    if (!isDragging && pressDuration >= LONG_PRESS_TIMEOUT) {
                        // 长按：关闭服务
                        stopSelf()
                    } else if (!isDragging) {
                        // 短按：显示详情
                        inspectCurrentIconCenter()
                        showNodeDetails()
                    }
                    currentRootNode = null
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(coordinateLabelView, coordinateLabelParams)
            windowManager.addView(floatIconView, iconParams)
            floatIconView?.post { refreshCurrentProbeCoordinate() }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.ui_inspector_float_permission_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun inspectCurrentIconCenter() {
        if (!refreshCurrentProbeCoordinate()) return
        inspectNodeAt(currentProbeX, currentProbeY)
    }

    private fun scheduleInspectionAtCurrentIconCenter() {
        if (!refreshCurrentProbeCoordinate()) return
        pendingInspectX = currentProbeX
        pendingInspectY = currentProbeY
        if (isInspectScheduled) return
        isInspectScheduled = true
        floatIconView?.postDelayed(inspectRunnable, INSPECT_THROTTLE_MS)
    }

    private fun flushPendingInspection() {
        floatIconView?.removeCallbacks(inspectRunnable)
        if (isInspectScheduled) {
            isInspectScheduled = false
            performNodeInspection(pendingInspectX, pendingInspectY)
        }
    }

    private fun refreshCurrentProbeCoordinate(): Boolean {
        val iconView = floatIconView ?: return false
        if (iconView.width == 0 || iconView.height == 0) return false

        currentProbeX = iconParams.x + iconView.width / 2
        currentProbeY = iconParams.y + iconView.height / 2
        updateCurrentCoordinateLabel(currentProbeX, currentProbeY)
        return true
    }

    private fun updateCurrentCoordinateLabel(x: Int, y: Int) {
        val newLabel = "$x,$y"
        val labelView = coordinateLabelView ?: return

        if (labelView.text != newLabel) {
            labelView.text = newLabel
        }

        val labelWidth = labelView.width
        val labelHeight = labelView.height
        if (labelWidth <= 0 || labelHeight <= 0) {
            labelView.post { updateCurrentCoordinateLabel(x, y) }
            return
        }

        coordinateLabelParams.x = x - labelWidth / 2
        coordinateLabelParams.y = iconParams.y - labelHeight - dp(COORD_LABEL_MARGIN_DP)
        try {
            windowManager.updateViewLayout(labelView, coordinateLabelParams)
        } catch (_: Exception) { }
    }

    private fun inspectNodeAt(x: Int, y: Int) {
        performNodeInspection(x, y)
    }

    private fun performNodeInspection(x: Int, y: Int) {
        val root = currentRootNode ?: return
        val targetNode = findSmallestNodeAtPoint(root, x, y)

        if (targetNode != null && targetNode != currentNodeInfo) {
            currentNodeInfo = targetNode
            val rect = Rect()
            targetNode.getBoundsInScreen(rect)

            frameParams.x = rect.left
            frameParams.y = rect.top
            frameParams.width = rect.width()
            frameParams.height = rect.height()

            selectionFrameView?.visibility = View.VISIBLE
            try {
                windowManager.updateViewLayout(selectionFrameView, frameParams)
            } catch (e: Exception) { }
        }
    }

    private fun findSmallestNodeAtPoint(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 检查点是否在节点范围内
        if (!bounds.contains(x, y)) return null

        // 收集所有包含该点的可见子节点
        val validChildren = mutableListOf<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            // 只考虑可见的节点
            if (child.isVisibleToUser) {
                val found = findSmallestNodeAtPoint(child, x, y)
                if (found != null) {
                    validChildren.add(found)
                }
            }
        }

        // 如果有有效的子节点，返回面积最小的那个
        if (validChildren.isNotEmpty()) {
            return validChildren.minByOrNull {
                val rect = Rect()
                it.getBoundsInScreen(rect)
                rect.width() * rect.height()
            }
        }

        // 如果没有子节点，检查当前节点是否可见
        return if (node.isVisibleToUser) node else null
    }

    // --- 弹窗显示逻辑 ---

    private fun showNodeDetails() {
        val node = currentNodeInfo ?: run {
            Toast.makeText(this, getString(R.string.ui_inspector_no_selection), Toast.LENGTH_SHORT).show()
            return
        }

        val items = collectNodeInfo(node)

        val dialogContext = ThemeUtils.createThemedContext(this)

        val detailsView = createDetailsListView(dialogContext, items)

        val builder = MaterialAlertDialogBuilder(dialogContext)
            .setView(detailsView)
            .setPositiveButton(getString(R.string.ui_inspector_copy_all)) { _, _ ->
                val fullText = items.joinToString("\n") { item ->
                    when(item) {
                        is InspectorItem.Header -> "\n=== ${item.title} ==="
                        is InspectorItem.Property -> "${item.label}: ${item.value}"
                    }
                }
                copyToClipboard("Full Details", fullText)
            }
            .setNeutralButton(getString(R.string.ui_inspector_close_service)) { _, _ -> stopSelf() }
            .setNegativeButton(getString(R.string.ui_inspector_close), null)

        lateinit var dialog: androidx.appcompat.app.AlertDialog
        if (enableWorkflowInsert) {
            builder.setCustomTitle(
                createDialogTitleView(dialogContext, getString(R.string.ui_inspector_properties_title)) {
                    sendInsertRequest(buildClickInsertRequest(node))
                    dialog.dismiss()
                }
            )
        } else {
            builder.setTitle(getString(R.string.ui_inspector_properties_title))
        }

        dialog = builder.create()

        dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE)

        dialog.show()
    }

    private fun collectNodeInfo(node: AccessibilityNodeInfo): List<InspectorItem> {
        val list = mutableListOf<InspectorItem>()
        val rect = Rect()
        node.getBoundsInScreen(rect)
        refreshCurrentProbeCoordinate()

        val packageName = node.packageName?.toString() ?: getString(R.string.ui_inspector_unknown)
        var appName = getString(R.string.ui_inspector_unknown)
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { }

        val currentActivity = ServiceStateBus.lastWindowClassName ?: getString(R.string.ui_inspector_unknown)
        val currentAppInsertRequest = buildLaunchAppInsertRequest(packageName)
        val currentPageInsertRequest = buildLaunchActivityInsertRequest(packageName, currentActivity)
        val controlId = node.viewIdResourceName ?: getString(R.string.ui_inspector_none)
        val controlText = node.text?.toString() ?: ""
        val gkdSelector = generateGkdSelector(node)
        val topLeftCoordinate = "${rect.left},${rect.top}"
        val bottomRightCoordinate = "${rect.right},${rect.bottom}"
        val centerCoordinate = "${rect.centerX()},${rect.centerY()}"
        val currentCoordinate = "$currentProbeX,$currentProbeY"

        list.add(InspectorItem.Header(getString(R.string.ui_inspector_basic_info)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_current_app), "$appName ($packageName)", currentAppInsertRequest))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_current_page), currentActivity, currentPageInsertRequest))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_control_id), controlId, buildClickInsertRequest(controlId)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_control_text), controlText, buildFindTextInsertRequest(controlText)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_control_type), node.className?.toString() ?: getString(R.string.ui_inspector_unknown)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_gkd_selector), gkdSelector, buildUiSelectorInsertRequest(gkdSelector)))

        list.add(InspectorItem.Header(getString(R.string.ui_inspector_layout_coordinates)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_current_coordinate), currentCoordinate, WorkflowInspectorInsertRequest.click(currentCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_top_left), topLeftCoordinate, WorkflowInspectorInsertRequest.click(topLeftCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_bottom_right), bottomRightCoordinate, WorkflowInspectorInsertRequest.click(bottomRightCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_center_point), centerCoordinate, WorkflowInspectorInsertRequest.click(centerCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_coord_x), rect.left.toString(), WorkflowInspectorInsertRequest.click(topLeftCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_coord_y), rect.top.toString(), WorkflowInspectorInsertRequest.click(topLeftCoordinate)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_width), rect.width().toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_height), rect.height().toString()))

        list.add(InspectorItem.Header(getString(R.string.ui_inspector_hierarchy)))
        val directChildCount = node.childCount
        val (totalChildCount, childTexts) = countAllChildrenAndText(node)
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_child_count), directChildCount.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_total_children), totalChildCount.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_child_text), if(childTexts.length > 300) childTexts.take(300) + "..." else childTexts))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_depth), calculateDepth(node).toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_parent), node.parent?.className?.toString() ?: getString(R.string.ui_inspector_none)))

        list.add(InspectorItem.Header(getString(R.string.ui_inspector_state)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_clickable), node.isClickable.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_long_clickable), node.isLongClickable.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_scrollable), node.isScrollable.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_checkable), node.isCheckable.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_checked), node.isChecked.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_focusable), node.isFocusable.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_focused), node.isFocused.toString()))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_visibility), if(node.isVisibleToUser) getString(R.string.ui_inspector_visible) else getString(R.string.ui_inspector_invisible)))
        list.add(InspectorItem.Property(getString(R.string.ui_inspector_enabled_status), node.isEnabled.toString()))

        return list
    }

    private fun createDialogTitleView(
        context: Context,
        title: String,
        onInsertClick: () -> Unit
    ): View {
        val titleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(4))
        }

        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val insertButton = ImageButton(context).apply {
            setImageResource(R.drawable.rounded_variable_insert_24)
            val backgroundDrawable = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dp(14).toFloat())
                    .build()
            ).apply {
                initializeElevationOverlay(context)
                fillColor = ColorStateList.valueOf(
                    resolveThemeColor(context, com.google.android.material.R.attr.colorSecondaryContainer)
                )
            }
            background = RippleDrawable(
                ColorStateList.valueOf(
                    resolveThemeColor(context, android.R.attr.colorControlHighlight)
                ),
                backgroundDrawable,
                null
            )
            imageTintList = ColorStateList.valueOf(
                resolveThemeColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
            scaleType = ImageView.ScaleType.CENTER
            minimumWidth = dp(40)
            minimumHeight = dp(40)
            contentDescription = context.getString(R.string.ui_inspector_insert_click_module)
            setOnClickListener { onInsertClick() }
        }

        titleContainer.addView(titleView)
        titleContainer.addView(insertButton)
        return titleContainer
    }

    private fun buildClickInsertRequest(node: AccessibilityNodeInfo): WorkflowInspectorInsertRequest {
        return WorkflowInspectorInsertRequest.click(buildClickTarget(node))
    }

    private fun buildClickInsertRequest(target: String): WorkflowInspectorInsertRequest? {
        if (!isInsertableValue(target)) return null
        return WorkflowInspectorInsertRequest.click(target)
    }

    private fun buildUiSelectorInsertRequest(selector: String): WorkflowInspectorInsertRequest? {
        if (!isInsertableValue(selector)) return null
        return WorkflowInspectorInsertRequest.uiSelector(selector)
    }

    private fun buildFindTextInsertRequest(text: String): WorkflowInspectorInsertRequest? {
        if (!isInsertableValue(text)) return null
        return WorkflowInspectorInsertRequest.findText(text)
    }

    private fun buildLaunchAppInsertRequest(packageName: String): WorkflowInspectorInsertRequest? {
        if (!isInsertableValue(packageName)) return null
        return WorkflowInspectorInsertRequest.launchApp(packageName)
    }

    private fun buildLaunchActivityInsertRequest(
        packageName: String,
        activityName: String
    ): WorkflowInspectorInsertRequest? {
        if (!isInsertableValue(packageName) || !isInsertableValue(activityName)) return null
        return WorkflowInspectorInsertRequest.launchActivity(packageName, activityName)
    }

    private fun sendInsertRequest(request: WorkflowInspectorInsertRequest?) {
        if (!enableWorkflowInsert || request == null) return
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(WorkflowInspectorInsertRequest.ACTION_INSERT_REQUEST).apply {
                putExtra(WorkflowInspectorInsertRequest.EXTRA_INSERT_REQUEST, request)
            }
        )
        Toast.makeText(this, R.string.ui_inspector_insert_request_sent, Toast.LENGTH_SHORT).show()
    }

    private fun buildClickTarget(node: AccessibilityNodeInfo): String {
        val viewId = node.viewIdResourceName?.takeIf { it.isNotBlank() }
        if (viewId != null) {
            return viewId
        }
        refreshCurrentProbeCoordinate()
        return "$currentProbeX,$currentProbeY"
    }

    private fun isInsertableValue(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        return trimmed != getString(R.string.ui_inspector_unknown) &&
                trimmed != getString(R.string.ui_inspector_none)
    }

    /**
     * 辅助函数：解析当前主题中的颜色属性
     */
    private fun resolveThemeColor(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    private fun createDetailsListView(context: Context, items: List<InspectorItem>): View {
        val scrollView = ScrollView(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        for (item in items) {
            val itemView = when (item) {
                is InspectorItem.Header -> createHeaderView(context, item.title)
                is InspectorItem.Property -> createPropertyRowView(context, item)
            }
            container.addView(itemView)
        }

        scrollView.addView(container)
        return scrollView
    }

    private fun createHeaderView(context: Context, title: String): View {
        return TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)

            // 使用 Primary 颜色
            setTextColor(resolveThemeColor(context, android.R.attr.colorPrimary))

            setPadding(48, 32, 48, 16)
        }
    }

    private fun createPropertyRowView(context: Context, item: InspectorItem.Property): View {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // 添加点击波纹效果
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            isClickable = true
            setOnClickListener { copyToClipboard(item.label, item.value) }
            if (enableWorkflowInsert && item.insertRequest != null) {
                isLongClickable = true
                setOnLongClickListener {
                    sendInsertRequest(item.insertRequest)
                    true
                }
            }
            setPadding(48, 16, 24, 16)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 标签
        val labelView = TextView(context).apply {
            text = item.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }

        // 值
        val valueView = TextView(context).apply {
            text = item.value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
            typeface = Typeface.MONOSPACE
        }

        textContainer.addView(labelView)
        textContainer.addView(valueView)

        // 复制按钮
        val copyButton = ImageButton(context).apply {
            setImageResource(R.drawable.rounded_content_copy_24)
            // 添加波纹效果
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            imageTintList = ColorStateList.valueOf(resolveThemeColor(context, android.R.attr.colorPrimary))
            setPadding(24, 24, 24, 24)
            setOnClickListener { copyToClipboard(item.label, item.value) }
            if (enableWorkflowInsert && item.insertRequest != null) {
                setOnLongClickListener {
                    sendInsertRequest(item.insertRequest)
                    true
                }
            }
        }

        rowLayout.addView(textContainer)
        rowLayout.addView(copyButton)

        return rowLayout
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.ui_inspector_copied, label), Toast.LENGTH_SHORT).show()
    }

    private fun countAllChildrenAndText(node: AccessibilityNodeInfo): Pair<Int, String> {
        var count = 0
        val sb = StringBuilder()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            count++
            if (!child.text.isNullOrBlank()) sb.append("[${child.text}] ")
            val (subCount, subText) = countAllChildrenAndText(child)
            count += subCount
            sb.append(subText)
        }
        return count to sb.toString()
    }

    private fun calculateDepth(node: AccessibilityNodeInfo): Int {
        var depth = 0
        var parent = node.parent
        while (parent != null) {
            depth++
            parent = parent.parent
        }
        return depth
    }

    /**
     * 生成 GKD 选择器路径
     * 根据 GKD 语法，从右往左匹配，父子关系由 A > B 建立，代表 A 是 B 的父节点。
     * 支持跨越层级：A >2 B 代表 A 是 B 的祖父节点。
     */
    private fun generateGkdSelector(node: AccessibilityNodeInfo): String {
        DebugLogger.d("GKDSelector", "开始生成选择器...")

        // 存储提取到的有用节点与它们的层级深度
        data class NodeItem(val selector: String, val depth: Int)
        val items = mutableListOf<NodeItem>()

        var currentNode: AccessibilityNodeInfo? = node
        var depth = 0
        var lastUsefulDepth = -1

        // 向上遍历，收集有用节点并记录其与目标节点的层级深度
        while (currentNode != null && depth < 8) {
            val isTarget = depth == 0
            val hasVid = !currentNode.viewIdResourceName.isNullOrBlank()
            val hasText = !currentNode.text.isNullOrBlank() && currentNode.text.toString().isNotEmpty()
            val hasDesc = !currentNode.contentDescription.isNullOrBlank() && currentNode.contentDescription.toString().isNotEmpty()

            // 目标节点总是包含，其它节点仅当其带有唯一标识时才作为辅助路径包含
            val isUseful = isTarget || hasVid || hasText || hasDesc

            if (isUseful) {
                val nodeSelector = buildConciseNodeSelector(currentNode, isTarget)
                items.add(NodeItem(nodeSelector, depth))
                lastUsefulDepth = depth
            }

            currentNode = currentNode.parent
            depth++

            // 如果连续3层都没有有用节点，停止遍历
            if (depth - lastUsefulDepth > 3) {
                break
            }
        }

        if (items.isEmpty()) return "*"
        if (items.size == 1) return items[0].selector

        // items 数组收集顺序是从底层(target)到顶层(ancestor)。
        // 根据 GKD 规范，关系选择器是从左向右书写 (祖先 > 父级 > ... > 目标)，且从右向左匹配。
        val reversedItems = items.reversed()
        var result = reversedItems[0].selector
        var currentDepth = reversedItems[0].depth

        for (i in 1 until reversedItems.size) {
            val next = reversedItems[i]
            // 计算层级差值以准确表达 GKD 中的结构：> 代表直系父节点，>n 代表第 n 层祖先节点
            val diff = currentDepth - next.depth
            val operator = if (diff == 1) " > " else " >$diff "

            result += operator + next.selector
            currentDepth = next.depth
        }

        DebugLogger.d("GKDSelector", "生成选择器: $result")
        return result
    }

    /**
     * 为节点构建简洁的选择器
     * 根据 GKD 快速查询优化：包含 vid/text 属性可优化检索性能。
     * @param isTarget 是否是目标节点
     */
    private fun buildConciseNodeSelector(node: AccessibilityNodeInfo, isTarget: Boolean): String {
        val attrs = mutableListOf<String>()

        // 获取简单类名（如 TextView, FrameLayout）
        val className = node.className?.toString() ?: "*"
        val simpleClassName = if (className.contains('.')) {
            className.substringAfterLast('.')
        } else {
            className
        }

        // 优先处理 vid，以便符合快速查询规则
        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrBlank()) {
            val shortVid = if (viewId.contains(":id/")) {
                viewId.substringAfter(":id/")
            } else if (viewId.contains("/id/")) {
                viewId.substringAfter("/id/")
            } else {
                viewId.substringAfterLast("/")
            }
            attrs.add("vid='${escapeGkdString(shortVid)}'")
        }

        // 其次添加 text
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.length <= 30) {
            attrs.add("text='${escapeGkdString(text)}'")
        }

        // 最后添加 desc
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc.length <= 30) {
            attrs.add("desc='${escapeGkdString(desc)}'")
        }

        // 如果是目标节点且无明显标识，但可点击，补充 clickable 状态
        if (isTarget && attrs.isEmpty() && node.isClickable) {
            attrs.add("clickable=true")
        }

        // 组装选择器，各属性独立使用 [] 包裹（对应 GKD 中的 && 操作）
        return if (attrs.isEmpty()) {
            simpleClassName
        } else {
            val attrStr = attrs.joinToString("][")
            "$simpleClassName[$attrStr]"
        }
    }

    /**
     * 转义 GKD 选择器字符串中的特殊字符
     */
    private fun escapeGkdString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            floatIconView?.removeCallbacks(inspectRunnable)
            if (coordinateLabelView != null) windowManager.removeView(coordinateLabelView)
            if (floatIconView != null) windowManager.removeView(floatIconView)
            if (selectionFrameView != null) windowManager.removeView(selectionFrameView)
        } catch (e: Exception) { }
        Toast.makeText(this, getString(R.string.ui_inspector_service_closed), Toast.LENGTH_SHORT).show()
    }
}
