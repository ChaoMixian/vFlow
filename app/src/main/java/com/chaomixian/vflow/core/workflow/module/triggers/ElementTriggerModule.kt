package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.graphics.Rect
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.ElementTriggerData

/**
 * 元素触发器模块
 * 当页面上出现匹配 selector 语句的控件时触发工作流
 */
class ElementTriggerModule : BaseModule() {

    companion object {
        private const val TAG = "ElementTriggerModule"
    }

    override val id = "vflow.trigger.element"

    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_element_name,
        descriptionStringRes = R.string.module_vflow_trigger_element_desc,
        name = "元素触发",  // Fallback
        description = "当页面上出现匹配选择器的控件时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_ads_click_24,
        category = "触发器"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "selector",
            "选择器表达式",
            ParameterType.STRING,
            "",
            acceptsMagicVariable = true,
            supportsRichText = false,
            hint = "如: @Button[text='确定']"
        ),
        InputDefinition(
            "actionCd",
            "冷却时间(ms)",
            ParameterType.NUMBER,
            1000,
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            "actionDelay",
            "触发延迟(ms)",
            ParameterType.NUMBER,
            0,
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            "matchTime",
            "匹配窗口(ms)",
            ParameterType.NUMBER,
            0,
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            "matchDelay",
            "匹配延迟(ms)",
            ParameterType.NUMBER,
            0,
            acceptsMagicVariable = true,
            isFolded = true
        ),
        InputDefinition(
            "actionMaximum",
            "最大触发次数",
            ParameterType.NUMBER,
            null,
            acceptsMagicVariable = true,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("element", "匹配的控件", VTypeRegistry.SCREEN_ELEMENT.id),
        OutputDefinition("all_elements", "所有匹配", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.SCREEN_ELEMENT.id)
    )

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("元素触发器已触发"))

        // 从 triggerData 中提取 VScreenElement
        val triggerData = context.triggerData as? ElementTriggerData
        if (triggerData == null) {
            DebugLogger.w(TAG, "triggerData 为空或类型错误")
            return ExecutionResult.Success(mapOf(
                "element" to VScreenElement(
                    bounds = Rect(0, 0, 0, 0),
                    text = null,
                    contentDescription = null,
                    viewId = null,
                    className = null,
                    isClickable = false,
                    isEnabled = false,
                    isCheckable = false,
                    isChecked = false,
                    isFocusable = false,
                    isFocused = false,
                    isScrollable = false,
                    isLongClickable = false,
                    isSelected = false,
                    isEditable = false,
                    depth = 0,
                    childCount = 0,
                    accessibilityId = null
                ),
                "all_elements" to VList(emptyList<VScreenElement>())
            ))
        }

        val element = triggerData.element
        DebugLogger.d(TAG, "元素触发器输出: ${element.asString()}")

        return ExecutionResult.Success(mapOf(
            "element" to element,
            "all_elements" to VList(listOf(element))
        ))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val selector = step.parameters["selector"]?.toString() ?: ""

        return if (selector.isNotEmpty()) {
            val displaySelector = if (selector.length > 30) {
                selector.take(30) + "..."
            } else {
                selector
            }

            val selectorPill = PillUtil.createPillFromParam(
                step.parameters["selector"],
                getInputs().find { it.id == "selector" }
            )

            val prefix = context.getString(R.string.summary_vflow_trigger_element_prefix)
            val suffix = context.getString(R.string.summary_vflow_trigger_element_suffix)

            PillUtil.buildSpannable(context, prefix, selectorPill, " ", suffix)
        } else {
            context.getString(R.string.module_vflow_trigger_element_name)
        }
    }
}
