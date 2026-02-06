// 文件: main/java/com/chaomixian/vflow/core/types/parser/TemplateParser.kt
package com.chaomixian.vflow.core.types.parser

/**
 * 状态机解析器。
 * 将包含变量占位符的字符串解析为 Segment 列表。
 */
class TemplateParser(private val input: String) {

    fun parse(): List<TemplateSegment> {
        val segments = mutableListOf<TemplateSegment>()
        val buffer = StringBuilder()

        var i = 0
        while (i < input.length) {
            val c = input[i]

            // --- 1. 处理转义字符 ---
            // 如果遇到反斜杠 \，且后面还有字符
            if (c == '\\' && i + 1 < input.length) {
                val nextChar = input[i + 1]
                // 仅转义关键符号：{, }, [, ], \
                if (isSpecialChar(nextChar)) {
                    buffer.append(nextChar)
                    i += 2
                    continue
                }
                // 其他情况，保留反斜杠（例如 \n）
            }

            // --- 2. 检测 {{ 变量 ---
            if (c == '{' && i + 1 < input.length && input[i + 1] == '{') {
                flushBuffer(segments, buffer)
                i = parseExpression(i, "{", "}", false, segments)
                continue
            }

            // --- 3. 检测 [[ 变量 (命名变量) ---
            if (c == '[' && i + 1 < input.length && input[i + 1] == '[') {
                flushBuffer(segments, buffer)
                i = parseExpression(i, "[", "]", true, segments)
                continue
            }

            // --- 4. 普通字符 ---
            buffer.append(c)
            i++
        }

        flushBuffer(segments, buffer)
        return segments
    }

    private fun isSpecialChar(c: Char): Boolean {
        return c == '{' || c == '}' || c == '[' || c == ']' || c == '\\'
    }

    private fun flushBuffer(segments: MutableList<TemplateSegment>, buffer: StringBuilder) {
        if (buffer.isNotEmpty()) {
            segments.add(TemplateSegment.Text(buffer.toString()))
            buffer.clear()
        }
    }

    /**
     * 解析表达式块
     * @param startIndex 开始索引 (例如 '{' 的位置)
     * @param openToken 开始标记 (例如 "{")
     * @param closeToken 结束标记 (例如 "}")
     * @return 闭合标签之后的索引
     */
    private fun parseExpression(
        startIndex: Int,
        openToken: String,
        closeToken: String,
        isNamed: Boolean,
        segments: MutableList<TemplateSegment>
    ): Int {
        // 寻找闭合标记 (例如 "}}")
        // 从 startIndex + 2 开始找
        val openFull = "$openToken$openToken"
        val closeFull = "$closeToken$closeToken"

        val closeIndex = findClosingTag(input, startIndex + 2, closeToken[0])

        if (closeIndex != -1) {
            // 提取内容 (去除首尾的 {{ }})
            val content = input.substring(startIndex + 2, closeIndex).trim()
            val raw = input.substring(startIndex, closeIndex + 2)

            if (content.isNotEmpty()) {
                val path = parsePath(content)
                segments.add(TemplateSegment.Variable(path, raw, isNamed))
            } else {
                // 空的 {{}} 视为文本
                segments.add(TemplateSegment.Text(raw))
            }
            return closeIndex + 2
        } else {
            // 未闭合，视为普通文本
            segments.add(TemplateSegment.Text(openFull))
            return startIndex + 2
        }
    }

    private fun findClosingTag(text: String, start: Int, closeChar: Char): Int {
        // 使用嵌套深度计数，正确处理 {{...{{...}}...}} 或 {{...}}...{{...}} 的情况
        // 以及 [[...[[...]]...]] 或 [[...]]...[[...]] 的情况
        var depth = 0
        var j = start
        while (j < text.length - 1) {
            // 检查是否是打开标记 {{ 或 [[
            if ((text[j] == '{' || text[j] == '[') && text[j + 1] == text[j]) {
                depth++
                j += 2
                continue
            }
            // 检查是否是关闭标记 }} 或 ]]
            if (text[j] == closeChar && text[j + 1] == closeChar) {
                if (depth > 0) {
                    depth--
                    j += 2
                    continue
                }
                // depth == 0，找到真正的闭合标记
                return j
            }
            j++
        }
        return -1
    }

    /**
     * 路径解析逻辑。
     * 将 "step1.result.width" 解析为 ["step1", "result", "width"]
     */
    private fun parsePath(expression: String): List<String> {
        // 简单按点分割
        return expression.split('.').map { it.trim() }
    }
}
