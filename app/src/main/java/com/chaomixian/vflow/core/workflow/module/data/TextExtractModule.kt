// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextExtractModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 文本提取模块
 * 从文本中提取指定部分
 */
class TextExtractModule : BaseModule() {

    private val modeOptions = listOf("提取中间", "提取前缀", "提取后缀", "提取字符")

    override val id = "vflow.data.text_extract"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_text_extract_name,
        descriptionStringRes = R.string.module_vflow_data_text_extract_desc,
        name = "提取文本",
        description = "从文本中提取指定部分",
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据"
    )

    override val uiProvider: ModuleUIProvider? = null

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            nameStringRes = R.string.param_vflow_data_text_extract_text_name,
            name = "源文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        ),
        InputDefinition(
            id = "mode",
            nameStringRes = R.string.param_vflow_data_text_extract_mode_name,
            name = "提取方式",
            staticType = ParameterType.ENUM,
            defaultValue = "提取中间",
            options = modeOptions,
            acceptsMagicVariable = false
        ),
        // 提取中间 - 当 mode 等于 "提取中间" 时显示
        InputDefinition(
            id = "start",
            nameStringRes = R.string.param_vflow_data_text_extract_start_name,
            name = "起始位置",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            visibility = InputVisibility.whenEquals("mode", "提取中间")
        ),
        InputDefinition(
            id = "end",
            nameStringRes = R.string.param_vflow_data_text_extract_end_name,
            name = "结束位置",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            visibility = InputVisibility.whenEquals("mode", "提取中间")
        ),
        // 提取字符 - 当 mode 等于 "提取字符" 时显示
        InputDefinition(
            id = "index",
            nameStringRes = R.string.param_vflow_data_text_extract_index_name,
            name = "字符位置",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            visibility = InputVisibility.whenEquals("mode", "提取字符")
        ),
        // 提取字符/前缀/后缀 - 当 mode 不等于 "提取中间" 时显示
        InputDefinition(
            id = "count",
            nameStringRes = R.string.param_vflow_data_text_extract_count_name,
            name = "字符数量",
            staticType = ParameterType.NUMBER,
            defaultValue = 1.0,
            acceptsMagicVariable = true,
            visibility = InputVisibility.notEquals("mode", "提取中间")
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result",
            nameStringRes = R.string.output_vflow_data_text_extract_result_name,
            name = "结果文本",
            typeName = VTypeRegistry.STRING.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            inputs.find { it.id == "text" }
        )
        val mode = step.parameters["mode"] as? String ?: "提取中间"
        return PillUtil.buildSpannable(
            context,
            "提取: ",
            textPill,
            " ($mode)"
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val text = context.getVariableAsString("text", "")
        val mode = context.getVariableAsString("mode", "提取中间")

        if (text.isEmpty()) {
            return ExecutionResult.Failure(
                "参数错误",
                "源文本不能为空"
            )
        }

        val result = when (mode) {
            "提取中间" -> {
                val start = context.getVariableAsString("start", "0").toIntOrNull() ?: 0
                val end = context.getVariableAsString("end", "0").toIntOrNull() ?: 0
                extractSubstring(text, start, end)
            }
            "提取前缀" -> {
                val count = context.getVariableAsString("count", "0").toIntOrNull() ?: 0
                text.take(count.coerceAtLeast(0))
            }
            "提取后缀" -> {
                val count = context.getVariableAsString("count", "0").toIntOrNull() ?: 0
                text.takeLast(count.coerceAtLeast(0))
            }
            "提取字符" -> {
                val index = context.getVariableAsString("index", "0").toIntOrNull() ?: 0
                val count = context.getVariableAsString("count", "1").toIntOrNull() ?: 1
                extractCharAt(text, index, count)
            }
            else -> text
        }

        return ExecutionResult.Success(mapOf("result" to VString(result)))
    }

    /**
     * 提取中间字符，处理各种边界情况
     */
    private fun extractSubstring(text: String, start: Int, end: Int): String {
        val length = text.length

        // 处理负数索引
        val actualStart = if (start < 0) maxOf(0, length + start) else minOf(start, length)
        val actualEnd = if (end < 0) maxOf(0, length + end) else minOf(end, length)

        // 确保 start <= end
        if (actualStart >= actualEnd) {
            return ""
        }

        return text.substring(actualStart, minOf(actualEnd, length))
    }

    /**
     * 提取指定位置的字符，可以指定提取数量
     */
    private fun extractCharAt(text: String, index: Int, count: Int): String {
        val length = text.length
        if (count <= 0 || length == 0) return ""

        // 处理负数索引
        val actualIndex = if (index < 0) maxOf(0, length + index) else minOf(index, length)

        val endIndex = minOf(actualIndex + count.coerceAtLeast(0), length)
        if (actualIndex >= endIndex || actualIndex >= length) return ""

        return text.substring(actualIndex, endIndex)
    }
}
