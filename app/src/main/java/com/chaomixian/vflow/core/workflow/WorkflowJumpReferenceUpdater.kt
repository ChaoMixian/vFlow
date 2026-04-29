package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 在工作流步骤重排后，重写“跳转到步骤”模块的静态目标序号，
 * 使其继续指向重排前的同一个目标步骤。
 */
object WorkflowJumpReferenceUpdater {
    private const val JUMP_MODULE_ID = "vflow.logic.jump"
    private const val TARGET_STEP_INDEX_PARAM = "target_step_index"

    fun remapAfterReorder(
        originalSteps: List<ActionStep>,
        reorderedSteps: List<ActionStep>
    ): List<ActionStep> {
        if (originalSteps.size != reorderedSteps.size || originalSteps.isEmpty()) {
            return reorderedSteps
        }

        val newDisplayIndexByStepId = reorderedSteps
            .mapIndexed { index, step -> step.id to (index + 1) }
            .toMap()

        return reorderedSteps.map { step ->
            if (step.moduleId != JUMP_MODULE_ID) {
                return@map step
            }

            val rawTarget = step.parameters[TARGET_STEP_INDEX_PARAM] as? Number ?: return@map step
            val targetDouble = rawTarget.toDouble()
            if (targetDouble.isNaN() || targetDouble % 1.0 != 0.0) {
                return@map step
            }

            val originalDisplayIndex = targetDouble.toInt()
            val targetStepId = originalSteps.getOrNull(originalDisplayIndex - 1)?.id ?: return@map step
            val newDisplayIndex = newDisplayIndexByStepId[targetStepId] ?: return@map step
            if (newDisplayIndex == originalDisplayIndex) {
                return@map step
            }

            step.copy(
                parameters = step.parameters.toMutableMap().apply {
                    put(TARGET_STEP_INDEX_PARAM, rewriteNumber(rawTarget, newDisplayIndex))
                }
            )
        }
    }

    private fun rewriteNumber(source: Number, value: Int): Number {
        return when (source) {
            is Long -> value.toLong()
            is Short -> value.toShort()
            is Byte -> value.toByte()
            is Float -> value.toFloat()
            is Double -> value.toDouble()
            else -> value
        }
    }
}
