// 文件: main/java/com/chaomixian/vflow/core/types/properties/PropertyDefinition.kt
package com.chaomixian.vflow.core.types.properties

/**
 * 属性定义：包含名称、别名、访问器和描述
 *
 * @param primaryName 主名称（推荐使用的名称）
 * @param aliases 别名集合（包括中文名、缩写等）
 * @param accessor 属性访问器，负责计算属性值
 * @param description 属性描述（用于文档和 IDE 支持）
 */
data class PropertyDefinition(
    val primaryName: String,
    val aliases: Set<String> = emptySet(),
    val accessor: PropertyAccessor,
    val description: String = ""
) {
    /**
     * 检查给定的名称是否匹配此属性
     * 匹配规则：
     * 1. 大小写敏感
     * 2. 匹配主名称或任意别名
     *
     * @param name 要检查的属性名称
     * @return 是否匹配
     */
    fun matches(name: String): Boolean {
        // 大小写敏感匹配
        return name == primaryName || aliases.contains(name)
    }

    /**
     * 获取所有匹配此属性的可能名称
     * 包括主名称和所有别名
     */
    fun allNames(): Set<String> = setOf(primaryName) + aliases
}
