// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/UiSelectorModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.property.CompareOperator
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * UI选择器模块
 * 使用 GKD selector 语法查找屏幕控件
 */
class UiSelectorModule : BaseModule() {

    override val id = "vflow.interaction.ui_selector"
    override val metadata = ActionMetadata(
        name = "UI选择器",
        description = "使用 GKD selector 语法查找屏幕控件。" +
                "支持复杂的选择器表达式，包括属性匹配、连接操作符、逻辑运算等",
        iconRes = R.drawable.rounded_feature_search_24,
        category = "界面交互"
    )

    companion object {
        val resultSelectionOptions = listOf("第一个", "最后一个", "最接近中心", "最接近顶部")
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "selector",
            "选择器表达式",
            ParameterType.STRING,
            "",
            acceptsMagicVariable = true,
            supportsRichText = false,
            hint = "如: @TextView[text='设置']"
        ),
        InputDefinition(
            "result_selection",
            "结果选择",
            ParameterType.ENUM,
            "第一个",
            options = resultSelectionOptions,
            acceptsMagicVariable = false,
            isFolded = true
        ),
        InputDefinition(
            "depth_limit",
            "最大深度",
            ParameterType.NUMBER,
            50,
            acceptsMagicVariable = true,
            hint = "默认 50，范围 1-200",
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("found", "是否找到", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("count", "找到数量", VTypeRegistry.NUMBER.id),
        OutputDefinition("element", "选中的控件", VTypeRegistry.SCREEN_ELEMENT.id),
        OutputDefinition("all_elements", "所有控件", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.SCREEN_ELEMENT.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val selector = step.parameters["selector"]?.toString() ?: ""

        return if (selector.isNotEmpty()) {
            val displaySelector = if (selector.length > 50) {
                selector.take(50) + "..."
            } else {
                selector
            }

            val parts = mutableListOf<Any>()
            parts.add("UI选择器: ")

            val selectorPill = PillUtil.createPillFromParam(
                step.parameters["selector"],
                getInputs().find { it.id == "selector" }
            )
            parts.add(selectorPill)

            PillUtil.buildSpannable(context, *parts.toTypedArray())
        } else {
            "UI选择器"
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 获取服务
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务不可用", "无障碍服务未启动")

        // 2. 解析参数
        val selectorString = context.variables["selector"]?.toString()
        val resultSelection = context.variables["result_selection"] as? String ?: "第一个"
        val depthLimit = (context.variables["depth_limit"] as? Number)?.toInt() ?: 50

        // 3. 验证参数
        if (selectorString.isNullOrBlank()) {
            return ExecutionResult.Failure("参数错误", "选择器表达式不能为空")
        }

        // 4. 解析选择器
        val selector = try {
            val parsed = Selector.parse(selectorString)
            DebugLogger.d("UiSelectorModule", "选择器解析成功: $parsed")
            DebugLogger.d("UiSelectorModule", "选择器表达式: ${parsed.expression}")
            DebugLogger.d("UiSelectorModule", "选择器fastQuery: ${parsed.fastQueryList}")
            parsed
        } catch (e: Exception) {
            DebugLogger.e("UiSelectorModule", "选择器解析失败: ${e.message}", e)
            return ExecutionResult.Failure("选择器语法错误", "无法解析: ${e.message}")
        }

        onProgress(ProgressUpdate("正在查找控件..."))

        // 5. 获取根节点
        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure("无法访问屏幕", "无法获取当前界面")

        // 6. 查找匹配节点
        val transform = createTransform()
        val allElements = mutableListOf<VScreenElement>()

        try {
            // 先统计总节点数并打印所有节点信息
            var totalNodes = 0
            val allNodesInfo = mutableListOf<String>()

            transform.getDescendants(rootNode).forEach { node ->
                totalNodes++
                val name = node.className?.toString()?.substringAfterLast('.') ?: "unknown"
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                allNodesInfo.add("  [$totalNodes] class=$name, text='$text', desc='$desc'")
            }

            DebugLogger.d("UiSelectorModule", "选择器: $selectorString")
            DebugLogger.d("UiSelectorModule", "总节点数: $totalNodes")

            // 只打印前20个节点，避免日志太多
            allNodesInfo.take(20).forEach { info ->
                DebugLogger.d("UiSelectorModule", info)
            }
            if (totalNodes > 20) {
                DebugLogger.d("UiSelectorModule", "  ... 还有 ${totalNodes - 20} 个节点")
            }

            val matchedNodes = transform.querySelectorAll(rootNode, selector, MatchOption(fastQuery = false))
                .toList()  // 强制求值

            DebugLogger.d("UiSelectorModule", "querySelectorAll 返回 ${matchedNodes.size} 个节点")

            matchedNodes.forEach { node ->
                DebugLogger.d("UiSelectorModule", "找到匹配节点: class=${node.className?.toString()?.substringAfterLast('.')}, text=${node.text}")
                allElements.add(VScreenElement.fromAccessibilityNode(node, calculateDepth(rootNode, node)))
            }

            DebugLogger.d("UiSelectorModule", "匹配节点数: ${allElements.size}")

            // 7. 处理结果
            if (allElements.isEmpty()) {
                return ExecutionResult.Failure(
                    "未找到控件",
                    "选择器 '$selectorString' 未匹配到任何控件 (总节点数: $totalNodes)",
                    partialOutputs = mapOf(
                        "success" to VBoolean(false),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),
                        "element" to VNull,
                        "all_elements" to emptyList<VScreenElement>()
                    )
                )
            }

            val selectedElement = selectElement(allElements, resultSelection, rootNode)

            onProgress(ProgressUpdate("找到 ${allElements.size} 个控件"))

            return ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "found" to VBoolean(true),
                "count" to VNumber(allElements.size.toDouble()),
                "element" to selectedElement,
                "all_elements" to allElements
            ))

        } catch (e: Exception) {
            return ExecutionResult.Failure("查找失败", e.localizedMessage ?: "发生了未知错误")
        }
    }

