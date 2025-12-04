// 文件: main/java/com/chaomixian/vflow/core/execution/VariableResolver.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 变量解析器。
 * 职责：将带有变量占位符的字符串解析为最终值，并提供通用的复杂度判断。
 */
object VariableResolver {

    // 将 private 改为 public，供 UI 组件复用，确保全应用解析规则一致
    val VARIABLE_PATTERN: Pattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")

    /**
     * 判断文本是否为“复杂”内容。
     */
    fun isComplex(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false

        val matcher = VARIABLE_PATTERN.matcher(text)
        var variableCount = 0
        while (matcher.find()) {
            variableCount++
        }

        if (variableCount == 0) return false // 纯文本
        if (variableCount > 1) return true   // 多个变量

        // 只有一个变量，检查是否还有其他文本
        val textWithoutVariable = matcher.replaceAll("").trim()
        return textWithoutVariable.isNotEmpty()
    }

    /**
     * 解析富文本。
     */
    fun resolve(rawText: String, context: ExecutionContext): String {
        if (rawText.isEmpty()) return ""

        val matcher = VARIABLE_PATTERN.matcher(rawText)
        val result = StringBuffer()

        while (matcher.find()) {
            val variableRef = matcher.group(1)
            var replacement = ""

            if (variableRef != null) {
                val valueObj = resolveValue(variableRef, context)
                replacement = valueObj?.let { convertToString(it) } ?: ""
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    fun resolveValue(variableRef: String, context: ExecutionContext): Any? {
        if (variableRef.isMagicVariable()) {
            val parts = variableRef.removeSurrounding("{{", "}}").split('.')
            val sourceStepId = parts.getOrNull(0)
            val sourceOutputId = parts.getOrNull(1)
            if (sourceStepId != null && sourceOutputId != null) {
                return context.stepOutputs[sourceStepId]?.get(sourceOutputId)
            }
        } else if (variableRef.isNamedVariable()) {
            val varName = variableRef.removeSurrounding("[[", "]]")
            return context.namedVariables[varName]
        }
        return null
    }

    private fun convertToString(value: Any): String {
        return when (value) {
            is TextVariable -> value.value
            is NumberVariable -> {
                val d = value.value
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            }
            is BooleanVariable -> value.value.toString()
            is ListVariable -> value.value.joinToString(", ")
            is DictionaryVariable -> value.value.toString()
            is String -> value
            else -> value.toString()
        }
    }
}