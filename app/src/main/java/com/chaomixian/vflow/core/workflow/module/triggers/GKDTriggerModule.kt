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
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.GKDTriggerData
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * GKD订阅触发器模块
 * 支持解析 gkd 格式的订阅规则，当匹配时触发工作流
 */
class GKDTriggerModule : BaseModule() {

    companion object {
        private const val TAG = "GKDTriggerModule"
    }

    override val id = "vflow.trigger.gkd"

    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_gkd_name,
        descriptionStringRes = R.string.module_vflow_trigger_gkd_desc,
        name = "GKD订阅触发",  // Fallback
        description = "解析 gkd 订阅规则并触发工作流",  // Fallback
        iconRes = R.drawable.rounded_ads_click_24,
        category = "触发器"
    )

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider = GKDTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "subscriptionUrl",
            "订阅链接",
            ParameterType.STRING,
            "",
            acceptsMagicVariable = true,
            supportsRichText = false,
            hint = "gkd 订阅规则的 URL"
        ),
        InputDefinition(
            "subscriptionFile",
            "订阅规则文件路径",
            ParameterType.STRING,
            "",
            acceptsMagicVariable = true,
            isFolded = true,
            hint = "本地规则文件路径 (可选)"
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
        OutputDefinition("all_elements", "所有匹配", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.SCREEN_ELEMENT.id),
        OutputDefinition("ruleName", "触发规则名称", VTypeRegistry.STRING.id),
        OutputDefinition("ruleGroup", "触发规则组", VTypeRegistry.STRING.id)
    )

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("GKD订阅触发器已触发"))

        val triggerData = context.triggerData as? GKDTriggerData
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
                "all_elements" to VList(emptyList<VScreenElement>()),
                "ruleName" to "",
                "ruleGroup" to ""
            ))
        }

        val element = triggerData.element
        val allElements = triggerData.allElements
        DebugLogger.d(TAG, "GKD订阅触发器输出: rule=${triggerData.ruleName}, element=${element.asString()}")

        return ExecutionResult.Success(mapOf(
            "element" to element,
            "all_elements" to VList(allElements),
            "ruleName" to triggerData.ruleName,
            "ruleGroup" to triggerData.ruleGroup
        ))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val subscriptionUrl = step.parameters["subscriptionUrl"]?.toString() ?: ""
        val subscriptionFile = step.parameters["subscriptionFile"]?.toString() ?: ""

        // 确定要显示的文本和参数ID
        val (displayText, paramId, paramValue) = when {
            subscriptionUrl.isNotEmpty() -> Triple(
                subscriptionUrl,
                "subscriptionUrl",
                subscriptionUrl
            )
            subscriptionFile.isNotEmpty() -> Triple(
                // 对于文件路径，只显示文件名
                subscriptionFile.substringAfterLast("/"),
                "subscriptionFile",
                subscriptionFile
            )
            else -> Triple("GKD订阅触发", "", null)
        }

        // 创建pill
        val pill = if (paramValue != null) {
            val inputDef = getInputs().find { it.id == paramId }
            PillUtil.createPillFromParam(displayText, inputDef)
        } else null

        val prefix = context.getString(R.string.summary_vflow_trigger_gkd_prefix)
        val suffix = context.getString(R.string.summary_vflow_trigger_gkd_suffix)

        return PillUtil.buildSpannable(context, prefix, pill ?: "", " ", suffix)
    }
}
