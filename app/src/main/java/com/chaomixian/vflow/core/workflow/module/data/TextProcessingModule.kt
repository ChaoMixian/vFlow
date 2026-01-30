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

    override val id = "vflow.data.text_processing"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_text_processing_name,
        descriptionStringRes = R.string.module_vflow_data_text_processing_desc,
        name = "文本处理",  // Fallback
        description = "执行文本的拼接、分割、替换、正则匹配等操作",  // Fallback
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据"
    )

    override val uiProvider: ModuleUIProvider? = TextProcessingModuleUIProvider()

    // 定义所有支持的操作
    private val operationOptions = listOf("拼接", "分割", "替换", "正则提取")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("operation", "操作", ParameterType.ENUM, "拼接", options = operationOptions, acceptsMagicVariable = false),
        // --- 拼接 ---
        InputDefinition("join_prefix", "前缀", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("join_list", "列表", ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.LIST.id)),
        InputDefinition("join_delimiter", "分隔符", ParameterType.STRING, ",", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("join_suffix", "后缀", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        // --- 通用文本输入 ---
        InputDefinition("source_text", "源文本", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        // --- 分割 ---
        InputDefinition("split_delimiter", "分隔符", ParameterType.STRING, ",", acceptsMagicVariable = true, supportsRichText = true),
        // --- 替换 ---
        InputDefinition("replace_from", "查找", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("replace_to", "替换为", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        // --- 正则 ---
        InputDefinition("regex_pattern", "正则表达式", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("regex_group", "匹配组号", ParameterType.NUMBER, 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id))
    )

    /**
     * 根据选择的操作动态调整输入参数。
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val operation = step?.parameters?.get("operation") as? String ?: "拼接"
        val allInputs = getInputs()
        val dynamicInputs = mutableListOf(allInputs.first { it.id == "operation" })

        when (operation) {
            "拼接" -> dynamicInputs.addAll(allInputs.filter { it.id.startsWith("join_") })
            "分割" -> dynamicInputs.addAll(allInputs.filter { it.id == "source_text" || it.id == "split_delimiter" })
            "替换" -> dynamicInputs.addAll(allInputs.filter { it.id == "source_text" || it.id.startsWith("replace_") })
            "正则提取" -> dynamicInputs.addAll(allInputs.filter { it.id == "source_text" || it.id.startsWith("regex_") })
        }
        return dynamicInputs
    }

    /**
     * 根据选择的操作动态调整输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val operation = step?.parameters?.get("operation") as? String ?: "拼接"
        return when (operation) {
            "拼接", "替换" -> listOf(OutputDefinition("result_text", "结果文本", VTypeRegistry.STRING.id))
            "分割", "正则提取" -> listOf(OutputDefinition("result_list", "结果列表", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.STRING.id))
            else -> emptyList()
        }
    }

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val operation = step.parameters["operation"]?.toString() ?: "拼接"

        return when (operation) {
            "拼接" -> {
                val listPill = PillUtil.createPillFromParam(step.parameters["join_list"], inputs.find { it.id == "join_list" })
                val delimiterPill = PillUtil.createPillFromParam(step.parameters["join_delimiter"], inputs.find { it.id == "join_delimiter" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_join_prefix)
                val middle = context.getString(R.string.summary_vflow_data_text_processing_join_middle)
                val suffix = context.getString(R.string.summary_vflow_data_text_processing_join_suffix)
                PillUtil.buildSpannable(context, prefix, listPill, middle, delimiterPill, suffix)
            }
            "分割" -> {
                val sourcePill = PillUtil.createPillFromParam(step.parameters["source_text"], inputs.find { it.id == "source_text" })
                val delimiterPill = PillUtil.createPillFromParam(step.parameters["split_delimiter"], inputs.find { it.id == "split_delimiter" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_split_prefix)
                val middle = context.getString(R.string.summary_vflow_data_text_processing_split_middle)
                val suffix = context.getString(R.string.summary_vflow_data_text_processing_split_suffix)
                PillUtil.buildSpannable(context, prefix, sourcePill, middle, delimiterPill, suffix)
            }
            "替换" -> {
                val sourcePill = PillUtil.createPillFromParam(step.parameters["source_text"], inputs.find { it.id == "source_text" })
                val fromPill = PillUtil.createPillFromParam(step.parameters["replace_from"], inputs.find { it.id == "replace_from" })
                val toPill = PillUtil.createPillFromParam(step.parameters["replace_to"], inputs.find { it.id == "replace_to" })
                val prefix = context.getString(R.string.summary_vflow_data_text_processing_replace_prefix)
                val middle1 = context.getString(R.string.summary_vflow_data_text_processing_replace_middle1)
                val middle2 = context.getString(R.string.summary_vflow_data_text_processing_replace_middle2)
                PillUtil.buildSpannable(context, prefix, sourcePill, middle1, fromPill, middle2, toPill)
            }
            "正则提取" -> {
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
        val operation = context.getVariableAsString("operation", "")
        if (operation.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "未指定操作类型")
        }

        return when (operation) {
            "拼接" -> executeJoin(context)
            "分割" -> executeSplit(context)
            "替换" -> executeReplace(context)
            "正则提取" -> executeRegex(context)
            else -> ExecutionResult.Failure("操作无效", "不支持的操作: $operation")
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
            return ExecutionResult.Failure("输入错误", "需要一个列表变量来进行拼接。")
        }

        val joinedString = listToJoin.joinToString(separator = delimiter, prefix = prefix, postfix = suffix)
        return ExecutionResult.Success(mapOf("result_text" to VString(joinedString)))
    }

    private fun executeSplit(context: ExecutionContext): ExecutionResult {
        // 统一解析
        val source = VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val delimiter = VariableResolver.resolve(context.getVariableAsString("split_delimiter", ","), context)

        if (source.isEmpty()) {
            return ExecutionResult.Failure("输入错误", "源文本不能为空。")
        }

        val resultList = source.split(delimiter).map { VString(it) }
        return ExecutionResult.Success(mapOf("result_list" to VList(resultList)))
    }

    private fun executeReplace(context: ExecutionContext): ExecutionResult {
        // 统一解析
        val source = VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val from = VariableResolver.resolve(context.getVariableAsString("replace_from", ""), context)
        val to = VariableResolver.resolve(context.getVariableAsString("replace_to", ""), context)

        if (source.isEmpty() || from.isEmpty()) {
            return ExecutionResult.Failure("输入错误", "源文本和查找内容不能为空。")
        }

        val resultText = source.replace(from, to)
        return ExecutionResult.Success(mapOf("result_text" to VString(resultText)))
    }

    private fun executeRegex(context: ExecutionContext): ExecutionResult {
        // 统一解析
        val source = VariableResolver.resolve(context.getVariableAsString("source_text", ""), context)
        val patternStr = VariableResolver.resolve(context.getVariableAsString("regex_pattern", ""), context)

        // 组号通常是数字
        val groupVar = context.getVariable("regex_group")
        val group = when(groupVar) {
            is VNumber -> groupVar.raw.toInt()
            is Number -> groupVar.toInt()
            else -> 0
        }

        if (source.isEmpty() || patternStr.isEmpty()) {
            return ExecutionResult.Failure("输入错误", "源文本和正则表达式不能为空。")
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
            return ExecutionResult.Failure("正则错误", e.localizedMessage ?: "无效的正则表达式")
        }
    }
}