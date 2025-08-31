package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 标准模块的抽象基类。
 * 它为 ActionModule 接口中的大多数方法提供了默认实现，
 * 开发者只需关注模块的核心逻辑。
 */
abstract class BaseModule : ActionModule {

    // --- 开发者通常需要实现的部分 ---

    override val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE)

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()


    // --- 开发者很少需要关心的部分 (已有默认实现) ---

    /**
     * 默认的步骤创建逻辑。
     * 它会根据 getInputs() 中定义的默认值自动创建一个步骤。
     */
    override fun createSteps(): List<ActionStep> {
        val defaultParams = getInputs()
            .associate { it.id to it.defaultValue }
            .filterValues { it != null }
        return listOf(ActionStep(id, defaultParams))
    }

    /**
     * 默认的删除逻辑。
     * 对于非积木块的普通模块，只需删除自己即可。
     */
    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position >= 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    /**
     * 默认的验证逻辑。
     * 默认所有参数都是有效的，除非开发者重写此方法。
     */
    override fun validate(step: ActionStep): ValidationResult {
        return ValidationResult(isValid = true)
    }

    /**
     * 默认不提供自定义UI。
     */
    override val uiProvider: ModuleUIProvider?
        get() = null
}