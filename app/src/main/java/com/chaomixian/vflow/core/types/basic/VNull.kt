// 文件: main/java/com/chaomixian/vflow/core/types/basic/VNull.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 空对象（Null）的 VObject 实现
 * 使用属性注册表管理属性，但保留了特殊的 null 安全行为
 *
 * 特殊行为：任何属性访问都返回 VNull 自身，支持链式安全调用
 * 例如: nullObject.prop1.prop2 依然是 VNull，不会崩溃
 */
object VNull : EnhancedBaseVObject() {
    override val raw: Any? = null
    override val type = VTypeRegistry.NULL

    // VNull 不需要属性注册表，因为它有特殊的行为
    override val propertyRegistry = PropertyRegistry()

    override fun asString(): String = "" // 空对象转字符串为空串
    override fun asNumber(): Double? = 0.0
    override fun asBoolean(): Boolean = false

    // 空对象的任何属性都返回空对象，支持链式安全调用 (Safe Navigation)
    // 例如: nullObject.prop1.prop2 依然是 VNull，不会崩
    override fun getProperty(propertyName: String): VObject? {
        return this
    }

    override fun toString(): String = "VNull"
}
