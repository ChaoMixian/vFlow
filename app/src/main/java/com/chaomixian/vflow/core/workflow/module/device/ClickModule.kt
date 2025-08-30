// main/java/com/chaomixian/vflow/core/workflow/module/device/ClickModule.kt

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
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.TextVariable

@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable

class ClickModule : ActionModule {
    override val id = "vflow.device.click"
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素或坐标", R.drawable.ic_coordinate, "设备")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "target",
            name = "目标",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = true,
            // 明确声明可接受的魔法变量类型
            acceptedMagicVariableTypes = setOf(
                ScreenElement::class.java,
                Coordinate::class.java,
                TextVariable::class.java // 用于 "x,y" 格式的字符串
            )
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable::class.java)
    )

    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override suspend fun execute(context: ExecutionContext): ActionResult {
        val target = context.magicVariables["target"]
            ?: context.variables["target"] // 也检查静态参数

        val coordinate: Coordinate? = when (target) {
            is ScreenElement -> Coordinate(target.bounds.centerX(), target.bounds.centerY())
            is Coordinate -> target
            is TextVariable -> target.value.toCoordinate()
            is String -> target.toCoordinate()
            else -> null
        }

        if (coordinate == null) {
            Log.w("ClickModule", "没有有效的点击目标。")
            return ActionResult(true, mapOf("success" to BooleanVariable(false)))
        }

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
        return ActionResult(true, mapOf("success" to BooleanVariable(clickSuccess)))
    }

    // 辅助扩展函数，用于从 "x,y" 格式的字符串解析坐标
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
}