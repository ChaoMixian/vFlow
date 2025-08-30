package com.chaomixian.vflow.modules.device

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.execution.ExecutionContext
import kotlinx.coroutines.delay

class DelayModule : ActionModule {

    override val id = "vflow.device.delay"

    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.ic_workflows,
        category = "设备"
    )

    // --- 新增：实现 getInputs 方法 ---
    override fun getInputs(): List<InputDefinition> {
        return listOf(
            InputDefinition("duration", "延迟时间 (毫秒)", ParameterType.NUMBER)
        )
    }

    // --- 新增：实现 getOutputs 方法 ---
    override fun getOutputs(): List<OutputDefinition> {
        return listOf(
            OutputDefinition("success", "是否成功", Boolean::class.java as Class<Parcelable>)
        )
    }

    override fun getParameters(): List<ParameterDefinition> {
        return listOf(
            ParameterDefinition(
                id = "duration",
                name = "延迟时间 (毫秒)",
                type = ParameterType.NUMBER,
                defaultValue = 1000
            )
        )
    }

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // 从魔法变量或静态参数中获取延迟时间
        val duration = (context.magicVariables["duration"] as? Number)?.toLong()
            ?: (context.variables["duration"] as? Number)?.toLong()
            ?: 1000L

        delay(duration)

        // --- 修改：返回带有名为 "success" 的输出的 ActionResult ---
        return ActionResult(success = true, outputs = mapOf("success" to true))
    }
}