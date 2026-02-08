// 文件: main/java/com/chaomixian/vflow/core/types/VTypeRegistry.kt
package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.R

object VTypeRegistry {
    // --- 基础类型 ---
    val ANY = SimpleVType("vflow.type.any", "任意")
    val NUMBER = SimpleVType("vflow.type.number", "数字", ANY, listOf(
        VPropertyDef("int", "整数部分", ANY, R.string.vtype_number_int),
        VPropertyDef("round", "四舍五入", ANY, R.string.vtype_number_round),
        VPropertyDef("abs", "绝对值", ANY, R.string.vtype_number_abs)
    ))
    val BOOLEAN = SimpleVType("vflow.type.boolean", "布尔", ANY, listOf(
        VPropertyDef("not", "反转", ANY, R.string.vtype_boolean_not)
    ))
    val STRING = SimpleVType("vflow.type.string", "文本", ANY, listOf(
        VPropertyDef("length", "长度", ANY, R.string.vtype_string_length),
        VPropertyDef("uppercase", "大写", ANY, R.string.vtype_string_uppercase),
        VPropertyDef("lowercase", "小写", ANY, R.string.vtype_string_lowercase),
        VPropertyDef("trim", "去除首尾空格", ANY, R.string.vtype_string_trim),
        VPropertyDef("removeSpaces", "去除所有空格", ANY, R.string.vtype_string_remove_spaces),
        VPropertyDef("isempty", "是否为空", ANY, R.string.vtype_string_isempty)
    ))

    val NULL = SimpleVType("vflow.type.null", "空", ANY)

    // --- 集合类型 ---
    val LIST = SimpleVType("vflow.type.list", "列表", ANY, listOf(
        VPropertyDef("count", "数量", ANY, R.string.vtype_list_count),
        VPropertyDef("first", "第一项", ANY, R.string.vtype_list_first),
        VPropertyDef("last", "最后一项", ANY, R.string.vtype_list_last),
        VPropertyDef("isempty", "是否为空", ANY, R.string.vtype_list_isempty),
        VPropertyDef("random", "随机一项", ANY, R.string.vtype_list_random)
    ))

    val DICTIONARY = SimpleVType("vflow.type.dictionary", "字典", ANY, listOf(
        VPropertyDef("count", "数量", ANY, R.string.vtype_dict_count),
        VPropertyDef("keys", "所有键", ANY, R.string.vtype_dict_keys),
        VPropertyDef("values", "所有值", ANY, R.string.vtype_dict_values)
    ))

    // --- 复杂业务类型 ---
    val IMAGE = SimpleVType("vflow.type.image", "图片", ANY, listOf(
        VPropertyDef("width", "宽度", ANY, R.string.vtype_image_width),
        VPropertyDef("height", "高度", ANY, R.string.vtype_image_height),
        VPropertyDef("path", "文件路径", ANY, R.string.vtype_image_path),
        VPropertyDef("uri", "URI地址", ANY, R.string.vtype_image_uri),
        VPropertyDef("size", "文件大小", ANY, R.string.vtype_image_size),
        VPropertyDef("name", "文件名", ANY, R.string.vtype_image_name)
    ))

    val DATE = SimpleVType("vflow.type.date", "日期", ANY, listOf(
        VPropertyDef("year", "年", ANY, R.string.vtype_date_year),
        VPropertyDef("month", "月", ANY, R.string.vtype_date_month),
        VPropertyDef("day", "日", ANY, R.string.vtype_date_day),
        VPropertyDef("weekday", "星期 (1-7)", ANY, R.string.vtype_date_weekday),
        VPropertyDef("timestamp", "时间戳", ANY, R.string.vtype_date_timestamp)
    ))

    val TIME = SimpleVType("vflow.type.time", "时间", ANY, listOf(
        VPropertyDef("hour", "时", ANY, R.string.vtype_time_hour),
        VPropertyDef("minute", "分", ANY, R.string.vtype_time_minute)
    ))

