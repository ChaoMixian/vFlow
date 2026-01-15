package com.chaomixian.vflow.core.workflow.module.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI 元素的类型枚举
 */
enum class UiElementType {
    TEXT,       // 纯文本展示
    INPUT,      // 输入框
    BUTTON,     // 按钮
    SWITCH,     // 开关
    // 后续可扩展 LIST, CHECKBOX 等
}

/**
 * UI 元素的数据定义，用于在 Intent 中传递
 */
@Parcelize
data class UiElement(
    val id: String,          // 对应的变量名 (key)
    val type: UiElementType, // 组件类型
    val label: String,       // 显示的标签或标题
    val defaultValue: String,// 默认值
    val placeholder: String, // 提示词
    val isRequired: Boolean,  // 是否必填
    val triggerEvent: Boolean = true
) : Parcelable