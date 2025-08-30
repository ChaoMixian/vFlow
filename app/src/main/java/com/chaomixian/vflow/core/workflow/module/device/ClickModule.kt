// main/java/com/chaomixian/vflow/core/workflow/module/device/ClickModule.kt

package com.chaomixian.vflow.modules.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.parcelize.Parcelize
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.TextVariable
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

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
            acceptedMagicVariableTypes = setOf(
                ScreenElement::class.java,
                Coordinate::class.java,
                TextVariable::class.java
            )
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable::class.java)
    )

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

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        val target = context.magicVariables["target"]
            ?: context.variables["target"]

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

        onProgress(ProgressUpdate("正在点击坐标: (${coordinate.x}, ${coordinate.y})"))

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