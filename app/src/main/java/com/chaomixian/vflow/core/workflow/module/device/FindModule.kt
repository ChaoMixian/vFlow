// main/java/com/chaomixian/vflow/core/workflow/module/device/FindModule.kt

package com.chaomixian.vflow.modules.device

import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
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

class FindTextModule : ActionModule, ModuleWithCustomEditor {
    override val id = "vflow.device.find.text"
    override val metadata = ActionMetadata("查找文本", "在屏幕上查找元素", R.drawable.ic_node_search, "设备")

    // --- 核心修复：getParameters 只保留纯静态参数 ---
    // "targetText" 已移至 getInputs，因为它既可以是静态也可以是动态的。
    override fun getParameters(): List<ParameterDefinition> = listOf(
        ParameterDefinition("matchMode", "匹配模式", ParameterType.ENUM, "完全匹配", listOf("完全匹配", "包含", "正则"))
    )

    // "targetText" 在这里定义，ActionEditorSheet 会为它创建带魔法变量按钮的UI
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            // --- 核心修复：添加 TextVariable 到可接受类型 ---
            acceptedMagicVariableTypes = setOf(ScreenElement::class.java, TextVariable::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("element", "找到的元素", ScreenElement::class.java)
    )

    // --- 自定义UI实现 ---

    // ViewHolder 现在只需要持有 Spinner
    class FindTextEditorViewHolder(
        view: View,
        val modeSpinner: Spinner
    ) : CustomEditorViewHolder(view)

    override fun createEditorView(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder {
        // --- 核心修复：自定义视图现在只创建 getParameters 中定义的UI ---
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 添加一些边距以获得更好的外观
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }

        // 只创建匹配模式下拉框
        val matchOptions = getParameters().find { it.id == "matchMode" }?.options ?: emptyList()
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, matchOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val currentMode = currentParameters["matchMode"] as? String ?: "完全匹配"
        val selectionIndex = matchOptions.indexOf(currentMode)
        if (selectionIndex != -1) spinner.setSelection(selectionIndex)

        container.addView(spinner)

        // "目标文本" 的输入框将由 ActionEditorSheet 自动生成
        return FindTextEditorViewHolder(container, spinner)
    }

    override fun readParametersFromEditorView(holder: CustomEditorViewHolder): Map<String, Any?> {
        // --- 核心修复：只从自定义视图中读取它所管理的参数 ---
        val h = holder as FindTextEditorViewHolder
        val mode = h.modeSpinner.selectedItem.toString()
        // 不再读取 "targetText"，它将由 ActionEditorSheet 的通用逻辑读取
        return mapOf("matchMode" to mode)
    }

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // ... execute 和 findNodesByText 方法保持不变 ...
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