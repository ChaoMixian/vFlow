// 文件: main/java/com/chaomixian/vflow/core/types/VObjectFactory.kt
package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.*
import com.chaomixian.vflow.core.workflow.module.interaction.Coordinate
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement

/**
 * 工厂类：负责将任意对象包装为 VObject。
 */
object VObjectFactory {

    /**
     * 将任意 Kotlin/Java 对象转换为 VObject。
     * 支持递归转换集合。
     */
    fun from(value: Any?): VObject {
        return when (value) {
            null -> VNull
            is VObject -> value // 防止重复包装

            // --- 基础类型 ---
            is String -> VString(value)
            is Int -> VNumber(value.toDouble())
            is Long -> VNumber(value.toDouble())
            is Float -> VNumber(value.toDouble())
            is Double -> VNumber(value)
            is Boolean -> VBoolean(value)

            // --- 旧版 Variable 类型兼容 ---
            is TextVariable -> VString(value.value)
            is NumberVariable -> VNumber(value.value)
            is BooleanVariable -> VBoolean(value.value)
            // 列表和字典需要递归处理
            is ListVariable -> fromCollection(value.value)
            is DictionaryVariable -> fromMap(value.value)
            is ImageVariable -> VImage(value.uri)
            is DateVariable -> VDate(value.value)
            is TimeVariable -> VTime(value.value)

            // --- 业务对象 ---
            is ScreenElement -> VScreenElement(value)
            is Coordinate -> VCoordinate(value)
            is NotificationObject -> VNotification(value)
            is UiElement -> VUiComponent(value, null)

            // --- 集合类型 ---
            is Collection<*> -> fromCollection(value)
            is Map<*, *> -> fromMap(value)
            is Array<*> -> fromCollection(value.toList())

            // --- 兜底 ---
            else -> VString(value.toString())
        }
    }

    private fun fromCollection(collection: Collection<*>): VList {
        val list = collection.map { from(it) }
        return VList(list)
    }

    private fun fromMap(map: Map<*, *>): VDictionary {
        val vMap = map.entries.associate { entry ->
            val key = entry.key?.toString() ?: "null"
            val value = from(entry.value)
            key to value
        }
        return VDictionary(vMap)
    }

    /**
     * 将 Map<String, Any?> 转换为 Map<String, VObject>
     * 用于执行引擎将模块输出转换为 VObject 格式
     */
    fun fromMapAny(outputs: Map<String, Any?>): Map<String, VObject> {
        return outputs.mapValues { (_, value) -> from(value) }
    }
}