// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/logic/ForEachModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

// --- ForEach模块常量 ---
const val FOREACH_PAIRING_ID = "foreach"
const val FOREACH_START_ID = "vflow.logic.foreach.start"
const val FOREACH_END_ID = "vflow.logic.foreach.end"

/**
 * "重复每一项" (ForEach) 模块.
 * 遍历一个列表，并为列表中的每一项执行循环体.
 */
class ForEachModule : BaseBlockModule() {
    override val id = FOREACH_START_ID
    override val metadata = ActionMetadata("重复每一项", "遍历列表中的每一个项目，并分别执行操作。", R.drawable.rounded_repeat_24, "逻辑控制")
    override val pairingId = FOREACH_PAIRING_ID
    override val stepIdsInBlock = listOf(FOREACH_START_ID, FOREACH_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "input_list",
            name = "输入列表",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.LIST.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("item", "重复项目", "vflow.type.any"), // 项目类型是任意的，因为列表可以包含任何类型
        OutputDefinition("index", "重复索引", VTypeRegistry.NUMBER.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val listPill = PillUtil.createPillFromParam(
            step.parameters["input_list"],
            getInputs().find { it.id == "input_list" }
        )
        return PillUtil.buildSpannable(context, "对于列表 ", listPill, " 中的每一项")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val listVar = context.magicVariables["input_list"] as? VList
        val items = listVar?.raw

        if (items == null) {
            return ExecutionResult.Failure("参数错误", "输入必须是一个列表变量。")
        }

        if (items.isEmpty()) {
            onProgress(ProgressUpdate("列表为空，跳过循环。"))
            // 使用 BlockNavigator
            val endPc = BlockNavigator.findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(FOREACH_END_ID))
            return if (endPc != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(endPc + 1)) // 跳转到结束块之后
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的结束循环块。")
            }
        }

        onProgress(ProgressUpdate("开始遍历列表，共 ${items.size} 项。"))
        context.loopStack.push(LoopState.ForEachLoopState(items))
        return ExecutionResult.Success() // 继续进入循环体
    }
}

/**
 * "结束重复" 模块, ForEach 逻辑块的结束点.
 */
class EndForEachModule : BaseModule() {
    override val id = FOREACH_END_ID
    override val metadata = ActionMetadata("结束重复", "", R.drawable.rounded_repeat_24, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, FOREACH_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "结束重复"

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val loopState = context.loopStack.peek()
        if (loopState !is LoopState.ForEachLoopState) {
            return ExecutionResult.Failure("执行错误", "结束重复模块不在一个'重复每一项'循环内。")
        }

        loopState.currentIndex++ // 移动到下一项

        return if (loopState.currentIndex < loopState.itemList.size) {
            // 列表还有剩余项，跳回到循环体开始处
            // 使用 BlockNavigator
            val forEachStartPos = BlockNavigator.findBlockStartPosition(context.allSteps, context.currentStepIndex, FOREACH_START_ID)
            if (forEachStartPos != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(forEachStartPos + 1))
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的'重复每一项'起始块。")
            }
        } else {
            // 循环结束，弹出状态并继续
            context.loopStack.pop()
            onProgress(ProgressUpdate("列表遍历完成。"))
            ExecutionResult.Success()
        }
    }
}