package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.model.ActionStep

// 文件：BaseBlockModule.kt
// 描述：为“积木块”类型的模块（如 If, Else, Loop）提供一个抽象基类。
//      它简化了积木块的创建和删除逻辑。

/**
 * “积木块”类型模块的抽象基类。
 * 例如 If 模块、Loop 模块等，它们通常由多个关联的步骤组成一个完整的逻辑块。
 * 此基类封装了创建和删除整个代码块的通用逻辑，
 * 开发者只需定义组成块的各个步骤的模块ID和配对ID即可。
 */
abstract class BaseBlockModule : BaseModule() {

    /**
     * 抽象属性，需要子类实现。
     * 定义了组成一个完整积木块的所有步骤的模块ID列表，按顺序排列。
     * 第一个ID通常是“开始”模块的ID，最后一个ID是“结束”模块的ID。
     * 例如，一个 If-Else-EndIf 结构可能定义为: `listOf("if.start", "if.else", "if.end")`。
     */
    abstract val stepIdsInBlock: List<String>

    /**
     * 抽象属性，需要子类实现。
     * 提供一个用于将积木块中相关步骤（如 Start 和 End）进行逻辑配对的唯一ID。
     * 例如: `"if_block"` 或 `"loop_block"`。
     * 此ID会被用于 BlockBehavior 中，以及在删除时查找配对的块结束位置。
     */
    abstract val pairingId: String

    /**
     * 重写 blockBehavior 属性。
     * 对于积木块的起始模块，其类型为 BLOCK_START，并使用定义的 pairingId。
     */
    override val blockBehavior: BlockBehavior
        get() = BlockBehavior(BlockType.BLOCK_START, pairingId)

    /**
     * 创建组成此积木块的一系列步骤。
     * 此方法被标记为 `final`，因为它提供了基于 `stepIdsInBlock` 的标准实现，
     * 子类不应重写此特定逻辑，而应通过定义 `stepIdsInBlock` 来定制步骤。
     * @return 根据 `stepIdsInBlock` 生成的 ActionStep 列表，每个步骤参数为空。
     */
    final override fun createSteps(): List<ActionStep> {
        return stepIdsInBlock.map { moduleId -> ActionStep(moduleId, emptyMap()) }
    }

    /**
     * 处理删除整个积木块的逻辑。
     * 当积木块的起始步骤被删除时，此方法会尝试找到对应的结束步骤，并删除从起始到结束之间的所有步骤。
     * 此方法被标记为 `final` 以提供统一的积木块删除行为。
     * @param steps 当前工作流中的步骤列表（可修改）。
     * @param position 被删除的积木块起始步骤在此列表中的位置。
     * @return 如果成功删除了积木块（或单个步骤），则返回 true；否则返回 false。
     */
    final override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        // 查找与当前起始块配对的结束块的位置
        val endPos = findBlockEndPosition(steps, position)
        if (endPos != position) { // 如果找到了有效的结束块位置 (不同于起始位置)
            // 从后往前删除，以避免因索引变化导致的错误
            for (i in endPos downTo position) {
                steps.removeAt(i)
            }
            return true
        }
        // 如果没有找到对应的结束块（例如，积木块不完整或查找逻辑失败），
        // 则回退到基类 BaseModule 的删除逻辑，即只删除当前步骤自己。
        return super.onStepDeleted(steps, position)
    }

    /**
     * 辅助函数，用于查找与给定的积木块起始位置相对应的结束块的位置。
     * 它通过计算嵌套的块开始和结束标记来确定正确的配对结束位置。
     * @param steps 工作流中的步骤列表。
     * @param startPosition 积木块起始步骤在列表中的索引。
     * @return 对应的结束块步骤的索引；如果未找到匹配的结束块，则返回原始的 startPosition。
     */
    private fun findBlockEndPosition(steps: List<ActionStep>, startPosition: Int): Int {
        if (stepIdsInBlock.isEmpty()) return startPosition // 如果没有定义块内步骤ID，则无法查找

        val startBlockModuleId = stepIdsInBlock.first() // 积木块的起始模块ID
        val endBlockModuleId = stepIdsInBlock.last()   // 积木块的结束模块ID

        // 如果起始和结束ID相同（例如，一个不成对的简单块，理论上不应在此方法处理），直接返回
        if (startBlockModuleId == endBlockModuleId && stepIdsInBlock.size == 1) return startPosition 

        var openBlocks = 0 // 用于跟踪嵌套块的计数器
        // 确保起始位置的模块确实是我们期望的起始模块，并初始化计数器
        if (steps.getOrNull(startPosition)?.moduleId == startBlockModuleId) {
            openBlocks = 1
        } else {
            // 如果起始位置的模块不是预期的起始模块ID，则无法正确查找，返回原位
            return startPosition
        }

        // 从起始位置的下一个步骤开始遍历，查找配对的结束模块
        for (i in (startPosition + 1) until steps.size) {
            val currentModuleId = steps[i].moduleId
            if (currentModuleId == startBlockModuleId) {
                openBlocks++ //遇到相同的起始模块ID，增加嵌套层级
            } else if (currentModuleId == endBlockModuleId) {
                openBlocks-- // 遇到配对的结束模块ID，减少嵌套层级
                if (openBlocks == 0) return i // 当嵌套层级回到0时，表示找到了最外层对应的结束块
            }
        }
        // 如果遍历完毕仍未找到配对的结束块 (openBlocks != 0)，说明积木块不完整或有误
        return startPosition // 返回原始起始位置，将导致只删除起始步骤本身
    }
}