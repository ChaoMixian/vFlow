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
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素、坐标或视图ID", R.drawable.ic_coordinate, "设备")
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

        val coordinate: Coordinate? = when (target) {
            is ScreenElement -> Coordinate(target.bounds.centerX(), target.bounds.centerY())
            is Coordinate -> target
            is TextVariable -> target.value.toCoordinate() ?: findNodeByViewId(service, target.value)?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.recycle()
                Coordinate(bounds.centerX(), bounds.centerY())
            }
            is String -> target.toCoordinate() ?: findNodeByViewId(service, target)?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.recycle()
                Coordinate(bounds.centerX(), bounds.centerY())
            }
            else -> null
        }

        if (coordinate == null) {
            onProgress(ProgressUpdate("点击失败：目标无效"))
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(false)))
        }

        onProgress(ProgressUpdate("正在点击坐标: (${coordinate.x}, ${coordinate.y})"))

        val path = Path().apply { moveTo(coordinate.x.toFloat(), coordinate.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
        }, null)

        val clickSuccess = deferred.await()
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(clickSuccess)))
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
}