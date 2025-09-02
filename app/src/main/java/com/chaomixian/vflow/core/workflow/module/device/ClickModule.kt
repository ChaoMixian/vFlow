// 文件: main/java/com/chaomixian/vflow/core/workflow/module/device/ClickModule.kt

package com.chaomixian.vflow.modules.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.TextVariable
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
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
            staticType = ParameterType.STRING, // 静态类型为字符串，用于输入坐标 "x,y" 或视图ID
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(
                ScreenElement.TYPE_NAME,
                Coordinate.TYPE_NAME,
                TextVariable.TYPE_NAME
            ),
            defaultValue = ""
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "点击成功", BooleanVariable.TYPE_NAME)
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

        // 核心修改：根据输入类型，执行不同的点击操作
        val clickSuccess = when (target) {
            is ScreenElement -> {
                onProgress(ProgressUpdate("正在点击找到的元素"))
                // 尝试直接使用无障碍服务点击，如果失败则回退到坐标点击
                val node = findNodeByBounds(service, target.bounds)
                val success = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                if(node != null) node.recycle()
                success || performGestureClick(service, target.bounds.centerX(), target.bounds.centerY(), onProgress)
            }
            is Coordinate -> {
                onProgress(ProgressUpdate("正在点击坐标: (${target.x}, ${target.y})"))
                performGestureClick(service, target.x, target.y, onProgress)
            }
            is TextVariable -> {
                val viewId = target.value
                onProgress(ProgressUpdate("正在点击视图ID: $viewId"))
                performViewIdClick(service, viewId, onProgress)
            }
            is String -> {
                // 首先尝试作为坐标解析，如果失败则作为视图ID处理
                val coordinate = target.toCoordinate()
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

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(clickSuccess)))
    }

    // 新增私有函数：处理基于视图ID的点击
    private suspend fun performViewIdClick(service: VFlowAccessibilityService, viewId: String, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        val node = findNodeByViewId(service, viewId)
        if (node == null) {
            onProgress(ProgressUpdate("视图ID '$viewId' 未找到"))
            return false
        }
        val clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        if (!clickSuccess) {
            onProgress(ProgressUpdate("视图ID '$viewId' 不可点击，回退到坐标点击"))
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            return performGestureClick(service, bounds.centerX(), bounds.centerY(), onProgress)
        }
        onProgress(ProgressUpdate("已通过视图ID成功点击"))
        return true
    }

    // 新增私有函数：处理基于手势的点击
    private suspend fun performGestureClick(service: VFlowAccessibilityService, x: Int, y: Int, onProgress: suspend (ProgressUpdate) -> Unit): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
        }, null)
        val success = deferred.await()
        if (success) onProgress(ProgressUpdate("已通过手势成功点击"))
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
        root.recycle()
        return nodes?.firstOrNull()
    }

    private fun findNodeByBounds(service: VFlowAccessibilityService, bounds: Rect): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val node = root.findAccessibilityNodeInfosByText("").find {
            val nodeBounds = Rect()
            it.getBoundsInScreen(nodeBounds)
            nodeBounds.contains(bounds)
        }
        root.recycle()
        return node
    }
}