    /**
     * 创建 Transform 实例
     */
    private fun createTransform(): Transform<AccessibilityNodeInfo> {
        return Transform(
            getAttr = { target, name ->
                when (target) {
                    is QueryContext<*> -> when (name) {
                        "prev" -> target.prev
                        "current" -> target.current
                        else -> getNodeAttr(target.current as AccessibilityNodeInfo, name)
                    }
                    is AccessibilityNodeInfo -> getNodeAttr(target, name)
                    is CharSequence -> getCharSequenceAttr(target, name)
                    else -> null
                }
            },
            getInvoke = getInvoke@{ target, name, args ->
                when (target) {
                    is QueryContext<*> -> when (name) {
                        "getPrev" -> {
                            val index = args.firstOrNull() as? Int ?: return@getInvoke null
                            target.getPrev(index)
                        }

                        "getChild" -> getNodeInvoke(target.current as AccessibilityNodeInfo, name, args)
                        else -> getNodeInvoke(target.current as AccessibilityNodeInfo, name, args)
                    }

                    is AccessibilityNodeInfo -> getNodeInvoke(target, name, args)
                    is CharSequence -> getCharSequenceInvoke(target, name, args)
                    is Int -> getIntInvoke(target, name, args)
                    is Boolean -> getBooleanInvoke(target, name, args)
                    else -> null
                }
            },
            getName = { node ->
                node.className?.toString()
            },
            getChildren = { node ->
                sequence {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { yield(it) }
                    }
                }
            },
            getParent = { node -> node.parent },
            traverseFastQueryDescendants = { rootNode, fastQueryList ->
                sequence {
                    val stack = mutableListOf(rootNode)
                    while (stack.isNotEmpty()) {
                        val node = stack.removeAt(stack.size - 1)

                        // 检查节点是否匹配所有 fastQuery 条件
                        var match = true
                        for (fq in fastQueryList) {
                            when (fq) {
                                is li.songe.selector.FastQuery.Text -> {
                                    val nodeText = node.text?.toString() ?: ""
                                    val matched = when (fq.operator) {
                                        is CompareOperator.Equal -> nodeText == fq.value
                                        is CompareOperator.NotEqual -> nodeText != fq.value
                                        is CompareOperator.Less -> nodeText < fq.value
                                        is CompareOperator.LessEqual -> nodeText <= fq.value
                                        is CompareOperator.More -> nodeText > fq.value
                                        is CompareOperator.MoreEqual -> nodeText >= fq.value
                                        is CompareOperator.Start -> nodeText.startsWith(fq.value)
                                        is CompareOperator.Include -> nodeText.contains(fq.value)
                                        is CompareOperator.End -> nodeText.endsWith(fq.value)
                                        else -> false
                                    }
                                    if (!matched) {
                                        match = false
                                        break
                                    }
                                }
                                is li.songe.selector.FastQuery.Id -> {
                                    val nodeId = node.viewIdResourceName ?: ""
                                    if (nodeId != fq.value) {
                                        match = false
                                        break
                                    }
                                }
                                is li.songe.selector.FastQuery.Vid -> {
                                    val id = node.viewIdResourceName ?: continue
                                    val pkg = node.packageName?.toString()
                                    val vid = if (pkg != null && id.startsWith(pkg) && id.startsWith(":id/", pkg.length)) {
                                        id.substring(pkg.length + ":id/".length)
                                    } else {
                                        id
                                    }
                                    if (vid != fq.value) {
                                        match = false
                                        break
                                    }
                                }
                            }
                        }

                        if (match) {
                            yield(node)
                        }

                        // 添加子节点到栈中（深度优先）
                        for (i in node.childCount - 1 downTo 0) {
                            node.getChild(i)?.let { stack.add(it) }
                        }
                    }
                }
            }
        )
    }

    /**
     * 获取节点属性
     */
    private fun getNodeAttr(node: Any, name: String): Any? {
        if (node !is AccessibilityNodeInfo) return null

        val result = when (name) {
            // 基本属性
            "text" -> node.text?.toString()
            "desc", "contentDescription" -> node.contentDescription?.toString()
            "id" -> node.viewIdResourceName
            "vid" -> {
                val id = node.viewIdResourceName ?: return null
                val pkg = node.packageName?.toString() ?: return null
                if (id.startsWith(pkg) && id.startsWith(":id/", pkg.length)) {
                    id.substring(pkg.length + ":id/".length)
                } else {
                    id
                }
            }
            "viewId" -> node.viewIdResourceName
            "name", "class", "className" -> node.className?.toString()

            // 内部 ID
            "_id" -> node.uniqueId?.toIntOrNull() ?: node.hashCode()

            // 布尔属性
            "clickable" -> node.isClickable
            "enabled" -> node.isEnabled
            "checkable" -> node.isCheckable
            "checked" -> node.isChecked
            "focusable" -> node.isFocusable
            "focused" -> node.isFocused
            "scrollable" -> node.isScrollable
            "longClickable" -> node.isLongClickable
            "selected" -> node.isSelected
            "editable" -> node.isEditable
            "visibleToUser" -> node.isVisibleToUser

            // 子节点信息
            "childCount" -> node.childCount
            "depth" -> {
                var depth = 0
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    current = current.parent
                    depth++
                }
                depth
            }
            "parent" -> node.parent
            "index" -> {
                val parent = node.parent
                var result = -1
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        if (parent.getChild(i) == node) {
                            result = i
                            break
                        }
                    }
                }
                result
            }

            // 边界信息
            "left" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.left
            }
            "top" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.top
            }
            "right" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.right
            }
            "bottom" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.bottom
            }
            "width" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width()
            }
            "height" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.height()
            }

            else -> null
        }

        return result
    }

    /**
     * 调用节点方法
     */
    private fun getNodeInvoke(node: Any, name: String, args: List<Any>): Any? {
        if (node !is AccessibilityNodeInfo) return null

        return when (name) {
            "getChild" -> {
                val index = args.firstOrNull()?.toString()?.toIntOrNull() ?: 0
                if (index in 0 until node.childCount) node.getChild(index) else null
            }
            "getParent" -> node.parent
            else -> null
        }
    }

    /**
     * 计算节点深度
     */
    private fun calculateDepth(root: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Int {
        var depth = 0
        var current: AccessibilityNodeInfo? = node
        while (current != null && current != root) {
            depth++
            current = current.parent
        }
        return depth
    }

    /**
     * 选择元素
     */
    private fun selectElement(
        elements: List<VScreenElement>,
        strategy: String,
        rootNode: AccessibilityNodeInfo
    ): VScreenElement {
        return when (strategy) {
            "最后一个" -> elements.last()
            "最接近中心" -> {
                val bounds = Rect()
                rootNode.getBoundsInScreen(bounds)
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                elements.minByOrNull {
                    val dx = it.centerX - centerX
                    val dy = it.centerY - centerY
                    dx * dx + dy * dy
                } ?: elements.first()
            }
            "最接近顶部" -> elements.minByOrNull { it.bounds.top } ?: elements.first()
            else -> elements.first()
        }
    }
}
