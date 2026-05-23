// 文件: main/java/com/chaomixian/vflow/core/types/parser/VariablePathParser.kt
package com.chaomixian.vflow.core.types.parser

import com.chaomixian.vflow.core.execution.VariableInfo
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 变量路径解析器
 *
 * 统一处理变量引用的路径解析，支持：
 * - 点号分隔：step1.result.width
 * - 点号索引：list.0, list.1, list.-1
 * - 混合语法：list.0.name, step1.result.width
 *
 * 注意：不支持方括号语法 list[0]，请使用 list.0 代替
 *
 * 这个工具类被以下模块共享使用：
 * - TemplateParser: 解析模板中的变量引用
 * - VariableInfo: 提取变量元数据
 * - PillRenderer: 渲染变量药丸
 *
 * 确保整个应用中的路径解析行为完全一致。
 */
object VariablePathParser {
    const val NAMED_VARIABLE_NAMESPACE = "vars"
    const val GLOBAL_VARIABLE_NAMESPACE = "global"

    data class ParsedVariableReference(
        val rawReference: String,
        val path: List<String>,
        val isNamedVariable: Boolean
    )

    /**
     * 解析变量路径为路径段列表
     *
     * @param expression 变量表达式（不包含 {{ }} 或 [[ ]]）
     * @return 路径段列表，例如 "list.0.name" → ["list", "0", "name"]
     */
    fun parsePath(expression: String): List<String> {
        // 简单按点分割，不支持方括号
        val path = mutableListOf<String>()
        val buffer = StringBuilder()
        var i = 0

        while (i < expression.length) {
            val c = expression[i]

            when (c) {
                // 遇到点号：将缓冲区内容作为一个路径元素
                '.' -> {
                    if (buffer.isNotEmpty()) {
                        path.add(buffer.toString().trim())
                        buffer.clear()
                    }
                    i++
                }

                // 普通字符
                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }

        // 添加最后一个路径元素
        if (buffer.isNotEmpty()) {
            path.add(buffer.toString().trim())
        }

        return path
    }

    /**
     * 从变量引用字符串中提取路径
     * 自动去除 {{ }} 或 [[ ]] 包装
     *
     * @param variableRef 变量引用，例如 "{{list.0}}" 或 "[\[myList.0]]"
     * @return 路径段列表
     */
    fun parseVariableReference(variableRef: String): List<String> {
        return parseSingleVariableReference(variableRef)?.path ?: parsePath(variableRef.trim())
    }

    fun parseNamedVariablePath(variableRef: String): List<String>? {
        val parsed = parseSingleVariableReference(variableRef) ?: return null
        if (!parsed.isNamedVariable) return null
        return stripNamedVariableNamespace(parsed.path)
    }

    fun canonicalizeNamedVariableReference(variableRef: String): String {
        val parsed = parseSingleVariableReference(variableRef) ?: return variableRef
        if (!parsed.isNamedVariable) return variableRef

        val path = stripNamedVariableNamespace(parsed.path)
        if (path.isEmpty()) return variableRef

        return buildNamedVariableReference(path.first(), path.drop(1))
    }

    fun canonicalizeVariableReference(variableRef: String, allSteps: List<ActionStep>): String {
        val parsed = parseSingleVariableReference(variableRef) ?: return variableRef
        if (parsed.isNamedVariable) {
            return canonicalizeNamedVariableReference(variableRef)
        }

        val path = parsed.path
        if (path.size < 3) return variableRef
        if (path.firstOrNull() == GLOBAL_VARIABLE_NAMESPACE) return variableRef

        val stepId = path[0]
        val outputId = path[1]
        val sourceStep = allSteps.find { it.id == stepId } ?: return variableRef
        val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return variableRef
        val outputDef = sourceModule.getDynamicOutputs(sourceStep, allSteps).find { it.id == outputId } ?: return variableRef

        var currentTypeId = outputDef.listElementType ?: outputDef.typeName
        val canonicalSegments = mutableListOf(stepId, outputId)

        path.drop(2).forEach { segment ->
            val canonical = VTypeRegistry.getType(currentTypeId)
                .properties
                .firstOrNull { it.matches(segment) }
                ?.name
                ?: segment
            canonicalSegments += canonical
            currentTypeId = VTypeRegistry.getPropertyType(currentTypeId, canonical)?.id ?: return@forEach
        }

        return "{{${canonicalSegments.joinToString(".")}}}"
    }

    fun buildNamedVariableReference(name: String, pathSegments: List<String> = emptyList()): String {
        val fullPath = buildList {
            add(name)
            addAll(pathSegments)
        }.joinToString(".")
        return "{{${NAMED_VARIABLE_NAMESPACE}.${fullPath}}}"
    }

    fun parseGlobalVariablePath(variableRef: String): List<String>? {
        val parsed = parseSingleVariableReference(variableRef) ?: return null
        if (parsed.isNamedVariable) return null
        if (parsed.path.size < 2 || parsed.path.firstOrNull() != GLOBAL_VARIABLE_NAMESPACE) return null
        return parsed.path.drop(1)
    }

    fun buildGlobalVariableReference(name: String, pathSegments: List<String> = emptyList()): String {
        val fullPath = buildList {
            add(name)
            addAll(pathSegments)
        }.joinToString(".")
        return "{{${GLOBAL_VARIABLE_NAMESPACE}.${fullPath}}}"
    }

    fun parseSingleVariableReference(variableRef: String): ParsedVariableReference? {
        val segments = TemplateParser(variableRef).parse()
        if (segments.size != 1) return null

        val variable = segments.single() as? TemplateSegment.Variable ?: return null
        if (variable.rawExpression != variableRef) return null

        return ParsedVariableReference(
            rawReference = variable.rawExpression,
            path = variable.path,
            isNamedVariable = variable.isNamedVariable
        )
    }

    fun appendPathSegment(variableRef: String, segment: String): String {
        val parsed = parseSingleVariableReference(variableRef) ?: return variableRef
        return if (parsed.isNamedVariable) {
            val path = stripNamedVariableNamespace(parsed.path)
            if (path.isEmpty()) {
                variableRef
            } else {
                buildNamedVariableReference(path.first(), path.drop(1) + segment)
            }
        } else {
            variableRef.removeSuffix("}}") + ".${segment}}}"
        }
    }

    private fun stripNamedVariableNamespace(path: List<String>): List<String> {
        return if (path.firstOrNull() == NAMED_VARIABLE_NAMESPACE) path.drop(1) else path
    }
}
