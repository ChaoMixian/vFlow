// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/BlockNavigator.kt
package com.chaomixian.vflow.core.workflow.module.logic

import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 积木块导航工具对象。
 * 提供了在工作流步骤列表中查找配对的开始、结束或中间块的通用方法。
 */
object BlockNavigator {

    /**
     * 从一个起始块开始，向前查找下一个配对的块。
     * @param steps 步骤列表。
     * @param startPosition 当前步骤的索引。
     * @param targetIds 目标模块ID的集合 (例如 Else 或 EndIf)。
     * @return 找到的步骤索引，未找到则返回 -1。
     */
    fun findNextBlockPosition(steps: List<ActionStep>, startPosition: Int, targetIds: Set<String>): Int {
        val startModule = ModuleRegistry.getModule(steps.getOrNull(startPosition)?.moduleId ?: return -1)
        val pairingId = startModule?.blockBehavior?.pairingId ?: return -1 // 获取当前块的配对ID
        var openBlocks = 1 // 嵌套块计数器，从当前块开始

        for (i in (startPosition + 1) until steps.size) {
            val currentModule = ModuleRegistry.getModule(steps[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == pairingId) { // 只关心同一配对ID的模块
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_START -> openBlocks++ // 遇到嵌套的开始块，增加计数
                    BlockType.BLOCK_END -> {
                        openBlocks-- // 遇到结束块，减少计数
                        // 如果计数归零且是目标ID之一，则找到
                        if (openBlocks == 0 && targetIds.contains(currentModule.id)) return i
                    }
                    BlockType.BLOCK_MIDDLE -> {
                        // 如果是中间块（如Else），且当前嵌套层级为1（即当前块的直接子块），且是目标ID之一，则找到
                        if (openBlocks == 1 && targetIds.contains(currentModule.id)) return i
                    }
                    else -> {} // 其他类型（NONE）不影响计数
                }
            }
        }
        return -1 // 未找到目标
    }

    /**
     * 从一个结束块开始，向后查找配对的起始块。
     * @param steps 步骤列表。
     * @param endPosition 结束块的索引。
     * @param targetId 目标起始块的模块ID。
     * @return 找到的步骤索引，未找到则返回 -1。
     */
    fun findBlockStartPosition(steps: List<ActionStep>, endPosition: Int, targetId: String): Int {
        val endModule = ModuleRegistry.getModule(steps.getOrNull(endPosition)?.moduleId ?: return -1)
        val pairingId = endModule?.blockBehavior?.pairingId ?: return -1
        var openBlocks = 1 // 从结束块开始，计数器为1

        for (i in (endPosition - 1) downTo 0) {
            val currentModule = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            if (currentModule.blockBehavior.pairingId == pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_END -> openBlocks++
                    BlockType.BLOCK_START -> {
                        openBlocks--
                        if (openBlocks == 0 && currentModule.id == targetId) return i
                    }
                    else -> {}
                }
            }
        }
        return -1
    }

    /**
     * 从一个起始块开始，向前查找配对的结束块。
     * @param steps 步骤列表。
     * @param startPosition 起始块的索引。
     * @param pairingId 配对ID。
     * @return 找到的结束块索引，未找到则返回 -1。
     */
    fun findEndBlockPosition(steps: List<ActionStep>, startPosition: Int, pairingId: String?): Int {
        if (pairingId == null) return -1
        var openBlocks = 1
        for (i in (startPosition + 1) until steps.size) {
            val module = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            val behavior = module.blockBehavior
            if (behavior.pairingId == pairingId) {
                when (behavior.type) {
                    BlockType.BLOCK_START -> openBlocks++
                    BlockType.BLOCK_END -> {
                        openBlocks--
                        if (openBlocks == 0) return i
                    }
                    else -> {}
                }
            }
        }
        return -1
    }

    /**
     * 查找当前位置所在的循环块的起始位置。
     * @param steps 步骤列表。
     * @param position 当前步骤的索引。
     * @return 循环起始块的索引，未找到则返回 -1。
     */
    fun findCurrentLoopStartPosition(steps: List<ActionStep>, position: Int): Int {
        var openCount = 0
        for (i in position downTo 0) {
            val module = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            val behavior = module.blockBehavior
            val isLoopBlock = behavior.pairingId == LOOP_PAIRING_ID || behavior.pairingId == WHILE_PAIRING_ID || behavior.pairingId == FOREACH_PAIRING_ID

            if (isLoopBlock) {
                if (behavior.type == BlockType.BLOCK_END) {
                    openCount++
                } else if (behavior.type == BlockType.BLOCK_START) {
                    if (openCount == 0) {
                        return i
                    }
                    openCount--
                }
            }
        }
        return -1
    }

    /**
     * 查找当前位置所在的循环块的配对ID。
     * @param steps 步骤列表。
     * @param position 当前步骤的索引。
     * @return 配对ID字符串，如果当前不在循环内则返回 null。
     */
    fun findCurrentLoopPairingId(steps: List<ActionStep>, position: Int): String? {
        var openCount = 0
        for (i in position downTo 0) {
            val module = ModuleRegistry.getModule(steps[i].moduleId) ?: continue
            val behavior = module.blockBehavior
            val isLoopBlock = behavior.pairingId == LOOP_PAIRING_ID || behavior.pairingId == WHILE_PAIRING_ID || behavior.pairingId == FOREACH_PAIRING_ID

            if (isLoopBlock) {
                if (behavior.type == BlockType.BLOCK_END) {
                    openCount++
                } else if (behavior.type == BlockType.BLOCK_START) {
                    if (openCount == 0) {
                        return behavior.pairingId
                    }
                    openCount--
                }
            }
        }
        return null
    }
}