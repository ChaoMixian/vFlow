// 文件：VariableUtils.kt
// 描述：变量相关的工具函数和扩展
package com.chaomixian.vflow.core.module

/**
 * 检查字符串是否为魔法变量引用 (来自步骤输出)。
 * e.g., "{{stepId.outputId}}"
 *
 * 注意：多个连续的变量引用 ({{...}}{{...}}) 不视为魔法变量，
 * 它们应该走 VariableResolver 的混合解析逻辑。
 */
fun String?.isMagicVariable(): Boolean {
    if (this == null || !this.startsWith("{{") || !this.endsWith("}}")) return false
    // 排除多个连续的变量引用，它们应该走混合解析逻辑
    if (this.contains("}}{{")) return false
    return true
}

/**
 * 检查字符串是否为命名变量引用。
 * e.g., "[[myCounter]]"
 */
fun String?.isNamedVariable(): Boolean = this?.startsWith("[[") == true && this.endsWith("]]")
