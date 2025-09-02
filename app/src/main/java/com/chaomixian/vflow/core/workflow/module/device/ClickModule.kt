// main/java/com/chaomixian/vflow/core/workflow/module/device/ClickModule.kt

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
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService // 使用别名避免冲突
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred

// 注意：ClickModule 内部也定义了 ScreenElement 和 Coordinate，因为它是自包含的。
// 理想情况下，如果多个模块都用到，可以考虑放到一个公共的 `model` 包中，但当前设计强调模块独立。

class ClickModule : BaseModule() {
    // 模块唯一ID
    override val id = "vflow.device.click"
    // 模块元数据
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素、坐标或视图ID", R.drawable.ic_coordinate, "设备")
    // 所需权限
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 定义输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            // 接受这些类型的变量，通过唯一的类型名称字符串来识别
            acceptedMagicVariableTypes = setOf(
                ScreenElement.TYPE_NAME,
                Coordinate.TYPE_NAME,
                TextVariable.TYPE_NAME
            )
        )
    )

    // 定义输出参数
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        // --- 核心修改：为输出定义条件选项 ---
        OutputDefinition(
            "success",
            "文本点击成功",
            BooleanVariable.TYPE_NAME,
            // 这个模块要成功不成功怪怪的
//            conditionalOptions = listOf(
//                ConditionalOption("成功", "成功"),
//                ConditionalOption("不成功", "不成功")
//            )
        )
    )

    // 定义编辑器摘要
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val targetValue = step.parameters["target"]?.toString() ?: "..."
        val isVariable = targetValue.startsWith("{{")
        val pillText = if (isVariable) "变量" else targetValue

        return PillUtil.buildSpannable(
            context,
            "点击 ",
            PillUtil.Pill(pillText, isVariable, parameterId = "target")
        )
    }

    // 核心执行逻辑
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 从服务容器中获取我们自己的无障碍服务
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "执行点击需要无障碍服务，但该服务当前未运行。")

        // 获取输入的目标值（可能是魔法变量或静态值）
        val target = context.magicVariables["target"] ?: context.variables["target"]

        // 智能判断输入类型，最终转换为一个坐标点
        val coordinate: Coordinate? = when (target) {
            // 如果是完整的屏幕元素，取其中心点
            is ScreenElement -> Coordinate(target.bounds.centerX(), target.bounds.centerY())
            // 如果直接是坐标，直接使用
            is Coordinate -> target
            // 如果是文本变量，尝试将其解析为坐标或视图ID
            is TextVariable -> target.value.toCoordinate() ?: findNodeByViewId(service, target.value)?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.recycle()
                Coordinate(bounds.centerX(), bounds.centerY())
            }
            // 如果是静态字符串，也尝试将其解析为坐标或视图ID
            is String -> target.toCoordinate() ?: findNodeByViewId(service, target)?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.recycle()
                Coordinate(bounds.centerX(), bounds.centerY())
            }
            else -> null
        }

        // 如果最终无法得到坐标，则执行失败
        if (coordinate == null) {
            onProgress(ProgressUpdate("点击失败：目标无效"))
            // --- 核心修改：即使失败，也返回 Success，但输出的布尔值为 false ---
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(false)))
        }

        onProgress(ProgressUpdate("正在点击坐标: (${coordinate.x}, ${coordinate.y})"))

        // 使用无障碍服务的手势功能来模拟点击
        val path = Path().apply { moveTo(coordinate.x.toFloat(), coordinate.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        // 异步等待手势执行结果
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
        }, null)

        val clickSuccess = deferred.await()
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(clickSuccess)))
    }

    /**
     * 辅助函数：将 "x,y" 格式的字符串转换为坐标对象。
     */
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

    /**
     * 辅助函数：通过视图ID查找屏幕上的UI节点。
     */
    private fun findNodeByViewId(service: VFlowAccessibilityService, viewId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        // 请注意：findAccessibilityNodeInfosByViewId 需要完整的 "包名:id/资源名" 格式
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        root.recycle()
        return nodes?.firstOrNull()
    }
}