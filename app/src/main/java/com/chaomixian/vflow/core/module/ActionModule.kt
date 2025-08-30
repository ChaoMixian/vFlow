// main/java/com/chaomixian/vflow/core/module/ActionModule.kt

package com.chaomixian.vflow.core.module

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep

interface ActionModule {
    val id: String
    val metadata: ActionMetadata
    val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE)

    fun getInputs(): List<InputDefinition>
    fun getOutputs(): List<OutputDefinition>
    fun getParameters(): List<ParameterDefinition>

    /**
     * 根据步骤的当前参数，获取动态的输出列表。
     * 这对于那些输出类型由用户在编辑器中决定的模块（如“设置变量”）至关重要。
     * @param step ActionStep 的实例，可以从中读取参数。
     * @return 一个只包含当前有效输出的列表。
     */
    fun getDynamicOutputs(step: ActionStep): List<OutputDefinition> {
        // 默认实现是返回静态的输出列表，以保持向后兼容
        return getOutputs()
    }

    fun createSteps(): List<ActionStep> {
        // 核心修复：创建步骤时，应使用 getParameters 的默认值
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

/**
 * 新接口：允许模块提供一个在 ActionStep 卡片中显示的自定义预览视图。
 */
interface ModuleWithPreview {
    /**
     * 根据当前步骤的参数，创建一个预览视图。
     * @param context 上下文
     * @param parent 父视图组
     * @param step 当前的 ActionStep 实例，包含所有参数
     * @return 一个用于预览的 View，如果不需要预览则返回 null
     */
    fun createPreviewView(context: Context, parent: ViewGroup, step: ActionStep): View?
}


/**
 * ViewHolder 模式，用于持有自定义编辑器视图的引用，避免在读取数据时重复查找。
 * @param view 自定义编辑器的主视图。
 */
abstract class CustomEditorViewHolder(val view: View)

/**
 * 一个接口，允许模块提供完全自定义的参数编辑器UI。
 * 如果一个模块实现了这个接口，ActionEditorSheet 将会调用它的方法来构建UI，
 * 而不是自己根据 getParameters() 来生成标准UI。
 */
interface ModuleWithCustomEditor {
    /**
     * 创建用于模块参数的自定义视图。
     * @param context 上下文
     * @param parent 父视图组
     * @param currentParameters 当前步骤已保存的参数，用于恢复UI状态
     * @param onParametersChanged 当自定义视图内部状态改变导致需要刷新时，可以调用此回调
     * @return 返回一个包含自定义视图的 ViewHolder
     */
    fun createEditorView(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder

    /**
     * 从自定义视图中读取用户输入的参数。
     * @param holder 包含自定义视图的 ViewHolder
     * @return 返回从UI中读取的、最新的参数Map
     */
    fun readParametersFromEditorView(holder: CustomEditorViewHolder): Map<String, Any?>
}


data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String
)

data class ActionResult(
    val success: Boolean,
    val outputs: Map<String, Any?> = emptyMap()
)