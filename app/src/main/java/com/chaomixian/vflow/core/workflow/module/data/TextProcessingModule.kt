// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextProcessingModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern

class TextProcessingModule : BaseModule() {
    companion object {
        const val OP_JOIN = "join"
        const val OP_SPLIT = "split"
        const val OP_REPLACE = "replace"
        const val OP_REGEX = "regex_extract"
    }

    override val id = "vflow.data.text_processing"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_text_processing_name,
        descriptionStringRes = R.string.module_vflow_data_text_processing_desc,
        name = "文本处理",  // Fallback
        description = "执行文本的拼接、分割、替换、正则匹配等操作",  // Fallback
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据",
        categoryId = "data"
    )

    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Process text by joining a list, splitting text, replacing content, or extracting regex matches.",
        inputHints = mapOf(
            "operation" to "Use one of join, split, replace, or regex_extract.",
            "join_list" to "For join, pass a list variable to combine into text.",
            "source_text" to "For split, replace, or regex operations, provide the source text.",
            "regex_pattern" to "For regex_extract, provide a valid regex pattern. Use regex_group=0 for the full match, or 1 and above for capturing groups."
        ),
        requiredInputIds = setOf("operation")
    )

    override val uiProvider: ModuleUIProvider? = null

    // 定义所有支持的操作
    private val operationOptions = listOf(OP_JOIN, OP_SPLIT, OP_REPLACE, OP_REGEX)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("operation", "操作", ParameterType.ENUM, OP_JOIN, options = operationOptions, optionsStringRes = listOf(R.string.option_vflow_data_text_processing_operation_join, R.string.option_vflow_data_text_processing_operation_split, R.string.option_vflow_data_text_processing_operation_replace, R.string.option_vflow_data_text_processing_operation_regex), legacyValueMap = mapOf("拼接" to OP_JOIN, "Join" to OP_JOIN, "分割" to OP_SPLIT, "Split" to OP_SPLIT, "替换" to OP_REPLACE, "Replace" to OP_REPLACE, "正则提取" to OP_REGEX, "Regex Extract" to OP_REGEX), acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_data_text_processing_operation_name),
        // --- 拼接 ---
        InputDefinition("join_prefix", "前缀", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_JOIN), nameStringRes = R.string.param_vflow_data_text_processing_join_prefix_name),
        InputDefinition("join_list", "列表", ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.LIST.id), visibility = InputVisibility.whenEquals("operation", OP_JOIN), nameStringRes = R.string.param_vflow_data_text_processing_join_list_name),
        InputDefinition("join_delimiter", "分隔符", ParameterType.STRING, ",", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_JOIN), nameStringRes = R.string.param_vflow_data_text_processing_join_delimiter_name),
        InputDefinition("join_suffix", "后缀", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_JOIN), nameStringRes = R.string.param_vflow_data_text_processing_join_suffix_name),
        // --- 通用文本输入 ---
        InputDefinition("source_text", "源文本", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenIn("operation", listOf(OP_SPLIT, OP_REPLACE, OP_REGEX)), nameStringRes = R.string.param_vflow_data_text_processing_source_text_name),
        // --- 分割 ---
        InputDefinition("split_delimiter", "分隔符", ParameterType.STRING, ",", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_SPLIT), nameStringRes = R.string.param_vflow_data_text_processing_split_delimiter_name),
        // --- 替换 ---
        InputDefinition("replace_from", "查找", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_REPLACE), nameStringRes = R.string.param_vflow_data_text_processing_replace_from_name),
        InputDefinition("replace_to", "替换为", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_REPLACE), nameStringRes = R.string.param_vflow_data_text_processing_replace_to_name),
        // --- 正则 ---
        InputDefinition("regex_pattern", "正则表达式", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, visibility = InputVisibility.whenEquals("operation", OP_REGEX), nameStringRes = R.string.param_vflow_data_text_processing_regex_pattern_name),
        InputDefinition("regex_group", "提取组号", ParameterType.NUMBER, 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), visibility = InputVisibility.whenEquals("operation", OP_REGEX), nameStringRes = R.string.param_vflow_data_text_processing_regex_group_name)
    )

    /**
     * 根据选择的操作动态调整输出参数。
     */
    override fun getDynamicOutputs(
        step: ActionStep?,
        allSteps: List<ActionStep>?
    ): List<OutputDefinition> {
        val operationInput = getInputs().first { it.id == "operation" }
        val rawOperation = step?.parameters?.get("operation") as? String ?: OP_JOIN
        val operation = operationInput.normalizeEnumValue(rawOperation) ?: rawOperation
        return when (operation) {
            OP_JOIN, OP_REPLACE -> listOf(OutputDefinition("result_text", "结果文本", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_text_processing_result_text_name))
            OP_SPLIT, OP_REGEX -> listOf(OutputDefinition("result_list", "结果列表", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_data_text_processing_result_list_name))
            else -> emptyList()
        }
    }

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val rawOperation = step.parameters["operation"]?.toString() ?: OP_JOIN
        val operation = inputs.first { it.id == "operation" }.normalizeEnumValue(rawOperation) ?: rawOperation

        return when (operation) {
            OP_JOIN -> {
                val listPill = PillUtil.createPillFromParam(step.parameters["join_list"], inputs.find { it.id == "join_list" })
                val delimiterPill = PillUtil.createPillFromParam(step.parameters["join_delimiter"], inputs.find { it.id == "join_delimiter" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_join_prefix)
                val middle = context.getString(R.string.summary_vflow_data_text_processing_join_middle)
                val suffix = context.getString(R.string.summary_vflow_data_text_processing_join_suffix)
                PillUtil.buildSpannable(context, prefix, listPill, middle, delimiterPill, suffix)
            }
            OP_SPLIT -> {
                val sourcePill = PillUtil.createPillFromParam(step.parameters["source_text"], inputs.find { it.id == "source_text" })
                val delimiterPill = PillUtil.createPillFromParam(step.parameters["split_delimiter"], inputs.find { it.id == "split_delimiter" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_split_prefix)
                val middle = context.getString(R.string.summary_vflow_data_text_processing_split_middle)
                val suffix = context.getString(R.string.summary_vflow_data_text_processing_split_suffix)
                PillUtil.buildSpannable(context, prefix, sourcePill, middle, delimiterPill, suffix)
            }
            OP_REPLACE -> {
                val sourcePill = PillUtil.createPillFromParam(step.parameters["source_text"], inputs.find { it.id == "source_text" })
                val fromPill = PillUtil.createPillFromParam(step.parameters["replace_from"], inputs.find { it.id == "replace_from" })
                val toPill = PillUtil.createPillFromParam(step.parameters["replace_to"], inputs.find { it.id == "replace_to" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_replace_prefix)
                val middle1 = context.getString(R.string.summary_vflow_data_text_processing_replace_middle1)
                val middle2 = context.getString(R.string.summary_vflow_data_text_processing_replace_middle2)
                PillUtil.buildSpannable(context, prefix, sourcePill, middle1, fromPill, middle2, toPill)
            }
            OP_REGEX -> {
                val sourcePill = PillUtil.createPillFromParam(step.parameters["source_text"], inputs.find { it.id == "source_text" })
                val patternPill = PillUtil.createPillFromParam(step.parameters["regex_pattern"], inputs.find { it.id == "regex_pattern" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_regex_prefix)
                val suffix = context.getString(R.string.summary_vflow_data_text_processing_regex_suffix)
                PillUtil.buildSpannable(context, prefix, sourcePill, suffix, patternPill)
            }
            else -> operation
        }
    }

    /**
     * 执行文本处理。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val operationInput = getInputs().first { it.id == "operation" }
        val rawOperation = context.getVariableAsString("operation", "")
        val operation = operationInput.normalizeEnumValue(rawOperation) ?: rawOperation
        if (operation.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_param_error), appContext.getString(R.string.error_vflow_data_text_processing_no_operation))
        }

        return when (operation) {
            OP_JOIN -> executeJoin(context)
            OP_SPLIT -> executeSplit(context)
            OP_REPLACE -> executeReplace(context)
            OP_REGEX -> executeRegex(context)
            else -> ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_param_error), appContext.getString(R.string.error_vflow_data_text_processing_invalid_operation, operation))
        }
    }

    private fun executeJoin(context: ExecutionContext): ExecutionResult {
        // 使用 VariableResolver 解析所有文本参数
        val prefix = VariableResolver.resolve(context.getVariableAsString("join_prefix", ""), context)
        val suffix = VariableResolver.resolve(context.getVariableAsString("join_suffix", ""), context)
        val delimiter = VariableResolver.resolve(context.getVariableAsString("join_delimiter", ","), context)

        // 列表变量通常直接引用，使用 resolveValue
        val rawList = context.getVariable("join_list")
        val listToJoin = if (rawList is String) {
            (VariableResolver.resolveValue(rawList, context) as? VList)?.raw ?: (VariableResolver.resolveValue(rawList, context) as? List<*>)
        } else {
            (rawList as? List<*>)
        }

        if (listToJoin == null) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_input_error), appContext.getString(R.string.error_vflow_data_text_processing_need_list))
        }

        val joinedString = listToJoin.joinToString(separator = delimiter, prefix = prefix, postfix = suffix)
        return ExecutionResult.Success(mapOf("result_text" to VString(joinedString)))
    }

    private fun executeSplit(context: ExecutionContext): ExecutionResult {
        // 获取原始变量值
        val sourceVar = context.getVariable("source_text")
        val delimiterVar = context.getVariable("split_delimiter")

        // 解析为字符串
        val source = if (sourceVar is com.chaomixian.vflow.core.types.basic.VString) sourceVar.raw
                     else VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val delimiter = if (delimiterVar is com.chaomixian.vflow.core.types.basic.VString) delimiterVar.raw
                        else VariableResolver.resolve(context.getVariableAsString("split_delimiter", ","), context)

        if (source.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_input_error), appContext.getString(R.string.error_vflow_data_text_processing_source_empty))
        }

        val resultList = source.split(delimiter).map { VString(it) }
        return ExecutionResult.Success(mapOf("result_list" to VList(resultList)))
    }

    private fun executeReplace(context: ExecutionContext): ExecutionResult {
        // 获取原始变量值
        val sourceVar = context.getVariable("source_text")
        val fromVar = context.getVariable("replace_from")
        val toVar = context.getVariable("replace_to")

        // 解析为字符串
        val source = if (sourceVar is VString) sourceVar.raw
                     else VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val from = if (fromVar is VString) fromVar.raw
                   else VariableResolver.resolve(context.getVariableAsString("replace_from", ""), context)
        val to = if (toVar is VString) toVar.raw
                 else VariableResolver.resolve(context.getVariableAsString("replace_to", ""), context)

        if (source.isEmpty() || from.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_input_error), appContext.getString(R.string.error_vflow_data_text_processing_source_and_from_empty))
        }

        val resultText = source.replace(from, to)
        return ExecutionResult.Success(mapOf("result_text" to VString(resultText)))
    }

    private fun executeRegex(context: ExecutionContext): ExecutionResult {
        // 获取原始变量值
        val sourceVar = context.getVariable("source_text")
        val patternVar = context.getVariable("regex_pattern")
        // 解析为字符串
        val source = if (sourceVar is com.chaomixian.vflow.core.types.basic.VString) sourceVar.raw
                     else VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val patternStr = if (patternVar is com.chaomixian.vflow.core.types.basic.VString) patternVar.raw
                         else VariableResolver.resolve(context.getVariableAsString("regex_pattern", ""), context)

        // 0 表示完整匹配，1 及以上表示括号捕获组
        val group = context.getVariableAsInt("regex_group") ?: 0

        if (source.isEmpty() || patternStr.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_input_error), appContext.getString(R.string.error_vflow_data_text_processing_pattern_empty))
        }

        try {
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(source)
            val results = mutableListOf<String>()
            while (matcher.find()) {
                if (group <= matcher.groupCount()) {
                    matcher.group(group)?.let { results.add(it) }
                }
            }
            return ExecutionResult.Success(mapOf("result_list" to VList(results.map { VString(it) })))
        } catch (e: Exception) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_data_text_processing_regex_error), e.localizedMessage ?: appContext.getString(R.string.error_vflow_data_text_processing_regex_error))
        }
    }
}
