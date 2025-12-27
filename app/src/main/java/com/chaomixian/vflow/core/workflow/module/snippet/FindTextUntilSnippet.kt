// 文件: main/java/com/chaomixian/vflow/core/workflow/module/snippet/FindTextUntilSnippet.kt
package com.chaomixian.vflow.core.workflow.module.snippet

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.interaction.FindTextModule
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import com.chaomixian.vflow.core.workflow.module.logic.*
import com.chaomixian.vflow.core.workflow.module.system.DelayModule
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.UUID

/**
 * “查找直到”的复杂动作模板。
 * 这个模块本身不执行任何操作，而是作为模板生成器，
 * 一键创建“循环直到找到文本”的流程。
 *
 * 采用 Do-While 逻辑 (While True + Break) 以避免前向引用变量的问题。
 * 修复：使用 count > 0 作为判断条件，避免因变量未生成导致的误判。
 * 更新：默认匹配模式改为“包含”。
 */
class FindTextUntilSnippet : BaseModule() {

    override val id = "vflow.snippet.find_until"
    override val metadata = ActionMetadata(
        name = "查找直到",
        description = "在屏幕上循环查找指定的文本，直到找到为止。",
        iconRes = R.drawable.rounded_search_24, // 使用搜索图标
        category = "模板"
    )

    // 这些输入目前仅用于显示，createSteps 中使用的是默认值
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
        val ifId = "if_${UUID.randomUUID()}"
        val delayId = "delay_${UUID.randomUUID()}"

        // 1. 无限循环 (Condition: "1" is not empty)
        val whileStep = ActionStep(
            moduleId = WhileModule().id,
            id = whileId,
            parameters = mapOf(
                "input1" to "1",
                "operator" to OP_IS_NOT_EMPTY,
                "value1" to null
            )
        )

        // 2. 查找文本
        val findStep = ActionStep(
            moduleId = FindTextModule().id,
            id = findId,
            parameters = mapOf(
                "matchMode" to "包含", // 默认为“包含”模式，适应性更强
                "targetText" to "需要查找的文本",
                "outputFormat" to "元素"
            )
        )

        // 3. 如果找到 (条件: count > 0)
        // 相比 OP_EXISTS，检查数量大于 0 是绝对可靠的，因为 count 变量总是存在（0或更多）
        val ifStep = ActionStep(
            moduleId = IfModule().id,
            id = ifId,
            parameters = mapOf(
                "input1" to "{{${findId}.count}}",
                "operator" to OP_NUM_GT, // 大于
                "value1" to 0
            )
        )

        // 4. 跳出循环 (Break)
        val breakStep = ActionStep(
            moduleId = BreakLoopModule().id,
            parameters = emptyMap()
        )

        // 5. 结束如果 (EndIf)
        val endIfStep = ActionStep(
            moduleId = EndIfModule().id,
            parameters = emptyMap()
        )

        // 6. 延迟 (没找到时等待)
        val delayStep = ActionStep(
            moduleId = DelayModule().id,
            id = delayId,
            parameters = mapOf("duration" to 1000L)
        )

        // 7. 结束循环 (EndWhile)
        val endWhileStep = ActionStep(
            moduleId = EndWhileModule().id,
            parameters = emptyMap()
        )

        return listOf(
            whileStep,
            findStep,
            ifStep,
            breakStep,
            endIfStep,
            delayStep,
            endWhileStep
        )
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        return ExecutionResult.Success()
    }
}