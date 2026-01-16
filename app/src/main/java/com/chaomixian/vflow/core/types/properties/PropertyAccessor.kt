// 文件: main/java/com/chaomixian/vflow/core/types/properties/PropertyAccessor.kt
package com.chaomixian.vflow.core.types.properties

import com.chaomixian.vflow.core.types.VObject

/**
 * 属性访问器接口
 * 将属性访问逻辑封装为可复用的对象
 */
interface PropertyAccessor {
    /**
     * 获取属性值
     * @param host 宿主对象，提供访问上下文
     * @return 属性值，如果无法获取则返回 null
     */
    fun get(host: VObject): VObject?
}

/**
 * 简单属性访问器：通过 lambda 获取值
 * 每次调用都会重新计算
 */
class SimplePropertyAccessor(
    private val getter: (VObject) -> VObject?
) : PropertyAccessor {
    override fun get(host: VObject): VObject? = getter(host)
}

/**
 * 延迟计算属性访问器：支持缓存
 * 首次访问时计算并缓存结果，后续访问返回缓存值
 * 适用于计算开销大的属性（如图片元数据）
 */
class LazyPropertyAccessor(
    private val getter: (VObject) -> VObject?
) : PropertyAccessor {
    private var cachedValue: VObject? = null
    private var computed = false

    override fun get(host: VObject): VObject? {
        if (!computed) {
            cachedValue = getter(host)
            computed = true
        }
        return cachedValue
    }

    /**
     * 重置缓存，强制下次访问时重新计算
     */
    fun resetCache() {
        cachedValue = null
        computed = false
    }
}

/**
 * 只读属性访问器：固定值
 * 不依赖于宿主对象的状态
 */
class ConstantPropertyAccessor(
    private val value: VObject
) : PropertyAccessor {
    override fun get(host: VObject): VObject? = value
}
