// 文件: main/java/com/chaomixian/vflow/core/types/parser/TemplateSegment.kt
package com.chaomixian.vflow.core.types.parser

/**
 * 模板片段：构成富文本字符串的原子单位。
 * 解析器会将字符串 "图片宽度: {{img.width}}" 拆分为:
 * [Text("图片宽度: "), Variable(["img", "width"], "{{img.width}}")]
 */
sealed class TemplateSegment {
    // 静态文本片段
    data class Text(val content: String) : TemplateSegment()

    // 变量引用片段
    data class Variable(
        val path: List<String>,      // 路径链: ["step1", "output", "width"]
        val rawExpression: String,   // 原始表达式: "{{step1.output.width}}" (用于调试或回退)
        val isNamedVariable: Boolean = false // 标记是否为 [[name]] 格式
    ) : TemplateSegment()
}