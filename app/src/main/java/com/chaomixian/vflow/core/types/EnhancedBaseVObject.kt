// 文件: main/java/com/chaomixian/vflow/core/types/EnhancedBaseVObject.kt
package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 增强的基类：提供自动属性管理能力
 *
 * 此类扩展了 BaseVObject，通过属性注册表（PropertyRegistry）自动处理属性访问。
 * 子类只需在 propertyRegistry 中声明属性，无需手动实现 getProperty() 方法。
 *
 * 使用示例：
 * ```
 * class VString(override val raw: String) : EnhancedBaseVObject() {
 *     override val type = VTypeRegistry.STRING
 *     override val propertyRegistry = Companion.registry
 *
 *     companion object {
 *         private val registry = PropertyRegistry().apply {
 *             register("length", "len", "长度") { host ->
 *                 VNumber((host as VString).raw.length.toDouble())
 *             }
 *         }
 *     }
 * }
 * ```
 */
abstract class EnhancedBaseVObject : BaseVObject() {

    /**
     * 属性注册表：每个子类必须提供
     * 通常在 companion object 中定义，所有实例共享同一个注册表
     */
    protected abstract val propertyRegistry: PropertyRegistry

    /**
     * 增强的属性访问：自动在注册表中查找属性
     *
     * 实现逻辑：
     * 1. 先在注册表中查找属性
     * 2. 如果找到，使用对应的访问器获取值
     * 3. 如果没找到，调用父类方法返回 null
     *
     * @param propertyName 属性名称（大小写不敏感，支持别名）
     * @return 属性值，如果属性不存在则返回 null
     */
    override fun getProperty(propertyName: String): VObject? {
        // 1. 先查注册表
        val propertyDef = propertyRegistry.find(propertyName)
        if (propertyDef != null) {
            return propertyDef.accessor.get(this)
        }

        // 2. 兜底：调用父类（返回 null）
        return super.getProperty(propertyName)
    }

    /**
     * 获取所有可用属性的名称（用于调试、文档、IDE自动完成）
     *
     * @return 包含主名称和所有别名的集合
     */
    fun getAvailableProperties(): Set<String> {
        return propertyRegistry.getAllPropertyNames()
    }

    /**
     * 获取所有属性定义（用于调试和文档）
     *
     * @return 属性定义列表
     */
    fun getPropertyDefinitions() = propertyRegistry.getAllProperties()

    /**
     * 检查是否包含指定属性
     *
     * @param propertyName 属性名称
     * @return 是否包含
     */
    fun hasProperty(propertyName: String): Boolean {
        return propertyRegistry.hasProperty(propertyName)
    }
}
