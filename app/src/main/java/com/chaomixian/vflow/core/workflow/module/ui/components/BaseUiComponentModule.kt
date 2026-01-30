// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/BaseUiComponentModule.kt
package com.chaomixian.vflow.core.workflow.module.ui.components
import com.chaomixian.vflow.core.types.VTypeRegistry

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.ui.blocks.KEY_UI_ELEMENTS_LIST
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.types.complex.VUiComponent

/**
 * UI 组件基类
 *
 * 所有 UI 组件（文本、输入框、按钮、开关）的抽象基类。
 * 负责将组件注册到 UI 元素列表中，并返回完整的组件对象。
 *
 * 工作流程：
 * 1. 组件被创建时会调用 createUiElement() 生成 UiElement
 * 2. UiElement 被添加到组件列表（存储在 namedVariables 中）
 * 3. 创建并返回 VUiComponent 对象供其他模块使用
 *
 * 输出：
 * - component: VUiComponent 对象（包含组件的所有信息和属性）
 * - id: 组件 ID（向后兼容）
 *
 * 子类需要实现：
 * - createUiElement(): 定义如何创建 UI 元素
 * - id, metadata: 模块标识和元数据
 * - getInputs(): 定义组件的参数
 */
abstract class BaseUiComponentModule : BaseModule() {
    /**
     * 子类实现此方法来定义如何创建 UI 元素
     */
    abstract fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement

    override fun getOutputs(step: ActionStep?) = listOf(
        OutputDefinition("component", "组件对象", "vflow.type.uicomponent"),
        OutputDefinition("id", "组件ID", VTypeRegistry.STRING.id)
    )

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        @Suppress("UNCHECKED_CAST")
        val list = context.namedVariables[KEY_UI_ELEMENTS_LIST] as? MutableList<UiElement>

        if (list != null) {
            val element = createUiElement(context, step = context.allSteps[context.currentStepIndex])
            list.add(element)
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_ui_component_registered, element.id)))

            // 保存对 namedVariables 的引用，用于动态获取组件值
            // 在事件循环中，组件值会被更新到 namedVariables["component_value.$componentId"]
            val valueProvider = {
                // 延迟获取：在访问 value 时才从 namedVariables 中读取最新值
                context.namedVariables["component_value.${element.id}"]
            }

            // 尝试获取当前值（如果已存在）
            val actualValue = valueProvider()

            // 创建并返回 VUiComponent 对象，传入 valueProvider 以支持动态获取值
            val vComponent = VUiComponent(
                element,
                actualValue ?: element.defaultValue,
                valueProvider
            )
            return ExecutionResult.Success(mapOf(
                "component" to vComponent,
                "id" to VString(element.id)
            ))
        } else {
            return ExecutionResult.Failure("位置错误", appContext.getString(R.string.error_vflow_ui_wrong_position))
        }
    }
}
