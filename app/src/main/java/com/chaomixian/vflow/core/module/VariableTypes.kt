// 文件: main/java/com/chaomixian/vflow/core/module/VariableTypes.kt
package com.chaomixian.vflow.core.module // 包名已更新

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 文件：VariableTypes.kt
// 描述：定义了工作流中使用的核心数据类型。

/**
 * 表示文本类型的变量。
 * @param value 变量的字符串值。
 */
@Parcelize
data class TextVariable(val value: String) : Parcelable {
    companion object {
        /** 文本变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.text"
    }
}

/**
 * 表示数字类型的变量。
 * @param value 变量的 Double 值。
 */
@Parcelize
data class NumberVariable(val value: Double) : Parcelable {
    companion object {
        /** 数字变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.number"
    }
}

/**
 * 表示布尔类型的变量。
 * @param value 变量的布尔值。
 */
@Parcelize
data class BooleanVariable(val value: Boolean) : Parcelable {
    companion object {
        /** 布尔变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.boolean"
    }
}

/**
 * 表示列表类型的变量。
 * @param value 变量的 List 值，列表元素可以是任意受支持的类型。
 */
@Parcelize
data class ListVariable(val value: @RawValue List<Any?>) : Parcelable {
    companion object {
        /** 列表变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.list"
    }
}

/**
 * 表示字典（Map）类型的变量。
 * @param value 变量的 Map 值，键为 String，值可以是任意受支持的类型。
 */
@Parcelize
data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable {
    companion object {
        /** 字典变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.dictionary"
    }
}

/**
 * 表示图像类型的变量。
 * @param uri 图像的 URI 字符串。
 */
@Parcelize
data class ImageVariable(val uri: String) : Parcelable {
    companion object {
        /** 图像变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.image"
    }
}

/**
 * 表示时间类型的变量。
 * @param value 变量的时间值，格式为 "HH:mm"。
 */
@Parcelize
data class TimeVariable(val value: String) : Parcelable {
    companion object {
        /** 时间变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.time"
    }
}

/**
 * 表示日期类型的变量。
 * @param value 变量的日期值，格式为 "yyyy-MM-dd"。
 */
@Parcelize
data class DateVariable(val value: String) : Parcelable {
    companion object {
        /** 日期变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.date"
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