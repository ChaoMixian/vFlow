package com.chaomixian.vflow.core.module

import android.content.Context
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission


/**
 * 核心模块接口（已重构）。
 * 这是所有模块（包括基类）必须遵循的最终契约。
 */
interface ActionModule {
    val id: String
    val metadata: ActionMetadata
    val blockBehavior: BlockBehavior

    /**
     * 统一的输出定义方法。
     * @param step 如果不为null，可以根据步骤的参数动态返回输出。
     */
    fun getOutputs(step: ActionStep? = null): List<OutputDefinition>

    fun getInputs(): List<InputDefinition>

    /**
     * 允许模块根据当前步骤的参数状态来动态调整其输入项。
     * @param step 当前正在编辑的步骤，包含其参数。
     * @param allSteps 整个工作流的步骤列表，用于上下文分析（例如，解析魔法变量的类型）。
     * @return 一个根据当前状态生成的 InputDefinition 列表。
     */
    fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition>

    /**
     * 生成在工作流步骤卡片上显示的紧凑摘要。
     */
    fun getSummary(context: Context, step: ActionStep): CharSequence? = null

    val uiProvider: ModuleUIProvider?

    /**
     * 声明模块运行所需的权限列表。
     */
    val requiredPermissions: List<Permission>

    fun validate(step: ActionStep): ValidationResult

    fun createSteps(): List<ActionStep>

    fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean

    /**
     * 模块的核心执行逻辑。
     * @return ExecutionResult 包含成功或失败的详细信息。
     */
    suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult
}