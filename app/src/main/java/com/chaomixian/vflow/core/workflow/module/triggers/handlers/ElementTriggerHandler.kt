package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.*
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.*
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.property.CompareOperator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 元素触发器处理器
 * 监听页面变化，匹配选择器并触发工作流
 */
class ElementTriggerHandler : ListeningTriggerHandler() {

    companion object {
        private const val TAG = "ElementTriggerHandler"
    }

    // 存储所有工作流的状态（动态创建）
    private val triggerStates = CopyOnWriteArrayList<ElementTriggerState>()

    // Transform 实例
    private val transform = createTransform()

    // 监听Job
    private var listeningJob: Job? = null

    override fun getTriggerModuleId(): String = ElementTriggerModule().id

    override fun startListening(context: Context) {
        DebugLogger.d(TAG, "开始监听元素变化")

        // 确保 triggerStates 与 listeningWorkflows 同步
        syncTriggerStates()

        listeningJob = triggerScope.launch {
            // 同时监听窗口变化和内容变化事件
            launch {
                ServiceStateBus.windowChangeEventFlow.collect { (packageName, className) ->
                    DebugLogger.d(TAG, "收到窗口变化事件: $packageName / $className")
                    checkAndTriggerWorkflows(context)
                }
            }
            launch {
                ServiceStateBus.windowContentChangedFlow.collect { (packageName, className) ->
                    DebugLogger.d(TAG, "收到窗口内容变化事件: $packageName / $className")
                    checkAndTriggerWorkflows(context)
                }
            }
        }
    }

    override fun stopListening(context: Context) {
        DebugLogger.d(TAG, "停止监听元素变化")
        listeningJob?.cancel()
        listeningJob = null
        triggerStates.clear()
    }

    /**
     * 同步 triggerStates 与 listeningWorkflows
     */
    private fun syncTriggerStates() {
        val currentWorkflowIds = listeningWorkflows.map { it.id }.toSet()

        // 移除不存在的workflow的state
        triggerStates.removeAll { state ->
            !currentWorkflowIds.contains(state.workflow.id)
        }

        // 为新workflow创建state
        for (workflow in listeningWorkflows) {
            if (triggerStates.none { it.workflow.id == workflow.id }) {
                val state = createTriggerState(workflow)
                if (state != null) {
                    triggerStates.add(state)
                    DebugLogger.d(TAG, "加载工作流 '${workflow.name}'")
                }
            }
        }
    }

    /**
     * 创建触发器状态
     */
    private fun createTriggerState(workflow: Workflow): ElementTriggerState? {
        val config = workflow.triggerConfig ?: return null

        val selectorString = config["selector"] as? String
        if (selectorString.isNullOrBlank()) {
            return null
        }

        val selector = try {
            Selector.parse(selectorString)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "解析选择器失败: $selectorString", e)
            return null
        }

