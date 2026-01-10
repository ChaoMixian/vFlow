// 文件: main/java/com/chaomixian/vflow/core/types/basic/VNull.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry

object VNull : BaseVObject() {
    override val raw: Any? = null
    override val type = VTypeRegistry.NULL

    override fun asString(): String = "" // 空对象转字符串为空串
    override fun asNumber(): Double? = 0.0
    override fun asBoolean(): Boolean = false

    // 空对象的任何属性都返回空对象，支持链式安全调用 (Safe Navigation)
    // 例如: nullObject.prop1.prop2 依然是 VNull，不会崩
    override fun getProperty(propertyName: String): VObject? {
        return this
    }
}