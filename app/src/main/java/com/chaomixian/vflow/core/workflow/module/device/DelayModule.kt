// main/java/com/chaomixian/vflow/core/workflow/module/device/DelayModule.kt

package com.chaomixian.vflow.modules.device

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.modules.variable.NumberVariable
import kotlinx.coroutines.delay

class DelayModule : ActionModule {
    override val id = "vflow.device.delay"
    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.ic_workflows,
        category = "设备"
    )

    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "延迟时间 (毫秒)",
            staticType = ParameterType.NUMBER,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", Boolean::class.java as Class<Parcelable>)
    )

    override suspend fun execute(context: ExecutionContext): ActionResult {
        val durationValue = context.magicVariables["duration"]
        val duration = when(durationValue) {
            is NumberVariable -> durationValue.value.toLong()
            is Number -> durationValue.toLong()
            else -> (context.variables["duration"] as? String)?.toLongOrNull() ?: 1000L
        }

        delay(duration)
        return ActionResult(success = true, outputs = mapOf("success" to true))
    }
}