        return ElementTriggerState(
            workflow = workflow,
            selector = selector,
            matchDelay = (config["matchDelay"] as? Number)?.toLong() ?: 0L,
            actionDelay = (config["actionDelay"] as? Number)?.toLong() ?: 0L,
            matchTime = (config["matchTime"] as? Number)?.toLong() ?: 0L,
            actionCd = (config["actionCd"] as? Number)?.toLong() ?: 1000L,
            actionMaximum = (config["actionMaximum"] as? Number)?.toInt()
        )
    }

    /**
     * 检查并触发匹配的工作流
     */
    private fun checkAndTriggerWorkflows(context: Context) {
        // 先同步状态
        syncTriggerStates()

        val service = ServiceStateBus.getAccessibilityService() ?: return
        val rootNode = service.rootInActiveWindow ?: return

        val t = System.currentTimeMillis()

        // 用于同一次检查中的去重（避免同一节点触发多个选择器）
        val matchedThisTime = mutableSetOf<Int?>()

        for (state in triggerStates) {
            try {
                // 1. 检查状态
                val status = state.getStatus()
                if (status != TriggerStatus.Ready) {
                    continue
                }

                // 2. 匹配选择器
                val matchedNode = transform.querySelectorAll(rootNode, state.selector, MatchOption(fastQuery = true))
                    .firstOrNull()
                if (matchedNode == null) {
                    continue
                }

                val nodeId = matchedNode.uniqueId?.toIntOrNull() ?: matchedNode.hashCode()

                // 3. 同一次检查中去重（避免不同选择器匹配到同一节点重复触发）
                if (nodeId in matchedThisTime) {
                    continue
                }
                matchedThisTime.add(nodeId)

                // 4. 更新匹配时间
                state.matchChangedTime = t

                // 5. 处理触发延迟
                if (state.actionDelay > 0) {
                    state.actionDelayTriggerTime = t
                    triggerScope.launch {
                        delay(state.actionDelay)
                        performTrigger(context, state, matchedNode)
                    }
                } else {
                    performTrigger(context, state, matchedNode)
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "处理工作流 '${state.workflow.name}' 时出错", e)
            }
        }
    }

    /**
     * 执行触发
     */
    private fun performTrigger(context: Context, state: ElementTriggerState, node: AccessibilityNodeInfo) {
        // 检查冷却时间
        if (state.getStatus() != TriggerStatus.Ready) {
            return
        }

        // 创建 VScreenElement
        val element = VScreenElement.fromAccessibilityNode(node, calculateDepth(node))

        // 调用 WorkflowExecutor
        WorkflowExecutor.execute(
            workflow = state.workflow,
            context = context.applicationContext,
            triggerData = ElementTriggerData(element)
        )

        // 更新状态
        state.actionTriggerTime = System.currentTimeMillis()
        state.actionCount++
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
                    else -> null
                }
            },
            getInvoke = getInvoke@{ target, name, args ->
                when (target) {
                    is QueryContext<*> -> getNodeInvoke(target.current as AccessibilityNodeInfo, name, args)
                    is AccessibilityNodeInfo -> getNodeInvoke(target, name, args)
                    else -> null
                }
            },
            getName = { node -> node.className?.toString() },
            getChildren = { node ->
                sequence {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { yield(it) }
                    }
                }
            },
            getParent = { node -> node.parent },
            traverseFastQueryDescendants = { rootNode, fastQueryList ->
                fastQueryDescendants(rootNode, fastQueryList)
            }
        )
    }

    /**
     * 快速查询后代节点
     */
    private fun fastQueryDescendants(
        rootNode: AccessibilityNodeInfo,
        fastQueryList: List<li.songe.selector.FastQuery>
    ): Sequence<AccessibilityNodeInfo> {
        return sequence {
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
                            if (node.viewIdResourceName != fq.value) {
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

                // 添加子节点到栈中
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.add(it) }
                }
            }
        }
    }

    /**
     * 获取节点属性
     */
    private fun getNodeAttr(node: Any, name: String): Any? {
        if (node !is AccessibilityNodeInfo) return null

        return when (name) {
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
            "_id" -> node.uniqueId?.toIntOrNull() ?: node.hashCode()
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
            "childCount" -> node.childCount
            "depth" -> calculateDepth(node)
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
            "left" -> getBounds(node).left
            "top" -> getBounds(node).top
            "right" -> getBounds(node).right
            "bottom" -> getBounds(node).bottom
            "width" -> getBounds(node).width()
            "height" -> getBounds(node).height()
            else -> null
        }
    }

    private fun getBounds(node: AccessibilityNodeInfo): Rect {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds
    }

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

    private fun calculateDepth(node: AccessibilityNodeInfo): Int {
        var depth = 0
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            current = current.parent
            depth++
        }
        return depth
    }
}

/**
 * 元素触发器触发数据
 */
data class ElementTriggerData(
    val element: VScreenElement
) : android.os.Parcelable {
    override fun toString(): String = "ElementTriggerData(element=${element.asString()})"

    companion object CREATOR : android.os.Parcelable.Creator<ElementTriggerData> {
        override fun createFromParcel(parcel: android.os.Parcel): ElementTriggerData {
            return ElementTriggerData(
                element = parcel.readParcelable(VScreenElement::class.java.classLoader)!!
            )
        }

        override fun newArray(size: Int): Array<ElementTriggerData?> {
            return arrayOfNulls(size)
        }
    }

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeParcelable(element, flags)
    }

    override fun describeContents(): Int = 0
}
