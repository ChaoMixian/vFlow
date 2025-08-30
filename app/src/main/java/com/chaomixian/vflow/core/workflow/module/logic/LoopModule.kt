// main/java/com/chaomixian/vflow/core/workflow/module/logic/LoopModule.kt

package com.chaomixian.vflow.modules.logic

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.NumberVariable

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : ActionModule { // <-- 实现接口
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, LOOP_PAIRING_ID)

    // `getParameters` 必须为空，因为所有用户可配置项都应在 `getInputs` 中定义
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable::class.java)
        )
    )

    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override fun createSteps(): List<ActionStep> {
        // 提供一个默认值
        return listOf(
            ActionStep(LOOP_START_ID, mapOf("count" to 5)),
            ActionStep(LOOP_END_ID, emptyMap())
        )
    }

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        val endPos = findBlockEndPosition(steps, position, LOOP_START_ID, LOOP_END_ID)
        if (endPos != position) {
            steps.subList(position, endPos + 1).clear()
            return true
        }
        return false
    }

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // 执行逻辑现在可以处理 NumberVariable
        val countValue = context.magicVariables["count"]
        val count = when (countValue) {
            is NumberVariable -> countValue.value.toInt()
            is Number -> countValue.toInt()
            else -> (context.variables["count"] as? String)?.toIntOrNull() ?: 5
        }
        // 实际的循环逻辑将在 WorkflowExecutor 中处理
        return ActionResult(success = true)
    }
}

class EndLoopModule : ActionModule {
    // ... EndLoopModule 保持不变 ...
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
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