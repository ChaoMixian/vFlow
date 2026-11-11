package com.chaomixian.vflow.core.recording

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson

/**
 * UI 录制使用的节点采集与 GKD 选择器生成工具。
 *
 * 所有对 [AccessibilityNodeInfo] 的访问都用 runCatching 包裹，
 * 避免个别厂商 ROM 的异常节点导致录制服务崩溃。
 */
object RecordingNodeUtils {

    /** 用于生成紧凑快照签名的 Gson（不输出 HTML 转义）。 */
    private val signatureGson = Gson()

    /**
     * 生成 GKD 选择器：
     * 优先使用 viewId（`[vid='...']`），其次 text，再次 contentDescription。
     * 单引号、反斜杠和换行会被转义。
     */
    fun buildGkdSelector(node: AccessibilityNodeInfo?): String {
        if (node == null) return "*"
        return runCatching {
            val viewId = node.viewIdResourceName
            if (!viewId.isNullOrBlank()) {
                val shortId = when {
                    viewId.contains(":id/") -> viewId.substringAfter(":id/")
                    viewId.contains("/id/") -> viewId.substringAfter("/id/")
                    else -> viewId.substringAfterLast("/")
                }
                return@runCatching "[vid='${escapeGkdString(shortId)}']"
            }

            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                return@runCatching "[text='${escapeGkdString(text)}']"
            }

            val description = node.contentDescription?.toString()
            if (!description.isNullOrBlank()) {
                return@runCatching "[desc='${escapeGkdString(description)}']"
            }

            val className = node.className?.toString()
            if (!className.isNullOrBlank()) {
                if (className.contains('.')) className.substringAfterLast('.') else className
            } else {
                "*"
            }
        }.getOrDefault("*")
    }

    /** 转义 GKD 选择器字符串中的特殊字符。 */
    fun escapeGkdString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 遍历以 [root] 为根的可见节点树，最多采集 [maxNodes] 个节点。
     * 本方法内部会回收 root 及其所有子节点，调用方不应再使用 root。
     */
    @Suppress("DEPRECATION")
    fun collectNodes(root: AccessibilityNodeInfo?, maxNodes: Int = 800): List<RecordedUiNode> {
        if (root == null || maxNodes <= 0) return emptyList()
        val result = ArrayList<RecordedUiNode>(minOf(maxNodes, 256))
        val counter = intArrayOf(0)
        try {
            traverse(root, 0, result, maxNodes, counter)
        } catch (_: Throwable) {
            // 单个节点异常不应该导致整次采样失败，保留已采集的部分
        } finally {
            runCatching { root.recycle() }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun traverse(
        node: AccessibilityNodeInfo,
        depth: Int,
        result: MutableList<RecordedUiNode>,
        maxNodes: Int,
        counter: IntArray,
    ) {
        if (result.size >= maxNodes) return
        if (depth > 0 && runCatching { !node.isVisibleToUser }.getOrDefault(true)) return

        val index = counter[0]++
        result.add(toRecordedNode(node, depth, index))
        if (result.size >= maxNodes) return

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until childCount) {
            if (result.size >= maxNodes) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            try {
                traverse(child, depth + 1, result, maxNodes, counter)
            } finally {
                runCatching { child.recycle() }
            }
        }
    }

    /** 把一个无障碍节点转换为可序列化的快照节点。 */
    @Suppress("DEPRECATION")
    fun toRecordedNode(node: AccessibilityNodeInfo, depth: Int = 0, index: Int = 0): RecordedUiNode {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        return RecordedUiNode(
            text = runCatching { node.text?.toString().orEmpty() }.getOrDefault(""),
            description = runCatching { node.contentDescription?.toString().orEmpty() }.getOrDefault(""),
            hint = runCatching { node.hintText?.toString().orEmpty() }.getOrDefault(""),
            viewId = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault(""),
            className = runCatching { node.className?.toString().orEmpty() }.getOrDefault(""),
            packageName = runCatching { node.packageName?.toString().orEmpty() }.getOrDefault(""),
            gkd = buildGkdSelector(node),
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            clickable = runCatching { node.isClickable }.getOrDefault(false),
            longClickable = runCatching { node.isLongClickable }.getOrDefault(false),
            editable = runCatching { node.isEditable }.getOrDefault(false),
            scrollable = runCatching { node.isScrollable }.getOrDefault(false),
            focusable = runCatching { node.isFocusable }.getOrDefault(false),
            focused = runCatching { node.isFocused }.getOrDefault(false),
            checkable = runCatching { node.isCheckable }.getOrDefault(false),
            checked = runCatching { node.isChecked }.getOrDefault(false),
            selected = runCatching { node.isSelected }.getOrDefault(false),
            enabled = runCatching { node.isEnabled }.getOrDefault(false),
            password = runCatching { node.isPassword }.getOrDefault(false),
            depth = depth,
            index = index,
            actions = runCatching {
                node.actionList.mapNotNull { actionName(it.id) }
            }.getOrDefault(emptyList()),
        )
    }

    /**
     * 用节点的 gkd / text / bounds / 状态信息生成 JSON 快照签名。
     * 两次采样的签名相同即认为 UI 未发生变化。
     */
    fun snapshotSignature(nodes: List<RecordedUiNode>): String {
        val payload = nodes.map { node ->
            arrayOf<Any>(
                node.gkd,
                node.text,
                node.description,
                node.left,
                node.top,
                node.right,
                node.bottom,
                node.clickable,
                node.longClickable,
                node.editable,
                node.scrollable,
                node.focused,
                node.checked,
                node.selected,
                node.enabled,
                node.password,
            )
        }
        return signatureGson.toJson(payload)
    }

    /**
     * 根据无障碍事件构造录制事件。节点可为 null，此时使用事件自带的信息。
     */
    fun buildEvent(
        event: AccessibilityEvent,
        type: String,
        node: AccessibilityNodeInfo?,
    ): UiRecordingEvent {
        val bounds = Rect()
        node?.let { runCatching { it.getBoundsInScreen(bounds) } }
        val eventText = runCatching { event.text.joinToString(" ") }.getOrDefault("")
        return UiRecordingEvent(
            timestampMs = SystemClock.elapsedRealtime(),
            type = type,
            gkd = buildGkdSelector(node),
            viewId = runCatching { node?.viewIdResourceName.orEmpty() }.getOrDefault(""),
            className = runCatching { node?.className?.toString().orEmpty() }
                .getOrDefault("")
                .ifBlank { runCatching { event.className?.toString().orEmpty() }.getOrDefault("") },
            text = runCatching { node?.text?.toString().orEmpty() }
                .getOrDefault("")
                .ifBlank { eventText },
            packageName = runCatching { node?.packageName?.toString().orEmpty() }
                .getOrDefault("")
                .ifBlank { runCatching { event.packageName?.toString().orEmpty() }.getOrDefault("") },
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
        )
    }

    private fun actionName(actionId: Int): String? {
        return when (actionId) {
            AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "LONG_CLICK"
            AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
            AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "CLEAR_FOCUS"
            AccessibilityNodeInfo.ACTION_SELECT -> "SELECT"
            AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "CLEAR_SELECTION"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "SCROLL_FORWARD"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "SCROLL_BACKWARD"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
            AccessibilityNodeInfo.ACTION_COPY -> "COPY"
            AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
            AccessibilityNodeInfo.ACTION_CUT -> "CUT"
            AccessibilityNodeInfo.ACTION_EXPAND -> "EXPAND"
            AccessibilityNodeInfo.ACTION_COLLAPSE -> "COLLAPSE"
            AccessibilityNodeInfo.ACTION_DISMISS -> "DISMISS"
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> "SET_SELECTION"
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "NEXT_AT_MOVEMENT_GRANULARITY"
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "PREVIOUS_AT_MOVEMENT_GRANULARITY"
            else -> actionId.toString()
        }
    }
}