    val SCREEN_ELEMENT = SimpleVType("vflow.type.screen_element", "屏幕控件", ANY, listOf(
        // 文本属性
        VPropertyDef("text", "文本内容", ANY, R.string.vtype_screen_element_text),
        VPropertyDef("content_description", "内容描述", ANY, R.string.vtype_screen_element_content_description),
        // 标识属性
        VPropertyDef("id", "控件ID", ANY, R.string.vtype_screen_element_id),
        VPropertyDef("class", "类名", ANY, R.string.vtype_screen_element_class),
        // 位置属性
        VPropertyDef("center", "中心点", ANY, R.string.vtype_screen_element_center),
        VPropertyDef("region", "区域", ANY, R.string.vtype_screen_element_region),
        VPropertyDef("center_x", "中心 X", ANY, R.string.vtype_screen_element_center_x),
        VPropertyDef("center_y", "中心 Y", ANY, R.string.vtype_screen_element_center_y),
        VPropertyDef("left", "左边界", ANY, R.string.vtype_screen_element_left),
        VPropertyDef("top", "上边界", ANY, R.string.vtype_screen_element_top),
        VPropertyDef("right", "右边界", ANY, R.string.vtype_screen_element_right),
        VPropertyDef("bottom", "下边界", ANY, R.string.vtype_screen_element_bottom),
        // 尺寸属性
        VPropertyDef("width", "宽度", ANY, R.string.vtype_screen_element_width),
        VPropertyDef("height", "高度", ANY, R.string.vtype_screen_element_height),
        // 交互状态
        VPropertyDef("clickable", "可点击", ANY, R.string.vtype_screen_element_clickable),
        VPropertyDef("enabled", "已启用", ANY, R.string.vtype_screen_element_enabled),
        VPropertyDef("checkable", "可勾选", ANY, R.string.vtype_screen_element_checkable),
        VPropertyDef("checked", "已勾选", ANY, R.string.vtype_screen_element_checked),
        VPropertyDef("editable", "可编辑", ANY, R.string.vtype_screen_element_editable)
    ))

    val UI_COMPONENT = SimpleVType("vflow.type.uicomponent", "UI组件", ANY, listOf(
        VPropertyDef("id", "组件ID", ANY, R.string.vtype_uicomponent_id),
        VPropertyDef("type", "类型", ANY, R.string.vtype_uicomponent_type),
        VPropertyDef("label", "标签", ANY, R.string.vtype_uicomponent_label),
        VPropertyDef("value", "值", ANY, R.string.vtype_uicomponent_value),
        VPropertyDef("defaultvalue", "默认值", ANY, R.string.vtype_uicomponent_defaultvalue),
        VPropertyDef("placeholder", "占位符", ANY, R.string.vtype_uicomponent_placeholder),
        VPropertyDef("required", "必填", ANY, R.string.vtype_uicomponent_required),
        VPropertyDef("triggerEvent", "触发事件", ANY, R.string.vtype_uicomponent_triggerEvent),
        VPropertyDef("istext", "是否文本", ANY, R.string.vtype_uicomponent_istext),
        VPropertyDef("isbutton", "是否按钮", ANY, R.string.vtype_uicomponent_isbutton),
        VPropertyDef("isinput", "是否输入框", ANY, R.string.vtype_uicomponent_isinput),
        VPropertyDef("isswitch", "是否开关", ANY, R.string.vtype_uicomponent_isswitch)
    ))

    val COORDINATE = SimpleVType("vflow.type.coordinate", "坐标", ANY, listOf(
        VPropertyDef("x", "X 坐标", ANY, R.string.vtype_coordinate_x),
        VPropertyDef("y", "Y 坐标", ANY, R.string.vtype_coordinate_y)
    ))

    val COORDINATE_REGION = SimpleVType("vflow.type.coordinate_region", "坐标区域", ANY, listOf(
        VPropertyDef("left", "左边界", ANY, R.string.vtype_coord_region_left),
        VPropertyDef("top", "上边界", ANY, R.string.vtype_coord_region_top),
        VPropertyDef("right", "右边界", ANY, R.string.vtype_coord_region_right),
        VPropertyDef("bottom", "下边界", ANY, R.string.vtype_coord_region_bottom),
        VPropertyDef("width", "宽度", ANY, R.string.vtype_coord_region_width),
        VPropertyDef("height", "高度", ANY, R.string.vtype_coord_region_height),
        VPropertyDef("center", "中心点", COORDINATE, R.string.vtype_coord_region_center),
        VPropertyDef("center_x", "中心 X", ANY, R.string.vtype_coord_region_center_x),
        VPropertyDef("center_y", "中心 Y", ANY, R.string.vtype_coord_region_center_y)
    ))

