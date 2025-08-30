package com.chaomixian.vflow.modules.logic

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : ActionModule {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata("循环", "重复执行一组操作固定的次数", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, LOOP_PAIRING_ID)

    // --- 新增/修改：实现接口 ---
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("count", "重复次数", ParameterType.NUMBER)
    )
    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override fun getParameters(): List<ParameterDefinition> {
        return listOf(
            ParameterDefinition("count", "重复次数", ParameterType.NUMBER, defaultValue = 5)
        )
    }

    // createSteps 和 onStepDeleted 保持不变...
    override fun createSteps(): List<ActionStep> {
        return listOf(
            ActionStep(LOOP_START_ID, getParameters().associate { it.id to it.defaultValue }),
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

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

class EndLoopModule : ActionModule {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata("结束循环", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    // --- 新增：实现接口 ---
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