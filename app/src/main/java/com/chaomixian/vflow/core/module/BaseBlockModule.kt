package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * “积木块”类型模块的抽象基类 (例如 If, Loop)。
 * 它封装了创建和删除整个代码块的复杂逻辑，
 * 开发者只需定义组成块的步骤ID即可。
 */
abstract class BaseBlockModule : BaseModule() {

    /**
     * 开发者需要重写这个属性，提供组成一个完整积木块的所有步骤的ID。
     * 第一个ID应为“开始”模块的ID，最后一个为“结束”模块的ID。
     *
     * 例如: `listOf("if.start", "if.else", "if.end")`
     */
    abstract val stepIdsInBlock: List<String>

    /**
     * 开发者需要提供用于配对的唯一ID。
     * 例如: "if_block" 或 "loop_block"
     */
    abstract val pairingId: String

    override val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.BLOCK_START, pairingId)

    /**
     * 自动根据 `stepIdsInBlock` 创建一系列步骤。
     */
    final override fun createSteps(): List<ActionStep> {
        return stepIdsInBlock.map { moduleId -> ActionStep(moduleId, emptyMap()) }
    }

    /**
     * 自动处理删除整个积木块的逻辑。
     */
    final override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        val endPos = findBlockEndPosition(steps, position)
        if (endPos != position) {
            // 从后往前删除，避免索引错乱
            for (i in endPos downTo position) {
                steps.removeAt(i)
            }
            return true
        }
        // 如果找不到结束块，只删除自己
        return super.onStepDeleted(steps, position)
    }

    private fun findBlockEndPosition(steps: List<ActionStep>, startPosition: Int): Int {
        val startId = stepIdsInBlock.first()
        val endId = stepIdsInBlock.last()
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
}