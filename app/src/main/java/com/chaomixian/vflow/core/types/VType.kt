// 文件: main/java/com/chaomixian/vflow/core/types/VType.kt
package com.chaomixian.vflow.core.types

import android.content.Context

/**
 * 属性定义元数据
 */
data class VPropertyDef(
    val name: String,       // 属性名 (e.g. "width")
    val displayName: String,// 显示名 (e.g. "宽度") - Fallback
    val type: VType,        // 属性值的类型 (e.g. VTypeRegistry.NUMBER)
    val nameStringRes: Int? = null  // 显示名的字符串资源ID（用于国际化，可选）
) {
    /**
     * 获取本地化的显示名称
     * @param context Android上下文
     * @return 本地化的显示名称，优先使用字符串资源
     */
    fun getLocalizedName(context: Context): String {
        return nameStringRes?.let { context.getString(it) } ?: displayName
    }
}

interface VType {
    val id: String
    val name: String
    val parentType: VType?
    val properties: List<VPropertyDef>
        get() = emptyList()
}

data class SimpleVType(
    override val id: String,
    override val name: String,
    override val parentType: VType? = null,
    private val _properties: List<VPropertyDef> = emptyList()
) : VType {
    override val properties: List<VPropertyDef>
        get() = _properties
}