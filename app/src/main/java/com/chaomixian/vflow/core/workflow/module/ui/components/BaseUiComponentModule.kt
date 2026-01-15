// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/BaseUiComponentModule.kt
package com.chaomixian.vflow.core.workflow.module.ui.components

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
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
        OutputDefinition("id", "组件ID", TextVariable.TYPE_NAME)
    )

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        @Suppress("UNCHECKED_CAST")
        val list = context.namedVariables[KEY_UI_ELEMENTS_LIST] as? MutableList<UiElement>

        if (list != null) {
            val element = createUiElement(context, step = context.allSteps[context.currentStepIndex])
            list.add(element)
            onProgress(ProgressUpdate("已注册组件: ${element.id}"))

            // 创建并返回 VUiComponent 对象
            val vComponent = VUiComponent(element, element.defaultValue)
            return ExecutionResult.Success(mapOf(
                "component" to vComponent,
                "id" to TextVariable(element.id)
            ))
        } else {
            return ExecutionResult.Failure("位置错误", "UI 组件必须放置在\"创建界面\"积木块内部。")
        }
    }
}
