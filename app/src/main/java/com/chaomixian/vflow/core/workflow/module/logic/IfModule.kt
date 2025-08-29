package com.chaomixian.vflow.modules.logic

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

// --- 模块ID常量，用于逻辑关联 ---
const val IF_PAIRING_ID = "if" // 用于配对
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

// --- “如果”模块：块的开始 ---
class IfModule : ActionModule {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, IF_PAIRING_ID)

    override fun getParameters(): List<ParameterDefinition> {
        return listOf(ParameterDefinition("condition", "条件", ParameterType.STRING))
    }

    override fun createSteps(): List<ActionStep> {
        return listOf(
            ActionStep(IF_START_ID, getParameters().associate { it.id to it.defaultValue }),
            ActionStep(ELSE_ID, emptyMap()),
            ActionStep(IF_END_ID, emptyMap())
        )
    }

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        // 删除“如果”块的开始，会删除整个块
        val endPos = findBlockEndPosition(steps, position, IF_START_ID, IF_END_ID)
        if (endPos != position) {
            // 从后往前删除，避免索引错乱
            for (i in endPos downTo position) {
                steps.removeAt(i)
            }
            return true
        }
        return false // 异常情况，不允许删除
    }

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

// --- “否则”模块：块的中间 ---
class ElseModule : ActionModule {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID)
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        // 用户可以单独删除“否则”块
        if (position > 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

// --- “结束如果”模块：块的结束 ---
class EndIfModule : ActionModule {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getParameters(): List<ParameterDefinition> = emptyList()
    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

// 辅助函数，用于在模块内部查找块的结束位置
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