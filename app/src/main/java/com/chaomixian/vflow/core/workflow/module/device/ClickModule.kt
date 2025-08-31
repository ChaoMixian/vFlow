package com.chaomixian.vflow.modules.device

import com.chaomixian.vflow.services.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.TextVariable
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.parcelize.Parcelize

@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable

class ClickModule : BaseModule() {
    override val id = "vflow.device.click"
    override val metadata = ActionMetadata("点击", "点击一个屏幕元素或坐标", R.drawable.ic_coordinate, "设备")
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

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

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
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
    ): ExecutionResult {
        // 从服务容器中获取无障碍服务
        val service = context.services.get(AccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "执行点击需要无障碍服务，但该服务当前未运行。")

        val target = context.magicVariables["target"] ?: context.variables["target"]

        val coordinate: Coordinate? = when (target) {
            is ScreenElement -> Coordinate(target.bounds.centerX(), target.bounds.centerY())
            is Coordinate -> target
            is TextVariable -> target.value.toCoordinate()
            is String -> target.toCoordinate()
            else -> null
        }

        if (coordinate == null) {
            return ExecutionResult.Failure("目标无效", "没有提供有效的点击目标或坐标格式不正确。")
        }

        onProgress(ProgressUpdate("正在点击坐标: (${coordinate.x}, ${coordinate.y})"))

        val path = Path().apply { moveTo(coordinate.x.toFloat(), coordinate.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
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
}