// 文件: FindTextUntilModule.kt
// 描述: 定义了一个“查找直到”的snippets，它会持续查找直到找到匹配的文本。

package com.chaomixian.vflow.core.workflow.module.snippet

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.interaction.FindTextModule
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import com.chaomixian.vflow.core.workflow.module.logic.EndWhileModule
import com.chaomixian.vflow.core.workflow.module.logic.OP_NOT_EXISTS
import com.chaomixian.vflow.core.workflow.module.logic.WhileModule
import com.chaomixian.vflow.core.workflow.module.system.DelayModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.UUID

/**
 * “查找直到”的复杂动作模板。
 * 这个模块本身不执行任何操作，而是作为模板生成器，
 * 一键创建“循环直到未找到文本”的流程，包含：
 * - While (条件：`{{findStepId.result}}` 不存在)
 * - 查找文本
 * - 延迟
 * - EndWhile
 */
class FindTextUntilModule : BaseModule() {

    override val id = "vflow.logic.find_until"
    override val metadata = ActionMetadata(
        name = "查找直到",
        description = "在屏幕上循环查找指定的文本，直到找到为止。",
        iconRes = R.drawable.rounded_search_24, // 使用搜索图标
        category = "模板"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        ),
        InputDefinition(
            id = "delay",
            name = "延迟（毫秒）",
            staticType = ParameterType.NUMBER,
            defaultValue = 1000L,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        return listOf(
            OutputDefinition(
                "result",
                "找到的元素",
                ScreenElement.TYPE_NAME
            )
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val targetPill = PillUtil.createPillFromParam(
            step.parameters["targetText"],
            inputs.find { it.id == "targetText" }
        )
        return PillUtil.buildSpannable(context, "查找直到找到文本 ", targetPill)
    }

    override fun createSteps(): List<ActionStep> {
        val whileId = "while_${UUID.randomUUID()}"
        val findId = "find_${UUID.randomUUID()}"
        val delayId = "delay_${UUID.randomUUID()}"

        val whileStep = ActionStep(
            moduleId = WhileModule().id,
            id = whileId,
            parameters = mapOf(
                "input1" to "{{${findId}.result}}",
                "operator" to OP_NOT_EXISTS,
                "value1" to null
            )
        )

        val findStep = ActionStep(
            moduleId = FindTextModule().id,
            id = findId,
            parameters = mapOf(
                "matchMode" to "完全匹配",
                "targetText" to "需要查找的文本",
                "outputFormat" to "元素"
            )
        )

        val delayStep = ActionStep(
            moduleId = DelayModule().id,
            id = delayId,
            parameters = mapOf("duration" to 1000L)
        )

        val endWhileStep = ActionStep(
            moduleId = EndWhileModule().id,
            parameters = emptyMap()
        )

        return listOf(
            whileStep,
            findStep,
            delayStep,
            endWhileStep
        )
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return ExecutionResult.Success()
    }
}