// main/java/com/chaomixian/vflow/core/module/ActionModule.kt

package com.chaomixian.vflow.core.module

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * ViewHolder 模式，用于持有自定义编辑器视图的引用。
 */
abstract class CustomEditorViewHolder(val view: View)

/**
 * 新接口：定义了模块UI的提供者。
 * 将所有 Android View 相关的逻辑从 ActionModule 中解耦出来。
 */
interface ModuleUIProvider {
    /**
     * 创建在 ActionStep 卡片中显示的自定义预览视图。
     */
    fun createPreview(context: Context, parent: ViewGroup, step: ActionStep): View?

    /**
     * 创建用于模块参数的自定义编辑器UI。
     */
    fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder

    /**
     * 从自定义编辑器UI中读取用户输入的参数。
     */
    fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?>

    /**
     * 核心修复：声明此UI提供者处理了哪些输入的UI渲染。
     * ActionEditorSheet 将不会为这些ID创建通用的UI。
     * @return 一个包含输入ID的Set。
     */
    fun getHandledInputIds(): Set<String>
}

/**
 * 核心模块接口（已重构）。
 * 现在是纯粹的逻辑接口，不直接依赖 Android UI 组件。
 */
interface ActionModule {
    val id: String
    val metadata: ActionMetadata
    val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.NONE)

    fun getInputs(): List<InputDefinition>
    fun getOutputs(): List<OutputDefinition>

    fun getDynamicOutputs(step: ActionStep): List<OutputDefinition> {
        return getOutputs()
    }

    /**
     * 新增：生成在工作流步骤卡片上显示的紧凑摘要。
     * @param context 安卓上下文，用于访问资源。
     * @param step 当前的动作步骤实例。
     * @return 一个 CharSequence，可以包含 Spans 以实现富文本效果（例如 "药丸"）。
     */
    fun getSummary(context: Context, step: ActionStep): CharSequence? = null

    val uiProvider: ModuleUIProvider? get() = null

    fun validate(step: ActionStep): ValidationResult {
        return ValidationResult(isValid = true)
    }

    fun createSteps(): List<ActionStep> {
        val defaultParams = getInputs().associate { it.id to it.defaultValue }.filterValues { it != null }
        return listOf(ActionStep(id, defaultParams))
    }

    fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position > 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult
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