    val NOTIFICATION = SimpleVType("vflow.type.notification_object", "通知", ANY, listOf(
        VPropertyDef("title", "标题", ANY, R.string.vtype_notification_title),
        VPropertyDef("content", "内容", ANY, R.string.vtype_notification_content),
        VPropertyDef("package", "应用包名", ANY, R.string.vtype_notification_package),
        VPropertyDef("id", "通知 ID", ANY, R.string.vtype_notification_id)
    ))

    val EVENT = SimpleVType("vflow.type.event", "UI事件", ANY, listOf(
        VPropertyDef("sessionId", "会话ID", ANY, R.string.vtype_event_session_id),
        VPropertyDef("elementId", "组件ID", ANY, R.string.vtype_event_element_id),
        VPropertyDef("type", "事件类型", ANY, R.string.vtype_event_type),
        VPropertyDef("value", "事件值", ANY, R.string.vtype_event_value)
    ))

    val APP = SimpleVType("vflow.type.app", "应用", ANY) // 暂无属性

    /**
     * 获取指定类型的指定属性的类型
     * 例如: getPropertyType(VTypeRegistry.STRING.id, "length") -> VTypeRegistry.NUMBER
     */
    fun getPropertyType(typeId: String?, propertyName: String): VType? {
        val type = getType(typeId)

        // 查找属性定义（仅通过属性名匹配）
        val propertyDef = type.properties.find { prop ->
            prop.name == propertyName
        }

        return if (propertyDef != null && propertyDef.type != ANY) {
            propertyDef.type
        } else {
            // 如果属性定义中的类型是 ANY，或者找不到属性定义，则根据属性名推断
            inferPropertyType(typeId, propertyName)
        }
    }

