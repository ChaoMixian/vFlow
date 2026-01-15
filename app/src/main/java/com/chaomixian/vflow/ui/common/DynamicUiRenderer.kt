// 文件: java/com/chaomixian/vflow/ui/common/DynamicUiRenderer.kt
package com.chaomixian.vflow.ui.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 动态 UI 渲染器
 *
 * 负责根据 UiElement 列表动态渲染 Android 视图。
 * 支持的组件类型：文本、输入框、开关、按钮。
 *
 * 功能：
 * - 将 UiElement 列表转换为 Android 视图并添加到容器中
 * - 为每个组件设置事件监听器（可选）
 * - 在触发事件时自动收集所有组件的值
 * - 提供视图查找和数据收集功能
 */
object DynamicUiRenderer {

    // 存储 ID 到 View 的映射，供 Activity 更新和查询使用
    private val viewsMap = mutableMapOf<String, View>()

    /**
     * 根据 ID 获取视图
     * @param id 组件 ID
     * @return 对应的视图，如果不存在则返回 null
     */
    fun getView(id: String): View? = viewsMap[id]

    /**
     * 收集所有输入组件的当前值
     *
     * 遍历所有已注册的视图，提取输入框和开关的值。
     * 用于在事件触发时自动收集数据。
     *
     * @return 组件 ID 到值的映射
     */
    fun collectAllValues(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        viewsMap.forEach { (id, view) ->
            when (view) {
                is TextInputEditText -> result[id] = view.text.toString()
                is MaterialSwitch -> result[id] = view.isChecked
            }
        }
        return result
    }

    fun render(
        context: Context,
        elements: List<UiElement>,
        container: ViewGroup,
        lifecycleScope: CoroutineScope? = null,
        sessionId: String? = null,
        onSubmit: (Map<String, Any>) -> Unit
    ) {
        viewsMap.clear()
        container.removeAllViews()

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 32)
        }

        elements.forEach { element ->
            var viewToRegister: View? = null

            val view: View? = when (element.type) {
                UiElementType.TEXT -> {
                    val tv = TextView(context).apply {
                        text = element.label
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    }
                    viewToRegister = tv
                    tv
                }
                UiElementType.INPUT -> {
                    val layout = TextInputLayout(context).apply {
                        hint = element.label
                        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    }
                    val editText = TextInputEditText(context).apply {
                        setText(element.defaultValue)
                        hint = element.placeholder

                        // 实时事件监听
                        if (element.triggerEvent && lifecycleScope != null && sessionId != null) {
                            addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val allValues = collectAllValues()
                                        UiSessionBus.emitEvent(UiEvent(sessionId, element.id, "change", s.toString(), allValues))
                                    }
                                }
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            })
                        }
                    }
                    layout.addView(editText)
                    viewToRegister = editText // 注册 EditText 以便获取值
                    layout
                }
                UiElementType.SWITCH -> {
                    val switch = MaterialSwitch(context).apply {
                        text = element.label
                        isChecked = element.defaultValue.toBoolean()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )

                        if (element.triggerEvent && lifecycleScope != null && sessionId != null) {
                            setOnCheckedChangeListener { _, isChecked ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val allValues = collectAllValues()
                                    UiSessionBus.emitEvent(UiEvent(sessionId, element.id, "change", isChecked, allValues))
                                }
                            }
                        }
                    }
                    viewToRegister = switch
                    switch
                }
                UiElementType.BUTTON -> {
                    val button = MaterialButton(context).apply {
                        text = element.label
                        setOnClickListener {
                            if (element.triggerEvent && lifecycleScope != null && sessionId != null) {
                                // 交互按钮：发送事件
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val allValues = collectAllValues()
                                    UiSessionBus.emitEvent(UiEvent(sessionId, element.id, "click", null, allValues))
                                }
                            } else {
                                // 提交按钮：收集数据并关闭
                                val result = collectData()
                                onSubmit(result)
                            }
                        }
                    }
                    viewToRegister = button
                    button
                }
            }

            if (view != null) {
                container.addView(view, params)
                if (viewToRegister != null) {
                    // 如果 element.id 为空，生成一个默认 ID
                    val viewId = if (element.id.isNotEmpty()) element.id else "view_${viewsMap.size}"
                    viewsMap[viewId] = viewToRegister
                }
            }
        }
    }

    fun collectData(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        viewsMap.forEach { (id, view) ->
            when (view) {
                is TextInputEditText -> result[id] = view.text.toString()
                is MaterialSwitch -> result[id] = view.isChecked
            }
        }
        return result
    }
}