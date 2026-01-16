// 文件: main/java/com/chaomixian/vflow/core/types/properties/PropertyRegistry.kt
package com.chaomixian.vflow.core.types.properties

import com.chaomixian.vflow.core.types.VObject

/**
 * 属性注册表：管理单个 VObject 类型的所有属性
 *
 * 职责：
 * - 注册属性定义
 * - 根据名称查找属性（支持别名和大小写不敏感）
 * - 提供属性列表用于文档和调试
 */
class PropertyRegistry {
    private val properties = mutableListOf<PropertyDefinition>()

    /**
     * 注册一个属性
     *
     * @param name 属性主名称
     * @param aliases 属性别名（可选）
     * @param accessor 属性访问器
     * @param description 属性描述（可选）
     */
    fun register(
        name: String,
        vararg aliases: String,
        accessor: PropertyAccessor,
        description: String = ""
    ) {
        val def = PropertyDefinition(
            primaryName = name,
            aliases = aliases.toSet(),
            accessor = accessor,
            description = description
        )
        properties.add(def)
    }

    /**
     * 便捷方法：注册简单 lambda 属性
     *
     * @param name 属性主名称
     * @param aliases 属性别名（可选）
     * @param description 属性描述（可选）
     * @param getter 属性获取器 lambda
     */
    fun register(
        name: String,
        vararg aliases: String,
        description: String = "",
        getter: (VObject) -> VObject?
    ) {
        register(name, aliases = aliases, accessor = SimplePropertyAccessor(getter), description = description)
    }

    /**
     * 查找属性定义
     *
     * @param propertyName 属性名称（大小写不敏感，支持别名）
     * @return 找到的属性定义，如果不存在则返回 null
     */
    fun find(propertyName: String): PropertyDefinition? {
        return properties.find { it.matches(propertyName) }
    }

    /**
     * 获取所有属性定义（用于文档/IDE支持/调试）
     *
     * @return 属性定义列表的副本
     */
    fun getAllProperties(): List<PropertyDefinition> = properties.toList()

    /**
     * 获取所有可能的属性名称（包括主名称和别名）
     * 用于调试和文档
     *
     * @return 所有属性名称的集合
     */
    fun getAllPropertyNames(): Set<String> {
        return properties.flatMap { it.allNames() }.toSet()
    }

    /**
     * 检查是否包含指定属性
     *
     * @param propertyName 属性名称
     * @return 是否包含
     */
    fun hasProperty(propertyName: String): Boolean {
        return find(propertyName) != null
    }
}
