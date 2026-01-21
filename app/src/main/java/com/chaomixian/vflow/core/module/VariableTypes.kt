// 文件：VariableTypes.kt
// 描述：定义了工作流中使用的核心数据类型。

/**
 * @deprecated 此文件中的类型已被 VObject 系统取代。
 *
 * **重要提示**: 此文件保留仅用于向后兼容和类型转换。
 * 新代码不应直接使用这些类型。
 *
 * 请使用以下新类型：
 * - 文本: [com.chaomixian.vflow.core.types.basic.VString]
 * - 数字: [com.chaomixian.vflow.core.types.basic.VNumber]
 * - 布尔: [com.chaomixian.vflow.core.types.basic.VBoolean]
 * - 列表: [com.chaomixian.vflow.core.types.basic.VList]
 * - 字典: [com.chaomixian.vflow.core.types.basic.VDictionary]
 * - 图像: [com.chaomixian.vflow.core.types.complex.VImage]
 * - 日期: [com.chaomixian.vflow.core.types.complex.VDate]
 * - 时间: [com.chaomixian.vflow.core.types.complex.VTime]
 *
 * 迁移示例:
 * ```kotlin
 * // 旧代码
 * val text = TextVariable("hello")
 *
 * // 新代码
 * val text = VString("hello")
 * ```
 *
 * VObjectFactory 会自动处理旧类型到新类型的转换。
 */
package com.chaomixian.vflow.core.module

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * 表示文本类型的变量。
 * @param value 变量的字符串值。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.basic.VString] 代替
 */
@Deprecated(
    message = "使用 VString 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VString(value)")
)
@Parcelize
data class TextVariable(val value: String) : Parcelable {
    companion object {
        /** 文本变量的唯一类型标识符。与 VTypeRegistry.STRING 保持一致 */
        const val TYPE_NAME = "vflow.type.string"
    }
}

/**
 * 表示数字类型的变量。
 * @param value 变量的 Double 值。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.basic.VNumber] 代替
 */
@Deprecated(
    message = "使用 VNumber 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VNumber(value)")
)
@Parcelize
data class NumberVariable(val value: Double) : Parcelable {
    companion object {
        /** 数字变量的唯一类型标识符。与 VTypeRegistry.NUMBER 保持一致 */
        const val TYPE_NAME = "vflow.type.number"
    }
}

/**
 * 表示布尔类型的变量。
 * @param value 变量的布尔值。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.basic.VBoolean] 代替
 */
@Deprecated(
    message = "使用 VBoolean 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VBoolean(value)")
)
@Parcelize
data class BooleanVariable(val value: Boolean) : Parcelable {
    companion object {
        /** 布尔变量的唯一类型标识符。与 VTypeRegistry.BOOLEAN 保持一致 */
        const val TYPE_NAME = "vflow.type.boolean"
    }
}

/**
 * 表示列表类型的变量。
 * @param value 变量的 List 值，列表元素可以是任意受支持的类型。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.basic.VList] 代替
 */
@Deprecated(
    message = "使用 VList 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VList(value.map { VObjectFactory.from(it) })")
)
@Parcelize
data class ListVariable(val value: @RawValue List<Any?>) : Parcelable {
    companion object {
        /** 列表变量的唯一类型标识符。与 VTypeRegistry.LIST 保持一致 */
        const val TYPE_NAME = "vflow.type.list"
    }
}

/**
 * 表示字典（Map）类型的变量。
 * @param value 变量的 Map 值，键为 String，值可以是任意受支持的类型。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.basic.VDictionary] 代替
 */
@Deprecated(
    message = "使用 VDictionary 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VDictionary(value.mapValues { VObjectFactory.from(it.value) })")
)
@Parcelize
data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable {
    companion object {
        /** 字典变量的唯一类型标识符。与 VTypeRegistry.DICTIONARY 保持一致 */
        const val TYPE_NAME = "vflow.type.dictionary"
    }
}

/**
 * 表示图像类型的变量。
 * @param uri 图像的 URI 字符串。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.complex.VImage] 代替
 */
@Deprecated(
    message = "使用 VImage 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VImage(uri)")
)
@Parcelize
data class ImageVariable(val uri: String) : Parcelable {
    companion object {
        /** 图像变量的唯一类型标识符。与 VTypeRegistry.IMAGE 保持一致 */
        const val TYPE_NAME = "vflow.type.image"
    }
}

/**
 * 表示时间类型的变量。
 * @param value 变量的时间值，格式为 "HH:mm"。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.complex.VTime] 代替
 */
@Deprecated(
    message = "使用 VTime 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VTime(value)")
)
@Parcelize
data class TimeVariable(val value: String) : Parcelable {
    companion object {
        /** 时间变量的唯一类型标识符。与 VTypeRegistry.TIME 保持一致 */
        const val TYPE_NAME = "vflow.type.time"
    }
}

/**
 * 表示日期类型的变量。
 * @param value 变量的日期值，格式为 "yyyy-MM-dd"。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.complex.VDate] 代替
 */
@Deprecated(
    message = "使用 VDate 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VDate(value)")
)
@Parcelize
data class DateVariable(val value: String) : Parcelable {
    companion object {
        /** 日期变量的唯一类型标识符。与 VTypeRegistry.DATE 保持一致 */
        const val TYPE_NAME = "vflow.type.date"
    }
}

/**
 * 表示屏幕上的一个UI元素。
 * @param bounds 元素在屏幕上的边界矩形。
 * @param text 元素的文本内容（可选）。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.complex.VScreenElement] 代替
 */
@Deprecated(
    message = "使用 VScreenElement 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VScreenElement(this)")
)
@Parcelize
data class ScreenElement(
    val bounds: android.graphics.Rect,
    val text: String?
) : Parcelable {
    companion object {
        /** ScreenElement 类型的唯一标识符。 */
        const val TYPE_NAME = "vflow.type.ui_element"
    }
}

/**
 * 表示屏幕上的一个坐标点。
 * @param x x轴坐标。
 * @param y y轴坐标。
 * @deprecated 使用 [com.chaomixian.vflow.core.types.complex.VCoordinate] 代替
 */
@Deprecated(
    message = "使用 VCoordinate 代替。此类型仅用于向后兼容。",
    replaceWith = ReplaceWith("VCoordinate(this)")
)
@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable {
    companion object {
        /** Coordinate 类型的唯一标识符。 */
        const val TYPE_NAME = "vflow.type.coordinate"
    }
}

/**
 * 检查字符串是否为魔法变量引用 (来自步骤输出)。
 * e.g., "{{stepId.outputId}}"
 */
fun String?.isMagicVariable(): Boolean = this?.startsWith("{{") == true && this.endsWith("}}")

/**
 * 检查字符串是否为命名变量引用。
 * e.g., "[[myCounter]]"
 */
fun String?.isNamedVariable(): Boolean = this?.startsWith("[[") == true && this.endsWith("]]")
