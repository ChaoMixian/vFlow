package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission

// 文件：BaseModule.kt
// 描述：为 ActionModule 接口提供一个便捷的抽象基类。
//      它为许多方法提供了默认实现，简化了具体模块的开发。

/**
 * ActionModule 接口的标准抽象基类。
 * 提供了大多数方法的默认实现，使得具体模块开发者可以更专注于核心功能的实现。
 */
abstract class BaseModule : ActionModule {

    // 模块所需的权限列表。默认实现为空列表，表示不需要任何特殊权限。
    override val requiredPermissions: List<Permission>
        get() = emptyList() // 默认模块不需要特定权限

    // 模块的积木块行为。默认实现为 BlockType.NONE，表示不是一个积木块的开始或结束。
    override val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE) // 默认不是积木块

    // 定义模块的静态输入参数。默认实现为空列表。
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 定义模块的输出参数。默认实现为空列表。
    // @param step 当前动作步骤实例，可用于动态确定输出（例如，根据输入参数）。
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    /**
     * 定义模块的动态输入参数。
     * 默认实现直接返回 getInputs() 的结果，适用于输入参数固定的模块。
     * 需要根据上下文动态改变输入参数的模块（如 IfModule）应重写此方法。
     * @param step 当前动作步骤实例。
     * @param allSteps 工作流中的所有动作步骤列表，可用于上下文判断。
     * @return 动态输入参数定义列表。
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        return getInputs() // 默认返回静态输入
    }

    /**
     * 当一个参数在编辑器中被用户更新后，此函数会被调用。
     * 它允许模块根据一个参数的变动，来动态修改其他参数的值或状态。
     *
     * @param step 当前的 ActionStep 实例，包含更新前的参数。
     * @param updatedParameterId 刚刚被用户修改的参数的 ID。
     * @param updatedValue 新的参数值。
     * @return 返回一个新的 Map，代表更新后的完整参数集。
     * 默认实现是简单地将新值应用到现有参数中。
     */
    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue
        return newParameters
    }

    /**
     * 创建此模块的一个或多个默认动作步骤实例。
     * 默认实现会基于 getInputs() 中定义的参数及其 defaultValue 创建一个 ActionStep。
     * @return 包含新创建的 ActionStep 的列表。
     */
    override fun createSteps(): List<ActionStep> {
        // 从输入定义中提取ID和非空的默认值，构建参数映射
        val defaultParams = getInputs()
            .filter { it.defaultValue != null } // 只包含有默认值的参数
            .associate { it.id to it.defaultValue!! } // 断言 defaultValue 不为 null
        return listOf(ActionStep(id, defaultParams)) // 使用模块ID和默认参数创建步骤
    }

    /**
     * 处理当此模块的一个步骤从工作流中被删除时的逻辑。
     * 默认实现仅移除指定位置的步骤，适用于非积木块模块。
     * 积木块模块（如 If/Loop）需要重写此方法以处理其内部步骤。
     * @param steps 当前工作流中的步骤列表（可修改）。
     * @param position 被删除步骤在此列表中的位置。
     * @return 如果成功处理了删除（例如，移除了步骤），则返回 true；否则返回 false。
     */
    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position >= 0 && position < steps.size) {
            steps.removeAt(position) // 标准模块直接移除自身
            return true
        }
        return false
    }

    /**
     * 验证指定动作步骤的参数是否有效。
     * 默认实现认为所有参数都是有效的。
     * 具体模块应根据其参数要求重写此方法以提供实际的验证逻辑。
     * @param step 要验证的动作步骤。
     * @return ValidationResult 对象，包含验证状态和错误消息（如果无效）。
     */
    override fun validate(step: ActionStep): ValidationResult {
        return ValidationResult(isValid = true) // 默认所有参数均有效
    }

    // 提供自定义编辑器UI的接口。默认实现为 null，表示不使用自定义UI，
    // 编辑器将基于 getInputs() 定义自动生成标准输入控件。
    override val uiProvider: ModuleUIProvider?
        get() = null // 默认不提供自定义编辑器UI
}