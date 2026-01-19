// 文件: main/java/com/chaomixian/vflow/core/types/basic/VDictionary.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * 字典类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 *
 * 特殊逻辑：支持内置属性和动态Key查找
 */
@Parcelize
data class VDictionary(override val raw: @RawValue Map<String, VObject>) : EnhancedBaseVObject(), Parcelable {
    override val type = VTypeRegistry.DICTIONARY
    override val propertyRegistry: PropertyRegistry = VDictionaryCompanion.registry

    override fun asString(): String {
        // 生成 JSON 风格的字符串
        return raw.entries.joinToString(prefix = "{", postfix = "}") {
            "\"${it.key}\": \"${it.value.asString()}\""
        }
    }

    override fun asNumber(): Double? = raw.size.toDouble()

    override fun asBoolean(): Boolean = raw.isNotEmpty()

    // 转换为列表时，返回 Values 的列表
    override fun asList(): List<VObject> = raw.values.toList()

    /**
     * 重写 getProperty 以支持动态Key查找
     * 优先级：内置属性 > 直接Key匹配（大小写敏感）> VNull
     */
    override fun getProperty(propertyName: String): VObject? {
        // 1. 先查内置属性
        val builtinProp = propertyRegistry.find(propertyName)
        if (builtinProp != null) {
            return builtinProp.accessor.get(this)
        }

        // 2. 再查字典Key（直接匹配，大小写敏感）
        if (raw.containsKey(propertyName)) {
            return raw[propertyName]
        }

        // 3. 兜底返回 VNull（不再进行大小写不敏感匹配）
        return VNull
    }

    /**
     * 获取所有可用的键（供UI使用）
     * 返回格式：键名的列表，按插入顺序排序
     */
    fun getAvailableKeys(): List<String> = raw.keys.toList()

    /**
     * 获取所有可用的键及其值类型（供UI展示）
     */
    fun getAvailableKeysWithTypes(): List<Pair<String, String>> {
        return raw.entries.map { (key, value) ->
            val typeName = when (value) {
                is VString -> "文本"
                is VNumber -> "数字"
                is VBoolean -> "布尔"
                is VList -> "列表"
                is VDictionary -> "字典"
                else -> value.type.name
            }
            key to typeName
        }
    }
}

/**
 * VDictionary 的伴生对象，持有共享的属性注册表
 */
object VDictionaryCompanion {
    // 属性注册表：所有 VDictionary 实例共享
    val registry = PropertyRegistry().apply {
        register("count", "size", "数量", getter = { host ->
            VNumber((host as VDictionary).raw.size.toDouble())
        })
        register("keys", "键", getter = { host ->
            VList((host as VDictionary).raw.keys.map { VString(it) })
        })
        register("values", "值", getter = { host ->
            VList((host as VDictionary).raw.values.toList())
        })
        // 添加一个特殊的属性，用于UI获取键列表
        register("availableKeys", "可用键", getter = { host ->
            val dict = host as VDictionary
            VList(dict.getAvailableKeys().map { VString(it) })
        })
    }
}