    /**
     * 推断属性的类型（当属性定义中的类型为 ANY 时使用）
     */
    private fun inferPropertyType(typeId: String?, propertyName: String): VType? {
        return when (typeId) {
            STRING.id -> {
                when (propertyName) {
                    "length", "len", "长度", "count" -> NUMBER
                    "uppercase", "大写", "lowercase", "小写", "trim", "trimmed", "去除首尾空格",
                    "removeSpaces", "remove_space", "去除所有空格" -> STRING
                    "isempty", "为空", "empty" -> BOOLEAN
                    else -> null
                }
            }
            NUMBER.id -> {
                when (propertyName) {
                    "int", "整数部分", "round", "四舍五入", "abs", "绝对值" -> NUMBER
                    else -> null
                }
            }
            BOOLEAN.id -> {
                when (propertyName) {
                    "not", "反转" -> BOOLEAN
                    else -> null
                }
            }
            LIST.id -> {
                when (propertyName) {
                    "count", "length", "大小", "数量" -> NUMBER
                    "isempty", "为空", "empty" -> BOOLEAN
                    else -> null
                }
            }
            DICTIONARY.id -> {
                when (propertyName) {
                    "count", "length", "大小", "数量" -> NUMBER
                    "isempty", "为空", "empty" -> BOOLEAN
                    "keys", "键", "values", "值" -> LIST
                    else -> null
                }
            }
            IMAGE.id -> {
                when (propertyName) {
                    "width", "宽度", "height", "高度", "size", "文件大小" -> NUMBER
                    "path", "文件路径", "uri", "URI地址", "name", "文件名" -> STRING
                    else -> null
                }
            }
            DATE.id -> {
                when (propertyName) {
                    "year", "年", "month", "月", "day", "日", "weekday", "星期", "timestamp", "时间戳" -> NUMBER
                    else -> null
                }
            }
            TIME.id -> {
                when (propertyName) {
                    "hour", "时", "minute", "分" -> NUMBER
                    else -> null
                }
            }
            SCREEN_ELEMENT.id, UI_COMPONENT.id -> {
                when (propertyName) {
                    "text", "文本内容", "content_description", "id", "type", "类型", "label", "标签", "placeholder", "占位符",
                    "package", "应用包名", "title", "标题", "content", "内容", "class", "className", "viewId" -> STRING
                    "center", "center_point" -> COORDINATE
                    "region", "bounds" -> COORDINATE_REGION
                    "center_x", "center_y", "left", "top", "right", "bottom", "width", "宽度", "height", "高度", "x", "y",
                    "depth", "child_count", "childCount", "accessibility_id" -> NUMBER
                    "value", "值", "defaultvalue", "默认值" -> ANY
                    "required", "必填", "triggerEvent", "触发事件", "istext", "isbutton", "isinput", "isswitch",
                    "clickable", "isClickable", "enabled", "isEnabled", "checkable", "isCheckable",
                    "checked", "isChecked", "focusable", "isFocusable", "focused", "isFocused",
                    "scrollable", "isScrollable", "long_clickable", "isLongClickable",
                    "selected", "isSelected", "editable", "isEditable" -> BOOLEAN
                    else -> null
                }
            }
            COORDINATE.id -> {
                when (propertyName) {
                    "x", "y" -> NUMBER
                    else -> null
                }
            }
            COORDINATE_REGION.id -> {
                when (propertyName) {
                    "left", "top", "right", "bottom" -> NUMBER
                    "width", "w" -> NUMBER  // width支持别名w
                    "height", "h" -> NUMBER  // height支持别名h
                    "center_x", "x" -> NUMBER
                    "center_y", "y" -> NUMBER
                    "center", "center_point" -> COORDINATE
                    "as_string", "string" -> STRING
                    "is_empty", "isEmpty", "is_valid", "isValid" -> BOOLEAN
                    else -> null
                }
            }
            NOTIFICATION.id -> {
                when (propertyName) {
                    "title", "标题", "content", "内容", "package", "应用包名", "id", "通知 ID" -> STRING
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * 检查某个类型是否匹配目标类型集合（包括该类型的属性）
     * @param typeId 要检查的类型ID
     * @param acceptedTypes 目标类型集合（如果为空，则接受所有类型）
     * @return true 如果类型本身匹配，或者该类型的任一属性匹配
     */
    fun isTypeOrAnyPropertyAccepted(typeId: String?, acceptedTypes: Set<String>): Boolean {
        if (acceptedTypes.isEmpty()) return true

        val type = getType(typeId)

        // 检查类型本身是否匹配
        if (type.id in acceptedTypes) return true

        // 检查是否有任一属性匹配
        for (property in type.properties) {
            val propertyType = getPropertyType(typeId, property.name)
            if (propertyType != null && propertyType.id in acceptedTypes) {
                return true
            }
        }

        return false
    }

    /**
     * 获取某个类型中匹配目标类型集合的所有属性
     * @param typeId 类型ID
     * @param acceptedTypes 目标类型集合（如果为空，则返回所有属性）
     * @return 匹配的属性列表
     */
    fun getAcceptedProperties(typeId: String?, acceptedTypes: Set<String>): List<VPropertyDef> {
        val type = getType(typeId)

        if (acceptedTypes.isEmpty()) return type.properties

        return type.properties.filter { property ->
            val propertyType = getPropertyType(typeId, property.name)
            propertyType != null && propertyType.id in acceptedTypes
        }
    }

    // 辅助方法：根据 ID 获取类型
    fun getType(id: String?): VType {
        return when(id) {
            STRING.id -> STRING
            NUMBER.id -> NUMBER
            BOOLEAN.id -> BOOLEAN
            LIST.id -> LIST
            DICTIONARY.id -> DICTIONARY
            IMAGE.id -> IMAGE
            DATE.id -> DATE
            TIME.id -> TIME
            SCREEN_ELEMENT.id -> SCREEN_ELEMENT
            UI_COMPONENT.id -> UI_COMPONENT
            COORDINATE.id -> COORDINATE
            COORDINATE_REGION.id -> COORDINATE_REGION
            NOTIFICATION.id -> NOTIFICATION
            EVENT.id -> EVENT
            else -> ANY
        }
    }
}