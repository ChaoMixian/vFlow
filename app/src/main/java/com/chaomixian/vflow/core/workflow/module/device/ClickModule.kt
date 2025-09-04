package com.chaomixian.vflow.core.workflow.module.device // Corrected package

import android.accessibilityservice.AccessibilityService // Android framework class
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
// Corrected imports for Variable types
import com.chaomixian.vflow.core.workflow.module.data.BooleanVariable
import com.chaomixian.vflow.core.workflow.module.data.TextVariable
// Added imports for ScreenElement and Coordinate from the same package (device)
import com.chaomixian.vflow.core.workflow.module.device.ScreenElement
import com.chaomixian.vflow.core.workflow.module.device.Coordinate
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService // Alias for project's service
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred

class ClickModule : BaseModule() {
    override val id = "vflow.device.click"
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素、坐标或视图ID", R.drawable.rounded_ads_click_24, "设备")
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(
                ScreenElement.TYPE_NAME, // Uses imported ScreenElement
                Coordinate.TYPE_NAME,    // Uses imported Coordinate
                TextVariable.TYPE_NAME   // Uses imported TextVariable
            ),
            defaultValue = ""
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "点击成功", BooleanVariable.TYPE_NAME) // Uses imported BooleanVariable
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetValue = step.parameters["target"]?.toString() ?: "..."
        val isVariable = targetValue.startsWith("{{")

        return PillUtil.buildSpannable(
            context,
            "点击 ",
            PillUtil.Pill(targetValue, isVariable, parameterId = "target")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "执行点击需要无障碍服务，但该服务当前未运行。")

        val target = context.magicVariables["target"] ?: context.variables["target"]

        val clickSuccess = when (target) {
            is ScreenElement -> { // Uses imported ScreenElement
                onProgress(ProgressUpdate("正在点击找到的元素"))
                val node = findNodeByBounds(service, target.bounds)
                var success = false
                if (node != null) {
                    success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle() // Ensure recycle after use
                }
                // If direct click on node failed or node not found by bounds, try gesture on original bounds
                success || performGestureClick(service, target.bounds.centerX(), target.bounds.centerY(), onProgress)
            }
            is Coordinate -> { // Uses imported Coordinate
                onProgress(ProgressUpdate("正在点击坐标: (${target.x}, ${target.y})"))
                performGestureClick(service, target.x, target.y, onProgress)
            }
            is TextVariable -> { // Uses imported TextVariable
                val viewId = target.value
                onProgress(ProgressUpdate("正在点击视图ID: $viewId"))
                performViewIdClick(service, viewId, onProgress)
            }
            is String -> {
                val coordinate = target.toCoordinate() // toCoordinate returns imported Coordinate
                if (coordinate != null) {
                    onProgress(ProgressUpdate("正在点击坐标: (${coordinate.x}, ${coordinate.y})"))
                    performGestureClick(service, coordinate.x, coordinate.y, onProgress)
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
        // Uses imported BooleanVariable
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(clickSuccess)))
    }

    private suspend fun performViewIdClick(service: VFlowAccessibilityService, viewId: String, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        val node = findNodeByViewId(service, viewId)
        if (node == null) {
            onProgress(ProgressUpdate("视图ID '$viewId' 未找到"))
            return false
        }
        var clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clickSuccess) { // If direct click fails, try gesture on bounds
            onProgress(ProgressUpdate("视图ID '$viewId' ACTION_CLICK 失败，尝试手势点击"))
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            clickSuccess = performGestureClick(service, bounds.centerX(), bounds.centerY(), onProgress)
        } else {
            onProgress(ProgressUpdate("已通过 ACTION_CLICK 成功点击视图ID '$viewId'"))
        }
        node.recycle()
        return clickSuccess
    }

    private suspend fun performGestureClick(service: VFlowAccessibilityService, x: Int, y: Int, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        if (x < 0 || y < 0) {
            onProgress(ProgressUpdate("手势点击失败：坐标 ($x, $y) 无效"))
            return false
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100L)) // Duration must be Long
            .build()
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                super.onCompleted(g)
                deferred.complete(true)
            }
            override fun onCancelled(g: GestureDescription?) {
                super.onCancelled(g)
                deferred.complete(false)
            }
        }, null)
        val success = deferred.await()
        if (success) onProgress(ProgressUpdate("已通过手势成功点击坐标: ($x, $y)"))
        else onProgress(ProgressUpdate("手势点击坐标 ($x, $y) 失败或被取消"))
        return success
    }

    private fun String.toCoordinate(): Coordinate? {
        return try {
            val parts = this.split(',')
            if (parts.size == 2) {
                Coordinate(parts[0].trim().toInt(), parts[1].trim().toInt())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun findNodeByViewId(service: VFlowAccessibilityService, viewId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val nodeToReturn = nodes?.firstOrNull()
        if (nodeToReturn == null) { // If no node is found, or list is null/empty
            nodes?.forEach { it.recycle() } // Recycle all if any were in the list
        } else {
            // If a node is being returned, recycle all OTHER nodes from the list
            nodes?.filter { it != nodeToReturn }?.forEach { it.recycle() }
        }
        // root.recycle(); // Generally, rootInActiveWindow should not be recycled by the caller unless explicitly documented by the service.
        return nodeToReturn // Caller is responsible for recycling the returned node
    }

    private fun findNodeByBounds(service: VFlowAccessibilityService, targetBounds: Rect): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root)) 
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val currentNode = queue.removeFirst()
            val nodeBounds = Rect()
            currentNode.getBoundsInScreen(nodeBounds)

            if (nodeBounds == targetBounds) { // Using == for Rect comparison
                foundNode = AccessibilityNodeInfo.obtain(currentNode) 
                queue.forEach { it.recycle() } 
                queue.clear()
                currentNode.recycle() 
                break 
            }

            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { child -> queue.add(child) }
            }
            currentNode.recycle() 
        }
        return foundNode 
    }
}