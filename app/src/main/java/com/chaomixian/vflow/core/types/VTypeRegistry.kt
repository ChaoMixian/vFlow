package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.core.types.basic.VBooleanCompanion
import com.chaomixian.vflow.core.types.basic.VDictionaryCompanion
import com.chaomixian.vflow.core.types.basic.VListCompanion
import com.chaomixian.vflow.core.types.basic.VNumberCompanion
import com.chaomixian.vflow.core.types.basic.VStringCompanion
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VCoordinateRegion
import com.chaomixian.vflow.core.types.complex.VDate
import com.chaomixian.vflow.core.types.complex.VEvent
import com.chaomixian.vflow.core.types.complex.VFile
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.types.complex.VTime
import com.chaomixian.vflow.core.types.complex.VUiComponent

object VTypeRegistry {
    val ANY = SimpleVType("vflow.type.any", "任意")
    val NUMBER = SimpleVType("vflow.type.number", "数字", ANY) { VNumberCompanion.propertyDefs() }
    val BOOLEAN = SimpleVType("vflow.type.boolean", "布尔", ANY) { VBooleanCompanion.propertyDefs() }
    val STRING = SimpleVType("vflow.type.string", "文本", ANY) { VStringCompanion.propertyDefs() }
    val NULL = SimpleVType("vflow.type.null", "空", ANY)

    val LIST = SimpleVType("vflow.type.list", "列表", ANY) { VListCompanion.propertyDefs() }
    val DICTIONARY = SimpleVType("vflow.type.dictionary", "字典", ANY) { VDictionaryCompanion.propertyDefs() }

    val IMAGE = SimpleVType("vflow.type.image", "图片", ANY) { VImage.propertyDefs() }
    val FILE = SimpleVType("vflow.type.file", "文件", ANY) { VFile.propertyDefs() }
    val DATE = SimpleVType("vflow.type.date", "日期", ANY) { VDate.propertyDefs() }
    val TIME = SimpleVType("vflow.type.time", "时间", ANY) { VTime.propertyDefs() }
    val SCREEN_ELEMENT = SimpleVType("vflow.type.screen_element", "屏幕控件", ANY) { VScreenElement.propertyDefs() }
    val UI_COMPONENT = SimpleVType("vflow.type.uicomponent", "UI组件", ANY) { VUiComponent.propertyDefs() }
    val COORDINATE = SimpleVType("vflow.type.coordinate", "坐标", ANY) { VCoordinate.propertyDefs() }
    val COORDINATE_REGION = SimpleVType("vflow.type.coordinate_region", "坐标区域", ANY) { VCoordinateRegion.propertyDefs() }
    val NOTIFICATION = SimpleVType("vflow.type.notification_object", "通知", ANY) { VNotification.propertyDefs() }
    val EVENT = SimpleVType("vflow.type.event", "UI事件", ANY) { VEvent.propertyDefs() }
    val APP = SimpleVType("vflow.type.app", "应用", ANY)

    fun getPropertyType(typeId: String?, propertyName: String): VType? {
        val type = getType(typeId)
        return type.properties.firstOrNull { it.matches(propertyName) }?.type
    }

    fun isTypeOrAnyPropertyAccepted(typeId: String?, acceptedTypes: Set<String>): Boolean {
        if (acceptedTypes.isEmpty()) return true

        val type = getType(typeId)
        if (type.id in acceptedTypes) return true

        return type.properties.any { property ->
            property.type.id in acceptedTypes
        }
    }

    fun getAcceptedProperties(typeId: String?, acceptedTypes: Set<String>): List<VPropertyDef> {
        val type = getType(typeId)
        if (acceptedTypes.isEmpty()) return type.properties

        return type.properties.filter { property ->
            property.type.id in acceptedTypes
        }
    }

    fun getType(id: String?): VType {
        return when (id) {
            STRING.id -> STRING
            NUMBER.id -> NUMBER
            BOOLEAN.id -> BOOLEAN
            LIST.id -> LIST
            DICTIONARY.id -> DICTIONARY
            IMAGE.id -> IMAGE
            FILE.id -> FILE
            DATE.id -> DATE
            TIME.id -> TIME
            SCREEN_ELEMENT.id -> SCREEN_ELEMENT
            UI_COMPONENT.id -> UI_COMPONENT
            COORDINATE.id -> COORDINATE
            COORDINATE_REGION.id -> COORDINATE_REGION
            NOTIFICATION.id -> NOTIFICATION
            EVENT.id -> EVENT
            APP.id -> APP
            NULL.id -> NULL
            else -> ANY
        }
    }
}
