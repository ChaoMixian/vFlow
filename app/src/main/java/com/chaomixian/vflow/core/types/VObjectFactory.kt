// 文件: main/java/com/chaomixian/vflow/core/types/VObjectFactory.kt
package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.*
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement

/**
 * 工厂类：负责将任意对象包装为 VObject。
 *
 * 设计原则：保留原始类型信息以支持严格等于比较。
 * 数字类型（Int, Long, Float, Double）不会被转换为 Double，而是保留原始类型。
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
            // 保留原始数字类型，不转换为 Double
            is Int -> VNumber(value)
            is Long -> VNumber(value)
            is Float -> VNumber(value)
            is Double -> VNumber(value)
            is Boolean -> VBoolean(value)

            // --- 业务对象 ---
            is NotificationObject -> VNotification(value)
            is UiElement -> VUiComponent(value, null)
            is UiEvent -> VEvent(value)

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