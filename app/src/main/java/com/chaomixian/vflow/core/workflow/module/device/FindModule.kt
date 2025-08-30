// main/java/com/chaomixian/vflow/core/workflow/module/device/FindModule.kt

package com.chaomixian.vflow.modules.device

import android.graphics.Rect
import android.os.Parcelable
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.modules.variable.*
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern

@Parcelize
data class ScreenElement(
    val bounds: Rect,
    val text: String?
) : Parcelable

class FindTextModule : ActionModule {
    override val id = "vflow.device.find.text"
    override val metadata = ActionMetadata("查找文本", "在屏幕上查找元素", R.drawable.ic_node_search, "设备")

    private val matchModeOptions = listOf("完全匹配", "包含", "正则")

    // 将UI逻辑委托给独立的UI提供者
    override val uiProvider = FindModuleUIProvider(matchModeOptions)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "matchMode",
            name = "匹配模式",
            staticType = ParameterType.ENUM,
            defaultValue = "完全匹配",
            options = matchModeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("element", "找到的元素", ScreenElement::class.java)
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        val service = context.accessibilityService
        val targetText = (context.magicVariables["targetText"]?.toString() ?: context.variables["targetText"] as? String)

        if (targetText.isNullOrBlank()) return ActionResult(success = false)
        val rootNode = service.rootInActiveWindow ?: return ActionResult(success = false)

        try {
            val matchModeStr = context.variables["matchMode"] as? String ?: "完全匹配"
            onProgress(ProgressUpdate("正在以 [${matchModeStr}] 模式查找文本: '$targetText'"))
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