package com.chaomixian.vflow.modules.device

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.execution.ExecutionContext
import kotlinx.coroutines.delay

class DelayModule : ActionModule {

    override val id = "vflow.device.delay"

    override val metadata = ActionMetadata(
        name = "延迟",
        description = "暂停工作流一段时间",
        iconRes = R.drawable.ic_workflows, // 复用旧图标
        category = "设备"
    )

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
        // 从执行上下文中获取参数
        val duration = (context.variables["duration"] as? Number)?.toLong() ?: 1000L

        // 执行核心逻辑
        delay(duration)

        // 返回成功结果
        return ActionResult(success = true)
    }
}