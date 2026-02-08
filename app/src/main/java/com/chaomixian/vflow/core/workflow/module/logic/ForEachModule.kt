// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/logic/ForEachModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VString
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
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_foreach_start_name,
        descriptionStringRes = R.string.module_vflow_logic_foreach_start_desc,
        name = "重复每一项",
        description = "遍历列表中的每一个项目，并分别执行操作。",
        iconRes = R.drawable.rounded_repeat_24,
        category = "逻辑控制"
    )
    override val pairingId = FOREACH_PAIRING_ID
    override val stepIdsInBlock = listOf(FOREACH_START_ID, FOREACH_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "input_list",
            nameStringRes = R.string.param_vflow_logic_foreach_start_input_list_name,
            name = "输入列表",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("item", nameStringRes = R.string.output_vflow_logic_foreach_start_item_name, name = "重复项目", typeName = "vflow.type.any"), // 默认类型，实际类型由 getDynamicOutputs 确定
        OutputDefinition("index", nameStringRes = R.string.output_vflow_logic_foreach_start_index_name, name = "重复索引", typeName = VTypeRegistry.NUMBER.id)
    )

    /**
     * 动态获取输出参数，根据输入列表的类型推断"重复项目"的类型
     */
    override fun getDynamicOutputs(step: ActionStep?, allSteps: List<ActionStep>?): List<OutputDefinition> {
        // 尝试从输入参数推断列表元素的类型
        val listElementType = inferListElementType(step, allSteps)

        return listOf(
            OutputDefinition("item", nameStringRes = R.string.output_vflow_logic_foreach_start_item_name, name = "重复项目", typeName = listElementType ?: "vflow.type.any"),
            OutputDefinition("index", nameStringRes = R.string.output_vflow_logic_foreach_start_index_name, name = "重复索引", typeName = VTypeRegistry.NUMBER.id)
        )
    }

    /**
     * 推断列表元素的类型
     * 从输入参数中解析变量引用，找到对应的输出
     * - 如果是 LIST 类型，返回列表元素类型
     * - 其他类型都返回 STRING（逐字符遍历）
     */
    private fun inferListElementType(step: ActionStep?, allSteps: List<ActionStep>?): String? {
        if (step == null || allSteps == null) return null

        val inputListValue = step.parameters["input_list"] as? String ?: return null

        // 检查是否是魔法变量引用 (格式: {{stepId.outputId}})
        if (inputListValue.startsWith("{{") && inputListValue.endsWith("}}")) {
            val content = inputListValue.removeSurrounding("{{", "}}")
            val parts = content.split('.')
            if (parts.size < 2) return null

            val sourceStepId = parts[0]
            val sourceOutputId = parts[1]

            // 查找源步骤
            val sourceStep = allSteps.find { it.id == sourceStepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null

            // 获取源模块的输出定义
            val sourceOutputs = sourceModule.getDynamicOutputs(sourceStep, allSteps)
            val sourceOutput = sourceOutputs.find { it.id == sourceOutputId } ?: return null

            // 如果源输出是 LIST 类型，返回列表元素类型
            if (sourceOutput.typeName == VTypeRegistry.LIST.id) {
                return sourceOutput.listElementType
            }

            // 其他所有类型都返回 STRING（逐字符遍历）
            return VTypeRegistry.STRING.id
        }

        // 非魔法变量引用时，默认为 STRING
        return VTypeRegistry.STRING.id
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val listPill = PillUtil.createPillFromParam(
            step.parameters["input_list"],
            getInputs().find { it.id == "input_list" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_for_each_prefix), listPill, context.getString(R.string.summary_vflow_logic_for_each_suffix))
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 支持列表和字符串遍历
        // VList: 遍历列表元素
        // 其他类型（包括 VString）: 转换为字符串后逐字符遍历
        val inputVar = context.getVariable("input_list")
        val items: List<VObject> = when (inputVar) {
            is VList -> inputVar.raw
            else -> {
                // 其他所有类型都转换为字符串后逐字符遍历
                val strValue = inputVar.asString()
                strValue.map { VString(it.toString()) }
            }
        }

        if (items.isEmpty()) {
            onProgress(ProgressUpdate("输入为空，跳过循环。"))
            // 使用 BlockNavigator
            val endPc = BlockNavigator.findNextBlockPosition(context.allSteps, context.currentStepIndex, setOf(FOREACH_END_ID))
            return if (endPc != -1) {
                ExecutionResult.Signal(ExecutionSignal.Jump(endPc + 1)) // 跳转到结束块之后
            } else {
                ExecutionResult.Failure("执行错误", "找不到配对的结束循环块。")
            }
        }

        // 只有列表类型才保持元素类型，其他都是字符串
        val itemType = if (inputVar is VList) VTypeRegistry.ANY.id else VTypeRegistry.STRING.id
        onProgress(ProgressUpdate("开始遍历，共 ${items.size} 项。"))

        // 存储当前输入类型，用于推断 item 类型
        context.magicVariables["__foreach_input_type"] = VString(itemType)

        context.loopStack.push(LoopState.ForEachLoopState(items))
        return ExecutionResult.Success() // 继续进入循环体
    }
}

/**
 * "结束重复" 模块, ForEach 逻辑块的结束点.
 */
class EndForEachModule : BaseModule() {
    override val id = FOREACH_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_foreach_end_name,
        descriptionStringRes = R.string.module_vflow_logic_foreach_end_desc,
        name = "结束重复",
        description = "",
        iconRes = R.drawable.rounded_repeat_24,
        category = "逻辑控制"
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, FOREACH_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_end_for_each)

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
            context.magicVariables.remove("__foreach_input_type")
            onProgress(ProgressUpdate("遍历完成。"))
            ExecutionResult.Success()
        }
    }
}