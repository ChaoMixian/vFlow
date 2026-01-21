package com.chaomixian.vflow.core.types.serialization

import com.chaomixian.vflow.core.types.*
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.*
import com.chaomixian.vflow.core.module.Coordinate
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException

/**
 * VObject 的 Gson TypeAdapter
 * 负责将 VObject 序列化为 JSON 并支持反序列化
 *
 * 序列化格式：
 * - 基础类型：{"type":"vflow.type.string","value":"hello"}
 * - 列表类型：{"type":"vflow.type.list","value":[...items...]}
 * - 字典类型：{"type":"vflow.type.dictionary","value":{"key":value,...}}
 */
class VObjectGsonAdapter : TypeAdapter<VObject>() {

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: VObject?) {
        if (value == null || value is VNull) {
            out.nullValue()
            return
        }

        out.beginObject()
        out.name("type").value(value.type.id)

        when (value) {
            is VString -> out.name("value").value(value.raw)
            is VNumber -> out.name("value").value(value.raw)
            is VBoolean -> out.name("value").value(value.raw)

            is VList -> {
                out.name("value").beginArray()
                value.raw.forEach { item ->
                    writeAny(out, item)
                }
                out.endArray()
            }

            is VDictionary -> {
                out.name("value").beginObject()
                value.raw.forEach { (k, v) ->
                    out.name(k)
                    writeAny(out, v)
                }
                out.endObject()
            }

            is VImage -> out.name("value").value(value.raw.toString())
            is VCoordinate -> {
                out.name("value").beginObject()
                out.name("x").value(value.coordinate.x)
                out.name("y").value(value.coordinate.y)
                out.endObject()
            }
            is VDate -> out.name("value").value(value.dateString)
            is VTime -> out.name("value").value(value.timeString)
            is VScreenElement -> {
                out.name("value").beginObject()
                out.name("bounds").beginObject()
                out.name("left").value(value.element.bounds.left)
                out.name("top").value(value.element.bounds.top)
                out.name("right").value(value.element.bounds.right)
                out.name("bottom").value(value.element.bounds.bottom)
                out.endObject()
                out.name("text").value(value.element.text ?: "")
                out.endObject()
            }
            is VNotification -> {
                out.name("value").beginObject()
                out.name("title").value(value.notification.title)
                out.name("content").value(value.notification.content)
                out.name("package").value(value.notification.packageName)
                out.name("id").value(value.notification.id)
                out.endObject()
            }
            is VUiComponent -> {
                out.name("value").beginObject()
                out.name("id").value(value.element.id)
                out.name("type").value(value.element.type.name.lowercase())
                out.name("label").value(value.element.label)
                // 简化序列化，只序列化关键字段
                out.endObject()
            }

            else -> out.name("value").value(value.asString())
        }

        out.endObject()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): VObject {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return VNull
        }

        reader.beginObject()

        var typeId: String? = null
        var rawValue: Any? = null

        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "type" -> typeId = reader.nextString()
                "value" -> rawValue = readValue(reader, typeId)
                else -> reader.skipValue()
            }
        }

        reader.endObject()

        // 根据类型 ID 创建对应的 VObject
        return createVObject(typeId, rawValue)
    }

    /**
     * 读取任意 JSON 值
     */
    private fun readValue(reader: JsonReader, typeId: String?): Any? {
        return when (reader.peek()) {
            com.google.gson.stream.JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            com.google.gson.stream.JsonToken.STRING -> reader.nextString()
            com.google.gson.stream.JsonToken.NUMBER -> reader.nextDouble()
            com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean()
            com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                val list = mutableListOf<Any?>()
                while (reader.hasNext()) {
                    list.add(readValue(reader, null))
                }
                reader.endArray()
                list
            }
            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val map = mutableMapOf<String, Any?>()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    map[key] = readValue(reader, null)
                }
                reader.endObject()
                map
            }
            else -> reader.skipValue()
        }
    }

    /**
     * 根据类型 ID 和原始值创建 VObject
     */
    private fun createVObject(typeId: String?, rawValue: Any?): VObject {
        val type = typeId?.let { VTypeRegistry.getType(it) } ?: VTypeRegistry.ANY

        return when (type.id) {
            VTypeRegistry.STRING.id -> VString(rawValue?.toString() ?: "")
            VTypeRegistry.NUMBER.id -> {
                when (rawValue) {
                    is Number -> VNumber(rawValue.toDouble())
                    is String -> rawValue.toDoubleOrNull()?.let { VNumber(it) } ?: VNull
                    else -> VNumber(0.0)
                }
            }
            VTypeRegistry.BOOLEAN.id -> {
                when (rawValue) {
                    is Boolean -> VBoolean(rawValue)
                    is String -> VBoolean(rawValue.toBoolean())
                    else -> VBoolean(false)
                }
            }
            VTypeRegistry.LIST.id -> {
                if (rawValue is List<*>) {
                    VList(rawValue.map { VObjectFactory.from(it) })
                } else {
                    VList(emptyList())
                }
            }
            VTypeRegistry.DICTIONARY.id -> {
                if (rawValue is Map<*, *>) {
                    val vMap = rawValue.entries.mapNotNull { entry ->
                        val key = entry.key?.toString()
                        if (key != null) {
                            key to VObjectFactory.from(entry.value)
                        } else {
                            null
                        }
                    }.toMap()
                    VDictionary(vMap)
                } else {
                    VDictionary(emptyMap())
                }
            }
            VTypeRegistry.IMAGE.id -> VImage(rawValue?.toString() ?: "")
            VTypeRegistry.DATE.id -> VDate(rawValue?.toString() ?: "")
            VTypeRegistry.TIME.id -> VTime(rawValue?.toString() ?: "")
            VTypeRegistry.COORDINATE.id -> {
                if (rawValue is Map<*, *>) {
                    val x = (rawValue["x"] as? Number)?.toDouble() ?: 0.0
                    val y = (rawValue["y"] as? Number)?.toDouble() ?: 0.0
                    VCoordinate(Coordinate(x.toInt(), y.toInt()))
                } else {
                    VNull
                }
            }
            else -> VString(rawValue?.toString() ?: "")
        }
    }

    /**
     * 写入任意值（用于列表/字典的递归序列化）
     */
    private fun writeAny(out: JsonWriter, value: Any?) {
        when (value) {
            null -> out.nullValue()
            is String -> out.value(value)
            is Number -> out.value(value)
            is Boolean -> out.value(value)
            is List<*> -> {
                out.beginArray()
                value.forEach { writeAny(out, it) }
                out.endArray()
            }
            is Map<*, *> -> {
                out.beginObject()
                value.forEach { (k, v) ->
                    out.name(k?.toString() ?: "null")
                    writeAny(out, v)
                }
                out.endObject()
            }
            else -> out.value(value.toString())
        }
    }
}
