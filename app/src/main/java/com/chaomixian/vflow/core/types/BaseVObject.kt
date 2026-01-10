// 文件: main/java/com/chaomixian/vflow/core/types/BaseVObject.kt
package com.chaomixian.vflow.core.types

/**
 * VObject 的抽象基类，提供默认行为。
 */
abstract class BaseVObject : VObject {
    // 默认的 toString 使用 asString()，方便调试
    override fun toString(): String = asString()

    // 默认没有子属性
    override fun getProperty(propertyName: String): VObject? {
        return null
    }

    // 默认将自己包装成单元素列表
    override fun asList(): List<VObject> = listOf(this)
}