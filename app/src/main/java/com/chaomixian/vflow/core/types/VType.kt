// 文件: main/java/com/chaomixian/vflow/core/types/VType.kt
package com.chaomixian.vflow.core.types

import android.content.Context
import com.chaomixian.vflow.R

/**
 * 属性定义元数据
 */
data class VPropertyDef(
    val name: String,       // 属性名 (e.g. "width")
    val displayName: String,// 显示名 (e.g. "宽度") - Fallback
    val type: VType,        // 属性值的类型 (e.g. VTypeRegistry.NUMBER)
    val nameStringRes: Int? = null,  // 显示名的字符串资源ID（用于国际化，可选）
    val aliases: Set<String> = emptySet()
) {
    /**
     * 获取本地化的显示名称
     * @param context Android上下文
     * @return 本地化的显示名称，优先使用字符串资源
     */
    fun getLocalizedName(context: Context): String {
        return nameStringRes?.let { context.getString(it) } ?: displayName
    }

    fun matches(propertyName: String): Boolean {
        return name == propertyName || aliases.contains(propertyName)
    }
}

interface VType {
    val id: String
    val name: String
    val parentType: VType?
    val properties: List<VPropertyDef>
        get() = emptyList()

    fun getLocalizedName(context: Context): String {
        return when (id) {
            "vflow.type.any" -> context.getString(R.string.magic_variable_type_any)
            "vflow.type.number" -> context.getString(R.string.magic_variable_type_number)
            "vflow.type.boolean" -> context.getString(R.string.magic_variable_type_boolean)
            "vflow.type.string" -> context.getString(R.string.variable_type_text)
            "vflow.type.null" -> context.getString(R.string.magic_variable_type_null)
            "vflow.type.list" -> context.getString(R.string.magic_variable_type_list)
            "vflow.type.dictionary" -> context.getString(R.string.magic_variable_type_dictionary)
            "vflow.type.image" -> context.getString(R.string.magic_variable_type_image)
            "vflow.type.file" -> context.getString(R.string.magic_variable_type_file)
            "vflow.type.date" -> context.getString(R.string.magic_variable_type_date)
            "vflow.type.time" -> context.getString(R.string.magic_variable_type_time)
            "vflow.type.screen_element" -> context.getString(R.string.magic_variable_type_screen_element)
            "vflow.type.uicomponent" -> context.getString(R.string.magic_variable_type_ui_component)
            "vflow.type.coordinate" -> context.getString(R.string.magic_variable_type_coordinate)
            "vflow.type.coordinate_region" -> context.getString(R.string.magic_variable_type_coordinate_region)
            "vflow.type.notification_object" -> context.getString(R.string.magic_variable_type_notification)
            "vflow.type.event" -> context.getString(R.string.magic_variable_type_event)
            "vflow.type.app" -> context.getString(R.string.magic_variable_type_app)
            else -> name
        }
    }
}

class SimpleVType(
    override val id: String,
    override val name: String,
    override val parentType: VType? = null,
    private val nameStringRes: Int? = null,
    private val propertiesProvider: () -> List<VPropertyDef> = { emptyList() }
) : VType {
    override val properties: List<VPropertyDef>
        get() = propertiesProvider()

    override fun getLocalizedName(context: Context): String {
        return nameStringRes?.let(context::getString) ?: super<VType>.getLocalizedName(context)
    }
}
