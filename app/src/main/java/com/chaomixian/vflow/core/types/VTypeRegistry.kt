// 文件: main/java/com/chaomixian/vflow/core/types/VTypeRegistry.kt
package com.chaomixian.vflow.core.types

object VTypeRegistry {
    // --- 基础类型 ---
    val ANY = SimpleVType("vflow.type.any", "任意")

    val STRING = SimpleVType("vflow.type.string", "文本", ANY, listOf(
        VPropertyDef("length", "长度", ANY),
        VPropertyDef("uppercase", "大写", ANY),
        VPropertyDef("lowercase", "小写", ANY),
        VPropertyDef("trim", "去空格", ANY)
    ))

    val NUMBER = SimpleVType("vflow.type.number", "数字", ANY, listOf(
        VPropertyDef("int", "整数部分", ANY),
        VPropertyDef("round", "四舍五入", ANY),
        VPropertyDef("abs", "绝对值", ANY)
    ))

    val BOOLEAN = SimpleVType("vflow.type.boolean", "布尔", ANY, listOf(
        VPropertyDef("not", "反转", ANY)
    ))

    val NULL = SimpleVType("vflow.type.null", "空", ANY)

    // --- 集合类型 ---
    val LIST = SimpleVType("vflow.type.list", "列表", ANY, listOf(
        VPropertyDef("count", "数量", NUMBER),
        VPropertyDef("first", "第一项", ANY),
        VPropertyDef("last", "最后一项", ANY),
        VPropertyDef("random", "随机一项", ANY)
    ))

    val DICTIONARY = SimpleVType("vflow.type.dictionary", "字典", ANY, listOf(
        VPropertyDef("count", "数量", NUMBER),
        VPropertyDef("keys", "所有键", LIST),
        VPropertyDef("values", "所有值", LIST)
    ))

    // --- 复杂业务类型 ---
    val IMAGE = SimpleVType("vflow.type.image", "图片", ANY, listOf(
        VPropertyDef("width", "宽度", NUMBER),
        VPropertyDef("height", "高度", NUMBER),
        VPropertyDef("path", "文件路径", STRING),
        VPropertyDef("size", "文件大小", NUMBER),
        VPropertyDef("name", "文件名", STRING)
    ))

    val DATE = SimpleVType("vflow.type.date", "日期", ANY, listOf(
        VPropertyDef("year", "年", NUMBER),
        VPropertyDef("month", "月", NUMBER),
        VPropertyDef("day", "日", NUMBER),
        VPropertyDef("weekday", "星期 (1-7)", NUMBER),
        VPropertyDef("timestamp", "时间戳", NUMBER)
    ))

    val TIME = SimpleVType("vflow.type.time", "时间", ANY, listOf(
        VPropertyDef("hour", "时", NUMBER),
        VPropertyDef("minute", "分", NUMBER)
    ))

    val UI_ELEMENT = SimpleVType("vflow.type.screen_element", "界面元素", ANY, listOf(
        VPropertyDef("text", "文本内容", STRING),
        VPropertyDef("center_x", "中心 X", NUMBER),
        VPropertyDef("center_y", "中心 Y", NUMBER),
        VPropertyDef("width", "宽度", NUMBER),
        VPropertyDef("height", "高度", NUMBER)
    ))

    val UI_COMPONENT = SimpleVType("vflow.type.uicomponent", "UI组件", ANY, listOf(
        VPropertyDef("id", "组件ID", STRING),
        VPropertyDef("type", "类型", STRING),
        VPropertyDef("label", "标签", STRING),
        VPropertyDef("value", "值", ANY),
        VPropertyDef("placeholder", "占位符", STRING),
        VPropertyDef("required", "必填", BOOLEAN),
        VPropertyDef("triggerEvent", "触发事件", BOOLEAN)
    ))

    val COORDINATE = SimpleVType("vflow.type.coordinate", "坐标", ANY, listOf(
        VPropertyDef("x", "X 坐标", NUMBER),
        VPropertyDef("y", "Y 坐标", NUMBER)
    ))

    val NOTIFICATION = SimpleVType("vflow.type.notification_object", "通知", ANY, listOf(
        VPropertyDef("title", "标题", STRING),
        VPropertyDef("content", "内容", STRING),
        VPropertyDef("package", "应用包名", STRING),
        VPropertyDef("id", "通知 ID", STRING)
    ))

    val APP = SimpleVType("vflow.type.app", "应用", ANY) // 暂无属性

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
            UI_ELEMENT.id -> UI_ELEMENT
            UI_COMPONENT.id -> UI_COMPONENT
            COORDINATE.id -> COORDINATE
            NOTIFICATION.id -> NOTIFICATION
            else -> ANY
        }
    }
}