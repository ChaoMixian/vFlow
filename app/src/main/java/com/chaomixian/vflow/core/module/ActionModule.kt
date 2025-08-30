package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep

interface ActionModule {
    val id: String
    val metadata: ActionMetadata
    val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE)

    // 使用新的 Input/Output 定义
    fun getInputs(): List<InputDefinition>
    fun getOutputs(): List<OutputDefinition>
    fun getParameters(): List<ParameterDefinition>

    // createSteps 和 onStepDeleted 保持不变
    fun createSteps(): List<ActionStep> {
        return listOf(ActionStep(id, getParameters().associate { it.id to it.defaultValue }))
    }

    fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position > 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    suspend fun execute(context: ExecutionContext): ActionResult
}

data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String
)

/**
 * 动作执行结果。
 * @param success 模块是否成功执行（用于逻辑判断）。
 * @param outputs 一个Map，包含此模块所有具名的输出结果。
 */
data class ActionResult(
    val success: Boolean,
    val outputs: Map<String, Any?> = emptyMap()
)