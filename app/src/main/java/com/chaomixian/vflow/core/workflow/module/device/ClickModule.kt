package com.chaomixian.vflow.modules.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.parcelize.Parcelize
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.BooleanVariable

// 模块内部定义自己的输入类型
@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable

class ClickModule : ActionModule {
    override val id = "vflow.device.click"
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素或坐标", R.drawable.ic_workflows, "设备")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标",
            staticType = ParameterType.STRING, // 静态输入时，可以手动输入坐标 "x,y"
            acceptsMagicVariable = true,
            // 明确声明只接受这两种类型的变量
            acceptedMagicVariableTypes = setOf(ScreenElement::class.java, Coordinate::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        // 所有执行模块都应输出一个布尔值表示成功与否
        OutputDefinition("success", "是否成功", BooleanVariable::class.java)
    )

    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override suspend fun execute(context: ExecutionContext): ActionResult {
        val target = context.magicVariables["target"]

        val coordinate: Coordinate? = when (target) {
            is ScreenElement -> Coordinate(target.bounds.centerX(), target.bounds.centerY())
            is Coordinate -> target
            else -> null
        }

        if (coordinate == null) {
            Log.w("ClickModule", "没有有效的点击目标。")
            // 即使没有目标，模块本身也算“成功”执行了（没有崩溃），但输出为false
            return ActionResult(true, mapOf("success" to false))
        }

        // ... (手势点击逻辑不变)
        val service = context.accessibilityService
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

        // 将布尔结果用 BooleanVariable 包装后输出
        return ActionResult(true, mapOf("success" to BooleanVariable(clickSuccess)))

    }
}