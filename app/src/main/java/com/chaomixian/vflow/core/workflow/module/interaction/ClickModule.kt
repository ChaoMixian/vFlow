package com.chaomixian.vflow.core.workflow.module.interaction

import android.accessibilityservice.AccessibilityService // Android 框架类
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.permissions.PermissionManager
// 为项目内的 AccessibilityService 设置别名，以区分 Android 框架的同名类
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred

// 文件：ClickModule.kt
// 描述：定义了通过无障碍服务在屏幕上执行点击操作的模块。
//      可以点击屏幕元素、特定坐标或通过视图ID定位的视图。

/**
 * 点击模块。
 * 使用无障碍服务在屏幕上模拟点击操作。
 * 支持多种目标类型：ScreenElement（通过其边界）、Coordinate（精确坐标）或视图ID（文本）。
 */
class ClickModule : BaseModule() {
    // 模块的唯一ID
    override val id = "vflow.device.click"
    // 模块的元数据，用于在UI中展示
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素、坐标或视图ID", R.drawable.rounded_ads_click_24, "界面交互") // 更新分类
    // 此模块需要的权限列表
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标", // 点击的目标，可以是元素、坐标或ID
            staticType = ParameterType.STRING, // 静态类型为字符串，实际可以是多种动态类型
            acceptsMagicVariable = true,       // 允许使用魔法变量
            acceptedMagicVariableTypes = setOf( // 定义接受的魔法变量类型
                VTypeRegistry.SCREEN_ELEMENT.id,
                VTypeRegistry.COORDINATE.id,
                VTypeRegistry.STRING.id
            ),
            defaultValue = "" // 默认值为空字符串
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "点击成功", VTypeRegistry.BOOLEAN.id) // 输出一个布尔值表示点击是否成功
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如：“点击 [目标元素]”
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetPill = PillUtil.createPillFromParam(
            step.parameters["target"],
            getInputs().find { it.id == "target" }
        )
        return PillUtil.buildSpannable(
            context,
            "点击 ",
            targetPill
        )
    }

    /**
     * 执行点击操作的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取无障碍服务实例
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "执行点击需要无障碍服务，但该服务当前未运行。")

        // 获取点击目标，优先从魔法变量，其次从静态变量
        val target = context.magicVariables["target"] ?: context.variables["target"]

        // 根据目标的不同类型执行相应的点击逻辑
        val clickSuccess: Boolean = when (target) {
            is VScreenElement -> {
                onProgress(ProgressUpdate("正在点击找到的元素"))
                val bounds = target.bounds
                val node = findNodeByBounds(service, bounds)
                var success = false
                if (node != null) {
                    success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                }
                success || performGestureClick(service, bounds.centerX(), bounds.centerY(), onProgress)
            }
            is VCoordinate -> {
                onProgress(ProgressUpdate("正在点击坐标: (${target.x}, ${target.y})"))
                performGestureClick(service, target.x, target.y, onProgress)
            }
            is VString -> {
                val viewId = target.raw
                onProgress(ProgressUpdate("正在点击视图ID: $viewId"))
                performViewIdClick(service, viewId, onProgress)
            }
            is String -> {
                // 尝试解析为坐标
                val parts = target.split(",")
                if (parts.size == 2) {
                    val x = parts[0].trim().toIntOrNull()
                    val y = parts[1].trim().toIntOrNull()
                    if (x != null && y != null) {
                        onProgress(ProgressUpdate("正在点击坐标: ($x, $y)"))
                        performGestureClick(service, x, y, onProgress)
                    } else {
                        onProgress(ProgressUpdate("正在点击视图ID: $target"))
                        performViewIdClick(service, target, onProgress)
                    }
                } else {
                    onProgress(ProgressUpdate("正在点击视图ID: $target"))
                    performViewIdClick(service, target, onProgress)
                }
            }
            else -> {
                onProgress(ProgressUpdate("点击失败：目标无效"))
                false
            }
        }
        // 返回执行结果，包含点击是否成功的布尔值
        return ExecutionResult.Success(mapOf("success" to VBoolean(clickSuccess)))
    }

    /**
     * 通过视图ID查找 AccessibilityNodeInfo 并执行点击操作。
     * 如果直接的 ACTION_CLICK 失败，会尝试在该节点的边界中心执行手势点击。
     * @param service 无障碍服务实例。
     * @param viewId 要点击的视图的ID。
     * @param onProgress 进度更新回调。
     * @return 点击是否成功。
     */
    private suspend fun performViewIdClick(service: VFlowAccessibilityService, viewId: String, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        val node = findNodeByViewId(service, viewId) // 根据视图ID查找节点
        if (node == null) {
            onProgress(ProgressUpdate("视图ID '$viewId' 未找到"))
            return false
        }
        var clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) // 尝试标准点击
        if (!clickSuccess) { // 如果标准点击失败，尝试手势点击
            onProgress(ProgressUpdate("视图ID '$viewId' ACTION_CLICK 失败，尝试手势点击"))
            val bounds = Rect()
            node.getBoundsInScreen(bounds) // 获取节点在屏幕上的边界
            // 在节点中心执行手势点击
            clickSuccess = performGestureClick(service, bounds.centerX(), bounds.centerY(), onProgress)
        } else {
            onProgress(ProgressUpdate("已通过 ACTION_CLICK 成功点击视图ID '$viewId'"))
        }
        node.recycle() // 回收节点
        return clickSuccess
    }

    /**
     * 在指定坐标执行手势点击（短按）。
     * @param service 无障碍服务实例。
     * @param x 点击的x坐标。
     * @param y 点击的y坐标。
     * @param onProgress 进度更新回调。
     * @return 点击是否成功。
     */
    private suspend fun performGestureClick(service: VFlowAccessibilityService, x: Int, y: Int, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        // 检查坐标有效性
        if (x < 0 || y < 0) {
            onProgress(ProgressUpdate("手势点击失败：坐标 ($x, $y) 无效"))
            return false
        }
        // 创建点击路径和手势描述
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100L)) // 0ms延迟, 100ms持续时间
            .build()

        // 异步派发手势并等待回调
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                super.onCompleted(g)
                deferred.complete(true) // 手势完成
            }
            override fun onCancelled(g: GestureDescription?) {
                super.onCancelled(g)
                deferred.complete(false) // 手势取消
            }
        }, null)

        val success = deferred.await() // 等待手势结果
        if (success) onProgress(ProgressUpdate("已通过手势成功点击坐标: ($x, $y)"))
        else onProgress(ProgressUpdate("手势点击坐标 ($x, $y) 失败或被取消"))
        return success
    }

    /**
     * 根据视图ID查找 AccessibilityNodeInfo。
     * 注意：此函数返回的节点（如果找到）需要调用者负责回收。
     * 在查找过程中产生的其他节点会被此函数回收。
     * @param service 无障碍服务实例。
     * @param viewId 视图的资源ID名称 (例如 \"com.example.app:id/button1\")。
     * @return 找到的第一个匹配节点，未找到则返回 null。
     */
    private fun findNodeByViewId(service: VFlowAccessibilityService, viewId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null // 获取当前活动窗口的根节点
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) // 查找匹配ID的节点列表
        val nodeToReturn = nodes?.firstOrNull() // 取第一个匹配的节点

        // 回收获取到的节点列表中的其他节点（如果列表不为空且找到了要返回的节点）
        // 或者回收所有节点（如果未找到要返回的节点但列表不为空）
        if (nodeToReturn == null) {
            nodes?.forEach { it.recycle() }
        } else {
            nodes?.filter { it != nodeToReturn }?.forEach { it.recycle() }
        }
        // 通常不应回收 rootInActiveWindow 返回的根节点，除非服务文档明确指出
        // root.recycle();
        return nodeToReturn // 调用者需要回收此返回的节点
    }

    /**
     * 根据精确的屏幕边界 (Rect) 查找 AccessibilityNodeInfo。
     * 通过层级遍历查找与目标边界完全匹配的节点。
     * 注意：此函数返回的节点（如果找到）需要调用者负责回收。
     * 在查找过程中产生的其他节点会被此函数回收。
     * @param service 无障碍服务实例。
     * @param targetBounds 目标节点的精确屏幕边界。
     * @return 找到的匹配节点，未找到则返回 null。
     */
    private fun findNodeByBounds(service: VFlowAccessibilityService, targetBounds: Rect): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null // 获取根节点
        val queue = ArrayDeque<AccessibilityNodeInfo>() // 用于广度优先搜索的队列
        queue.add(AccessibilityNodeInfo.obtain(root)) // 从根节点开始搜索，需要复制一份节点以进行处理和回收
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val currentNode = queue.removeFirst()
            val nodeBounds = Rect()
            currentNode.getBoundsInScreen(nodeBounds) // 获取当前节点的屏幕边界

            if (nodeBounds == targetBounds) { // 如果边界完全匹配
                foundNode = AccessibilityNodeInfo.obtain(currentNode) // 复制找到的节点以返回
                // 清理队列中剩余的节点，并回收它们
                queue.forEach { it.recycle() }
                queue.clear()
                currentNode.recycle() // 回收当前处理的节点副本
                break // 已找到，跳出循环
            }

            // 将子节点加入队列继续搜索
            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { child -> queue.add(child) } // 子节点直接加入，它们会在后续迭代中被复制和回收
            }
            currentNode.recycle() // 回收当前处理的节点副本
        }
        // 如果 root 是通过 obtain() 获取的副本，则此处也应 root.recycle()。
        // 但由于 service.rootInActiveWindow 通常返回的是一个共享实例或由系统管理的实例，
        // 我们不在此回收原始的 root，仅回收通过 obtain(root) 创建的副本。
        return foundNode // 调用者需要回收此返回的节点
    }
}