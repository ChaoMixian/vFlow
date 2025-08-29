package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.services.AccessibilityService
import com.chaomixian.vflow.core.workflow.model.ActionStep


// 所有“动作”的统一规范
interface ActionModule {
    val id: String
    val metadata: ActionMetadata
    val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE)

    fun getParameters(): List<ParameterDefinition>

    /**
     * 当此模块被添加到编辑器时，它有机会创建并返回一个包含多个步骤的完整逻辑块。
     * @return 返回一个步骤列表。对于原子模块，只返回它自己。对于'If'模块，返回[if, else, endif]三个步骤。
     */
    fun createSteps(): List<ActionStep> {
        // 默认实现：只创建自身这一个步骤
        return listOf(ActionStep(id, getParameters().associate { it.id to it.defaultValue }))
    }

    /**
     * 当用户尝试删除此模块的一个步骤时，编辑器将调用此方法。
     * 模块可以根据自己的逻辑（例如，删除'else'只删除自己，删除'if'删除整个块）来修改列表。
     * @param steps 当前工作流的完整步骤列表（可修改）
     * @param position 被点击删除的步骤的位置
     * @return 如果列表被修改，返回true
     */
    fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        // 默认实现：只删除自己
        if (position > 0 && position < steps.size) { // 触发器不能删
            steps.removeAt(position)
            return true
        }
        return false
    }

    suspend fun execute(context: ExecutionContext): ActionResult
}

// 模块的自我描述信息
data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String // e.g., "界面交互", "逻辑控制"
)

// 动作执行结果
data class ActionResult(
    val success: Boolean,
    val output: Map<String, Any?> = emptyMap()
)