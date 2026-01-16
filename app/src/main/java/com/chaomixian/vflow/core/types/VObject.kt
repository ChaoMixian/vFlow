// 文件: main/java/com/chaomixian/vflow/core/types/VObject.kt
package com.chaomixian.vflow.core.types

/**
 * vFlow 对象系统的核心接口。
 * 所有的运行时数据都必须封装为 VObject。
 */
interface VObject {
    /** 原始数据 (Java/Android 原生对象，如 String, Bitmap, List) */
    val raw: Any?

    /** 此对象的类型定义 */
    val type: VType

    /**
     * 获取属性。
     * 这是实现“魔法变量”属性访问（如 image.width）的关键。
     * @param propertyName 属性名称 (大小写不敏感)
     * @return 属性值包装为 VObject，如果属性不存在返回 null (或 VNull)
     */
    fun getProperty(propertyName: String): VObject?

    // --- 类型强制转换 (Coercion) ---
    // 所有的 VObject 都应该能“尽力”转换为基础类型，方便模块使用。

    /** 转换为文本表示 */
    fun asString(): String

    /** 转换为数字 (如果无法转换则返回 0.0 或 null) */
    fun asNumber(): Double?

    /** 转换为布尔值 (例如非空判断) */
    fun asBoolean(): Boolean

    /**
     * 转换为列表。
     * 如果本身是列表则返回自身，否则返回包含自身的单元素列表。
     * 这使得 "ForEach" 可以遍历任何东西。
     */
    fun asList(): List<VObject>
}