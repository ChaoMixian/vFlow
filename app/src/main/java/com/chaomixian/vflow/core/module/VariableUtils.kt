// 文件：VariableUtils.kt
// 描述：变量相关的工具函数和扩展
package com.chaomixian.vflow.core.module

/**
 * 检查字符串是否为魔法变量引用 (来自步骤输出)。
 * e.g., "{{stepId.outputId}}"
 */
fun String?.isMagicVariable(): Boolean = this?.startsWith("{{") == true && this.endsWith("}}")

/**
 * 检查字符串是否为命名变量引用。
 * e.g., "[[myCounter]]"
 */
fun String?.isNamedVariable(): Boolean = this?.startsWith("[[") == true && this.endsWith("]]")
