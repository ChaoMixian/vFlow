package com.chaomixian.vflow.modules.logic

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.BooleanVariable

// --- 模块ID常量，用于逻辑关联 ---
const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

class IfModule : ActionModule {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, IF_PAIRING_ID)

    // --- 新增/修改：实现接口 ---
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "condition",
            name = "条件",
            staticType = ParameterType.BOOLEAN, // 静态输入时是布尔值
            acceptsMagicVariable = true,
            // 明确声明只接受布尔类型的魔法变量
            acceptedMagicVariableTypes = setOf(BooleanVariable::class.java)
        )
    )
    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable::class.java)
    )
    // "如果"模块完全依赖于魔法变量输入，因此没有静态参数
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    // createSteps 和 onStepDeleted 保持不变...
    override fun createSteps(): List<ActionStep> {
        return listOf(
            ActionStep(IF_START_ID, emptyMap()),
            ActionStep(ELSE_ID, emptyMap()),
            ActionStep(IF_END_ID, emptyMap())
        )
    }

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        val endPos = findBlockEndPosition(steps, position, IF_START_ID, IF_END_ID)
        if (endPos != position) {
            for (i in endPos downTo position) {
                steps.removeAt(i)
            }
            return true
        }
        return false
    }

    override suspend fun execute(context: ExecutionContext): ActionResult {
        // “如果”模块的核心职责是评估输入条件，并将布尔结果输出
        val condition = context.magicVariables["condition"]

        // 智能判断各种输入类型的真假值
        val result = when (condition) {
            is Boolean -> condition
            is BooleanVariable -> condition.value
            is Number -> condition.toDouble() != 0.0
            is String -> condition.isNotEmpty()
            is Collection<*> -> condition.isNotEmpty()
            is ScreenElement -> true // 如果上一步找到了元素，视为真
            else -> condition != null // 其他非空对象也视为真
        }

        // 执行器将根据这个输出来决定是否跳过后续步骤
        return ActionResult(true, mapOf("result" to BooleanVariable(result)))
    }
}

class ElseModule : ActionModule {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    // --- 新增：实现接口 ---
    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position > 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

class EndIfModule : ActionModule {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)

    // --- 新增：实现接口 ---
    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()
    override fun getParameters(): List<ParameterDefinition> = emptyList()

    override suspend fun execute(context: ExecutionContext) = ActionResult(success = true)
}

// 辅助函数保持不变...
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