// 文件: main/java/com/chaomixian/vflow/core/types/parser/VariablePathParser.kt
package com.chaomixian.vflow.core.types.parser

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
        val content = when {
            variableRef.startsWith("{{") && variableRef.endsWith("}}") -> {
                variableRef.removeSurrounding("{{", "}}")
            }
            variableRef.startsWith("[[") && variableRef.endsWith("]]") -> {
                variableRef.removeSurrounding("[[", "]]")
            }
            else -> variableRef
        }
        return parsePath(content.trim())
    }

}
