package com.chaomixian.vflow.modules.logic

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.device.ScreenElement
import com.chaomixian.vflow.modules.variable.*

const val IF_PAIRING_ID = "if"
const val IF_START_ID = "vflow.logic.if.start"
const val ELSE_ID = "vflow.logic.if.middle"
const val IF_END_ID = "vflow.logic.if.end"

class IfModule : ActionModule {
    override val id = IF_START_ID
    override val metadata = ActionMetadata("如果", "根据条件执行不同的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_START, IF_PAIRING_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "condition",
            name = "条件",
            staticType = ParameterType.BOOLEAN,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(
                BooleanVariable::class.java,
                NumberVariable::class.java,
                TextVariable::class.java,
                DictionaryVariable::class.java,
                ListVariable::class.java,
                ScreenElement::class.java
            )
        )
    )
    override fun getOutputs(): List<OutputDefinition> = listOf(
        OutputDefinition("result", "条件结果", BooleanVariable::class.java)
    )

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

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        val condition = context.magicVariables["condition"]
            ?: context.variables["condition"]

        val result = when (condition) {
            is Boolean -> condition
            is BooleanVariable -> condition.value
            is Number -> condition.toDouble() != 0.0
            is NumberVariable -> condition.value != 0.0
            is String -> condition.isNotEmpty()
            is TextVariable -> condition.value.isNotEmpty()
            is Collection<*> -> condition.isNotEmpty()
            is ListVariable -> condition.value.isNotEmpty()
            is Map<*,*> -> condition.isNotEmpty()
            is DictionaryVariable -> condition.value.isNotEmpty()
            is ScreenElement -> true
            else -> condition != null
        }
        onProgress(ProgressUpdate("条件判断结果: $result"))
        return ActionResult(true, mapOf("result" to BooleanVariable(result)))
    }
}

// ... (ElseModule and EndIfModule remain the same, just add the new execute signature)

class ElseModule : ActionModule {
    override val id = ELSE_ID
    override val metadata = ActionMetadata("否则", "如果条件不满足，则执行这里的操作", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_MIDDLE, IF_PAIRING_ID, isIndividuallyDeletable = true)
    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean {
        if (position > 0 && position < steps.size) {
            steps.removeAt(position)
            return true
        }
        return false
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ) = ActionResult(success = true)
}

class EndIfModule : ActionModule {
    override val id = IF_END_ID
    override val metadata = ActionMetadata("结束如果", "", R.drawable.ic_control_flow, "逻辑控制")
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, IF_PAIRING_ID)
    override fun getInputs(): List<InputDefinition> = emptyList()
    override fun getOutputs(): List<OutputDefinition> = emptyList()
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ) = ActionResult(success = true)
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