// main/java/com/chaomixian/vflow/core/workflow/module/logic/LoopModule.kt

package com.chaomixian.vflow.modules.logic

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.NumberVariable
import java.lang.Exception

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : ActionModule {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, LOOP_PAIRING_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override fun createSteps(): List<ActionStep> {
        // 使用 getInputs 的默认值创建步骤
        val defaultParams = getInputs().associate { it.id to it.defaultValue }
        return listOf(
            ActionStep(LOOP_START_ID, defaultParams),
            ActionStep(LOOP_END_ID, emptyMap())
        )
    }

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        val endPos = findBlockEndPosition(steps, position, LOOP_START_ID, LOOP_END_ID)
        if (endPos != position) {
            // 从后往前删除以避免索引问题
            for (i in endPos downTo position) {
                steps.removeAt(i)
            }
            return true
        }
        return false
    }

    override fun validate(step: ActionStep): ValidationResult {
        val count = step.parameters["count"]
        val countAsLong = when (count) {
            is String -> count.toLongOrNull()
            is Number -> count.toLong()
            else -> null
        }

        if (countAsLong == null && (count as? String)?.startsWith("{{") != true) {
            return ValidationResult(false, "无效的数字格式")
        }

        if (countAsLong != null && countAsLong <= 0) {
            return ValidationResult(false, "循环次数必须大于0")
        }

        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        // LoopModule 的 execute 仅作为循环开始的标记。
        // 实际的循环逻辑由 WorkflowExecutor 处理。
        // 我们可以在这里传递一些初始信息。
        val countValue = context.magicVariables["count"] ?: context.variables["count"]
        onProgress(ProgressUpdate("循环开始，次数: $countValue"))
        return ActionResult(success = true)
    }
}

class EndLoopModule : ActionModule {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        onProgress(ProgressUpdate("循环迭代结束"))
        return ActionResult(success = true)
    }
}

private fun findBlockEndPosition(steps: List<ActionStep>, startPosition: Int, startId: String, endId: String): Int {
    var openBlocks = 1
    for (i in (startPosition + 1) until steps.size) {
        val currentId = steps[i].moduleId
        if (currentId == startId) {
            openBlocks++
        } else if (currentId == endId) {
            openBlocks--
            if (openBlocks == 0) return i
        }
    }
    return startPosition
}