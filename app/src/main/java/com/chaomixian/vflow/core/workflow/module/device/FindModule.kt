package com.chaomixian.vflow.modules.device

import android.graphics.Rect
import android.os.Parcelable
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern

// 模块内部定义自己的输出类型，实现完全解耦
@Parcelize
data class ScreenElement(
    val bounds: Rect,
    val text: String?
) : Parcelable

class FindTextModule : ActionModule {
    override val id = "vflow.device.find.text"
    override val metadata = ActionMetadata("查找文本", "在屏幕上查找元素", R.drawable.ic_node_search, "设备")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("targetText", "目标文本", ParameterType.STRING, acceptsMagicVariable = true)
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("element", "找到的元素", ScreenElement::class.java)
    )

    override fun getParameters(): List<ParameterDefinition> = listOf(
        ParameterDefinition("targetText", "目标文本", ParameterType.STRING),
        ParameterDefinition("matchMode", "匹配模式", ParameterType.ENUM, "完全匹配", listOf("完全匹配", "包含", "正则"))
    )

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // ... (查找逻辑不变)
        val service = context.accessibilityService
        val targetText = (context.magicVariables["targetText"]?.toString() ?: context.variables["targetText"] as? String)

        if (targetText.isNullOrBlank()) return ActionResult(success = false)
        val rootNode = service.rootInActiveWindow ?: return ActionResult(success = false)

        try {
            val matchModeStr = context.variables["matchMode"] as? String ?: "完全匹配"
            val nodes = findNodesByText(rootNode, targetText, matchModeStr)

            if (nodes.isEmpty()) return ActionResult(success = false)

            val foundNode = nodes.first()
            val bounds = Rect()
            foundNode.getBoundsInScreen(bounds)

            val screenElement = ScreenElement(
                bounds = bounds,
                text = foundNode.text?.toString() ?: foundNode.contentDescription?.toString()
            )

            nodes.forEach { it.recycle() }

            return ActionResult(success = true, outputs = mapOf("element" to screenElement))
        } finally {
            rootNode.recycle()
        }
    }

    // findNodesByText 逻辑保持不变...
    private fun findNodesByText(rootNode: AccessibilityNodeInfo, text: String, matchModeStr: String): List<AccessibilityNodeInfo> {
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(rootNode))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()

            val checkMatch = { source: String ->
                when (matchModeStr) {
                    "包含" -> source.contains(text, ignoreCase = true)
                    "正则" -> try { Pattern.compile(text).matcher(source).find() } catch (e: Exception) { false }
                    else -> source == text
                }
            }

            if (nodeText != null && checkMatch(nodeText)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
            } else if (nodeDesc != null && checkMatch(nodeDesc)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }
        return matchedNodes
    }
}