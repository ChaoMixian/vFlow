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
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

/**
 * UI 检查器服务
 */
class UiInspectorService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatIconView: ImageView? = null
    private var selectionFrameView: View? = null

    private lateinit var iconParams: WindowManager.LayoutParams
    private lateinit var frameParams: WindowManager.LayoutParams

    private var currentNodeInfo: AccessibilityNodeInfo? = null
    private var currentRootNode: AccessibilityNodeInfo? = null

    private var screenWidth = 0
    private var screenHeight = 0

    // 用于列表展示的数据结构
    private sealed class InspectorItem {
        data class Header(val title: String) : InspectorItem()
        data class Property(val label: String, val value: String) : InspectorItem()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "UI 检查器服务已启动", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
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
        val strokeColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorError)

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

        floatIconView = ImageView(themedContext).apply {
            setImageResource(R.drawable.rounded_architecture_24)
            setBackgroundResource(R.drawable.bg_widget_rounded)
            val containerColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorPrimaryContainer)
            val iconColor = resolveThemeColor(themedContext, com.google.android.material.R.attr.colorOnPrimaryContainer)

            background.setTint(containerColor)
            setColorFilter(iconColor)

            setPadding(25, 25, 25, 25)
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
            width = 150
            height = 150
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

        floatIconView?.setOnTouchListener { view, event ->
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
                        Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
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

                    inspectNodeAt(event.rawX.toInt(), event.rawY.toInt())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - downTime
                    if (!isDragging && pressDuration >= LONG_PRESS_TIMEOUT) {
                        // 长按：关闭服务
                        stopSelf()
                    } else if (!isDragging) {
                        // 短按：显示详情
                        showNodeDetails()
                    }
                    currentRootNode = null
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatIconView, iconParams)
        } catch (e: Exception) {
            Toast.makeText(this, "无法显示悬浮窗，请检查权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun inspectNodeAt(x: Int, y: Int) {
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
            Toast.makeText(this, "未选中任何控件", Toast.LENGTH_SHORT).show()
            return
        }

        val items = collectNodeInfo(node)

        val dialogContext = ThemeUtils.createThemedContext(this)

        val detailsView = createDetailsListView(dialogContext, items)

        val dialog = MaterialAlertDialogBuilder(dialogContext)
            .setTitle("控件属性")
            .setView(detailsView)
            .setPositiveButton("复制全部") { _, _ ->
                val fullText = items.joinToString("\n") { item ->
                    when(item) {
                        is InspectorItem.Header -> "\n=== ${item.title} ==="
                        is InspectorItem.Property -> "${item.label}: ${item.value}"
                    }
                }
                copyToClipboard("Full Details", fullText)
            }
            .setNeutralButton("关闭服务") { _, _ -> stopSelf() }
            .setNegativeButton("关闭", null)
            .create()

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

        val packageName = node.packageName?.toString() ?: "未知"
        var appName = "未知"
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { }

        val currentActivity = ServiceStateBus.lastWindowClassName ?: "未知"

        list.add(InspectorItem.Header("基础信息"))
        list.add(InspectorItem.Property("所在APP", "$appName ($packageName)"))
        list.add(InspectorItem.Property("所在页面", currentActivity))
        list.add(InspectorItem.Property("控件ID", node.viewIdResourceName ?: "无"))
        list.add(InspectorItem.Property("控件文本", node.text?.toString() ?: ""))
        list.add(InspectorItem.Property("控件类型", node.className?.toString() ?: "未知"))

        list.add(InspectorItem.Header("布局坐标"))
        list.add(InspectorItem.Property("中心点", "${rect.centerX()},${rect.centerY()}"))
        list.add(InspectorItem.Property("坐标 X", rect.left.toString()))
        list.add(InspectorItem.Property("坐标 Y", rect.top.toString()))
        list.add(InspectorItem.Property("控件长度(W)", rect.width().toString()))
        list.add(InspectorItem.Property("控件宽度(H)", rect.height().toString()))

        list.add(InspectorItem.Header("层级与子节点"))
        val directChildCount = node.childCount
        val (totalChildCount, childTexts) = countAllChildrenAndText(node)
        list.add(InspectorItem.Property("子控件数", directChildCount.toString()))
        list.add(InspectorItem.Property("子控件总数", totalChildCount.toString()))
        list.add(InspectorItem.Property("子控件文本", if(childTexts.length > 300) childTexts.take(300) + "..." else childTexts))
        list.add(InspectorItem.Property("控件层级", calculateDepth(node).toString()))
        list.add(InspectorItem.Property("父控件", node.parent?.className?.toString() ?: "无"))

        list.add(InspectorItem.Header("状态属性"))
        list.add(InspectorItem.Property("可点击 (Clickable)", node.isClickable.toString()))
        list.add(InspectorItem.Property("可长按 (LongClickable)", node.isLongClickable.toString()))
        list.add(InspectorItem.Property("可滑动 (Scrollable)", node.isScrollable.toString()))
        list.add(InspectorItem.Property("可选中 (Checkable)", node.isCheckable.toString()))
        list.add(InspectorItem.Property("已选中 (Checked)", node.isChecked.toString()))
        list.add(InspectorItem.Property("可聚焦 (Focusable)", node.isFocusable.toString()))
        list.add(InspectorItem.Property("已聚焦 (Focused)", node.isFocused.toString()))
        list.add(InspectorItem.Property("可见性 (Visible)", if(node.isVisibleToUser) "可见" else "不可见"))
        list.add(InspectorItem.Property("启用状态 (Enabled)", node.isEnabled.toString()))

        return list
    }

    /**
     * 辅助函数：解析当前主题中的颜色属性
     */
    private fun resolveThemeColor(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
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
            setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary))

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
            imageTintList = ColorStateList.valueOf(resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary))
            setPadding(24, 24, 24, 24)
            setOnClickListener { copyToClipboard(item.label, item.value) }
        }

        rowLayout.addView(textContainer)
        rowLayout.addView(copyButton)

        return rowLayout
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制 $label", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (floatIconView != null) windowManager.removeView(floatIconView)
            if (selectionFrameView != null) windowManager.removeView(selectionFrameView)
        } catch (e: Exception) { }
        Toast.makeText(this, "UI 检查器已关闭", Toast.LENGTH_SHORT).show()
    }
}