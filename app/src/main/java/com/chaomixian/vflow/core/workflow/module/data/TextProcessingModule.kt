// 文件: TextProcessingModule.kt
// 描述: 提供多种文本处理功能，如拼接、分割、替换和正则匹配。

package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern

/**
 * “文本处理”模块。
 * 提供多种文本操作功能，根据用户选择动态调整输入和输出。
 */
class TextProcessingModule : BaseModule() {

    override val id = "vflow.data.text_processing"
    override val metadata = ActionMetadata(
        name = "文本处理",
        description = "执行文本的拼接、分割、替换、正则匹配等操作。",
        iconRes = R.drawable.rounded_convert_to_text_24, // 使用新图标
        category = "数据"
    )

    // 定义所有支持的操作
    private val operationOptions = listOf("拼接", "分割", "替换", "正则提取")

    /**
     * 静态输入定义。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = "operation", name = "操作", staticType = ParameterType.ENUM, defaultValue = "拼接", options = operationOptions, acceptsMagicVariable = false),
        // --- 拼接 ---
        InputDefinition(id = "join_prefix", name = "前缀", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        InputDefinition(id = "join_list", name = "列表", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(ListVariable.TYPE_NAME)),
        InputDefinition(id = "join_delimiter", name = "分隔符", staticType = ParameterType.STRING, defaultValue = ",", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        InputDefinition(id = "join_suffix", name = "后缀", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        // --- 分割/替换/正则 ---
        InputDefinition(id = "source_text", name = "源文本", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        // --- 分割 ---
        InputDefinition(id = "split_delimiter", name = "分隔符", staticType = ParameterType.STRING, defaultValue = ",", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        // --- 替换 ---
        InputDefinition(id = "replace_from", name = "查找", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        InputDefinition(id = "replace_to", name = "替换为", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        // --- 正则 ---
        InputDefinition(id = "regex_pattern", name = "正则表达式", staticType = ParameterType.STRING, defaultValue = "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)),
        InputDefinition(id = "regex_group", name = "匹配组号", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
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
            "拼接", "替换" -> listOf(OutputDefinition("result_text", "结果文本", TextVariable.TYPE_NAME))
            "分割", "正则提取" -> listOf(OutputDefinition("result_list", "结果列表", ListVariable.TYPE_NAME))
            else -> emptyList()
        }
    }

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val operation = step.parameters["operation"]?.toString() ?: "拼接"

        // 辅助函数，用于获取参数值和是否为变量，并创建Pill对象
        fun createPill(paramId: String, defaultValue: String = "..."): PillUtil.Pill {
            val value = step.parameters[paramId]?.toString() ?: defaultValue
            val isVariable = value.startsWith("{{") && value.endsWith("}}")
            // 对于非变量的空字符串，显示更友好的提示
            val displayText = if (!isVariable && value.isEmpty()) "空" else value
            return PillUtil.Pill(displayText, isVariable, paramId)
        }

        // 创建操作类型本身的药丸
        val operationPill = PillUtil.Pill(operation, false, "operation", isModuleOption = true)

        return when (operation) {
            "拼接" -> {
                val listPill = createPill("join_list", "[列表]")
                val delimiterPill = createPill("join_delimiter", ",")
                // 拼接操作的摘要不显示前后缀，保持简洁，因为它们不常用
                PillUtil.buildSpannable(context,
                    operationPill, ": 将列表 ", listPill,
                    " 用 ", delimiterPill, " 连接"
                )
            }
            "分割" -> {
                val sourcePill = createPill("source_text", "[源文本]")
                val delimiterPill = createPill("split_delimiter", ",")
                PillUtil.buildSpannable(context,
                    operationPill, ": 将 ", sourcePill,
                    " 用 ", delimiterPill, " 分割"
                )
            }
            "替换" -> {
                val sourcePill = createPill("source_text", "[源文本]")
                val fromPill = createPill("replace_from")
                val toPill = createPill("replace_to")
                PillUtil.buildSpannable(context,
                    operationPill, ": 在 ", sourcePill,
                    " 中将 ", fromPill,
                    " 替换为 ", toPill
                )
            }
            "正则提取" -> {
                val sourcePill = createPill("source_text", "[源文本]")
                val patternPill = createPill("regex_pattern")
                PillUtil.buildSpannable(context,
                    operationPill, ": 从 ", sourcePill,
                    " 提取 ", patternPill
                )
            }
            else -> operation // 备用
        }
    }

    /**
     * 执行文本处理。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val operation = context.variables["operation"] as? String ?: return ExecutionResult.Failure("参数错误", "未指定操作类型")

        return when (operation) {
            "拼接" -> executeJoin(context)
            "分割" -> executeSplit(context)
            "替换" -> executeReplace(context)
            "正则提取" -> executeRegex(context)
            else -> ExecutionResult.Failure("操作无效", "不支持的操作: $operation")
        }
    }

    private fun executeJoin(context: ExecutionContext): ExecutionResult {
        val prefix = (context.magicVariables["join_prefix"] as? TextVariable)?.value ?: context.variables["join_prefix"]?.toString() ?: ""
        val suffix = (context.magicVariables["join_suffix"] as? TextVariable)?.value ?: context.variables["join_suffix"]?.toString() ?: ""
        val delimiter = (context.magicVariables["join_delimiter"] as? TextVariable)?.value ?: context.variables["join_delimiter"]?.toString() ?: ""
        val listToJoin = (context.magicVariables["join_list"] as? ListVariable)?.value ?: (context.variables["join_list"] as? List<*>)

        if (listToJoin == null) {
            return ExecutionResult.Failure("输入错误", "需要一个列表变量来进行拼接。")
        }

        val joinedString = listToJoin.joinToString(separator = delimiter, prefix = prefix, postfix = suffix)
        return ExecutionResult.Success(mapOf("result_text" to TextVariable(joinedString)))
    }

    private fun executeSplit(context: ExecutionContext): ExecutionResult {
        val source = (context.magicVariables["source_text"] as? TextVariable)?.value ?: context.variables["source_text"]?.toString()
        val delimiter = (context.magicVariables["split_delimiter"] as? TextVariable)?.value ?: context.variables["split_delimiter"]?.toString()

        if (source == null || delimiter == null) {
            return ExecutionResult.Failure("输入错误", "源文本和分隔符不能为空。")
        }

        val resultList = source.split(delimiter)
        return ExecutionResult.Success(mapOf("result_list" to ListVariable(resultList)))
    }

    private fun executeReplace(context: ExecutionContext): ExecutionResult {
        val source = (context.magicVariables["source_text"] as? TextVariable)?.value ?: context.variables["source_text"]?.toString()
        val from = (context.magicVariables["replace_from"] as? TextVariable)?.value ?: context.variables["replace_from"]?.toString()
        val to = (context.magicVariables["replace_to"] as? TextVariable)?.value ?: context.variables["replace_to"]?.toString()

        if (source == null || from == null || to == null) {
            return ExecutionResult.Failure("输入错误", "源文本、查找内容和替换内容均不能为空。")
        }

        val resultText = source.replace(from, to)
        return ExecutionResult.Success(mapOf("result_text" to TextVariable(resultText)))
    }

    private fun executeRegex(context: ExecutionContext): ExecutionResult {
        val source = (context.magicVariables["source_text"] as? TextVariable)?.value ?: context.variables["source_text"]?.toString()
        val patternStr = (context.magicVariables["regex_pattern"] as? TextVariable)?.value ?: context.variables["regex_pattern"]?.toString()
        val group = (context.magicVariables["regex_group"] as? NumberVariable)?.value?.toInt() ?: (context.variables["regex_group"] as? Number)?.toInt() ?: 0

        if (source == null || patternStr.isNullOrEmpty()) {
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
            return ExecutionResult.Success(mapOf("result_list" to ListVariable(results)))
        } catch (e: Exception) {
            return ExecutionResult.Failure("正则错误", e.localizedMessage ?: "无效的正则表达式")
        }
    }
}