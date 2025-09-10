// 文件：ActionModule.kt
// 描述：定义了所有可执行模块必须实现的核心接口。
//      它规定了模块的基本属性、参数定义、UI交互、验证和执行逻辑。

package com.chaomixian.vflow.core.module

import android.content.Context
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission

// 文件：ActionModule.kt
// 描述：定义了所有可执行模块必须实现的核心接口。
//      它规定了模块的基本属性、参数定义、UI交互、验证和执行逻辑。

/**
 * 核心模块接口。
 * 所有在工作流中可执行的动作模块都必须实现此接口。
 * 它定义了模块的标识、元数据、行为、参数、UI、验证和执行等方面的契约。
 */
interface ActionModule {
    /** 模块的唯一标识符。 */
    val id: String

    /** 模块的元数据，用于在UI中展示（名称、描述、图标、分类等）。 */
    val metadata: ActionMetadata

    /** 模块的积木块行为定义，指示其是否为积木块的一部分以及如何表现。 */
    val blockBehavior: BlockBehavior

    /**
     * 获取模块的输出参数定义。
     * @param step 可选参数，当前的动作步骤实例。如果提供，模块可以根据步骤的当前参数动态确定其输出。
     * @return 输出参数定义列表。
     */
    fun getOutputs(step: ActionStep? = null): List<OutputDefinition>

    /**
     * 获取模块的静态输入参数定义。
     * 这些是模块固有的输入，不随上下文变化（除非被 getDynamicInputs 覆盖）。
     * @return 静态输入参数定义列表。
     */
    fun getInputs(): List<InputDefinition>

    /**
     * 获取模块的动态输入参数定义。
     * 允许模块根据当前步骤的参数状态或整个工作流的上下文来动态调整其输入项。
     * @param step 当前正在编辑或执行的步骤，包含其参数。
     * @param allSteps 整个工作流的步骤列表，可用于上下文分析（例如，解析魔法变量的类型）。
     * @return 根据当前状态生成的输入参数定义列表。
     */
    fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition>

    /**
     * 生成在工作流步骤卡片上显示的紧凑摘要文本。
     * 如果返回 null，则步骤卡片上可能不显示摘要或显示通用文本。
     * @param context Android 上下文。
     * @param step 当前动作步骤实例。
     * @return 模块摘要的 CharSequence，或 null。
     */
    fun getSummary(context: Context, step: ActionStep): CharSequence? = null

    /**
     * 模块的用户界面提供者。
     * 如果模块需要自定义的参数编辑界面，则通过此属性提供一个 ModuleUIProvider 实现。
     * 如果为 null，则编辑器会尝试基于 getInputs() 定义自动生成标准UI。
     */
    val uiProvider: ModuleUIProvider?

    /**
     * 声明模块运行所需的Android权限列表。
     * 执行引擎会在执行前检查并请求这些权限。
     */
    val requiredPermissions: List<Permission>

    /**
     * 验证指定动作步骤的参数是否有效。
     * @param step 要验证的动作步骤。
     * @return ValidationResult 对象，包含验证状态和错误消息（如果无效）。
     */
    fun validate(step: ActionStep): ValidationResult

    /**
     * 创建此模块的一个或多个默认动作步骤实例。
     * 当用户从模块列表中选择此模块并添加到工作流时调用。
     * @return 包含新创建的 ActionStep 的列表。
     */
    fun createSteps(): List<ActionStep>

    /**
     * 处理当此模块的一个步骤从工作流中被删除时的逻辑。
     * 对于非积木块模块，通常只需简单删除该步骤。
     * 对于积木块模块（如 If/Loop），需要处理其内部或配对的步骤。
     * @param steps 当前工作流中的步骤列表（可修改）。
     * @param position 被删除步骤在此列表中的位置。
     * @return 如果成功处理了删除（例如，移除了步骤），则返回 true；否则返回 false。
     */
    fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean

    /**
     * 当一个参数在编辑器中被用户更新后，此函数会被调用。
     * 它允许模块根据一个参数的变动，来动态修改其他参数的值或状态。
     *
     * @param step 当前的 ActionStep 实例，包含更新前的参数。
     * @param updatedParameterId 刚刚被用户修改的参数的 ID。
     * @param updatedValue 新的参数值。
     * @return 返回一个新的 Map，代表更新后的完整参数集。
     */
    fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?>

    /**
     * 模块的核心执行逻辑。
     * 这是一个挂起函数，允许执行异步操作。
     * @param context 执行上下文，包含变量、魔法变量、服务等。
     * @param onProgress 用于报告执行进度的回调函数。
     * @return ExecutionResult 对象，表示执行的结果（成功、失败或信号）。
     */
    suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult
}