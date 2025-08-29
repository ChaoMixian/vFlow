package com.chaomixian.vflow.modules.triggers

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*

class ManualTriggerModule : ActionModule {
    override val id = "vflow.trigger.manual"
    override val metadata = ActionMetadata(
        name = "手动触发",
        description = "通过点击按钮手动启动此工作流",
        iconRes = R.drawable.ic_play_arrow,
        category = "触发器"
    )
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    // 触发器模块本身不执行操作，只作为工作流的起点
    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}