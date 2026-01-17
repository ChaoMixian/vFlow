// 文件: main/java/com/chaomixian/vflow/core/pill/PillBuilder.kt
// 描述: Pill构建器接口，平台无关的抽象
package com.chaomixian.vflow.core.pill

/**
 * Pill构建器接口（平台无关）
 *
 * 这个接口定义了构建包含Pill的文本结构的方法。
 * 不同的平台可以实现这个接口来提供平台特定的文本对象。
 *
 * 例如：
 * - Android平台可以实现返回 android.text.Spannable
 * - iOS平台可以实现返回 NSAttributedString
 * - Desktop平台可以实现返回富文本对象
 *
 * 核心模块只依赖这个接口，不依赖任何平台特定的实现。
 */
interface PillBuilder {
    /**
     * 构建包含Pill的文本结构
     *
     * 此方法接受混合的文本片段和Pill对象，返回平台特定的文本对象。
     *
     * @param parts 文本片段和Pill的混合序列
     *               - String: 普通文本
     *               - Pill: Pill对象（包含text, parameterId等）
     * @return 平台特定的文本对象（如Android的Spannable）
     *
     * 示例：
     * ```
     * val pill = Pill("{{step1.text}}", "input1")
     * val result = builder.build("如果 ", pill, " 等于 ", "test")
     * // Android平台返回 Spannable
     * // iOS平台返回 NSAttributedString
     * ```
     */
    fun build(vararg parts: Any): Any
}
