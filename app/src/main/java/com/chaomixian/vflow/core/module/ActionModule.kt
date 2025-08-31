package com.chaomixian.vflow.core.module

import android.content.Context
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.workflow.model.ActionStep

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
     * 生成在工作流步骤卡片上显示的紧凑摘要。
     */
    fun getSummary(context: Context, step: ActionStep): CharSequence? = null

    val uiProvider: ModuleUIProvider?

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