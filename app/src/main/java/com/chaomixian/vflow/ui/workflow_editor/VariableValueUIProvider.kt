// 文件路径: main/java/com/chaomixian/vflow/ui/workflow_editor/VariableValueUIProvider.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 一个可复用的 ModuleUIProvider，专门用于在步骤卡片中
 * 详细地以内联方式显示字典和列表的内容。
 */
class VariableValueUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("value")

    /**
     * 现在会先加载 CardView，再找到内部的 LinearLayout 来填充内容。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val type = step.parameters["type"] as? String
        val value = step.parameters["value"]

        if (value is String && (value.isMagicVariable() || value.isNamedVariable())) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        // 1. 加载整个卡片视图
        val cardView = inflater.inflate(R.layout.partial_variable_preview, parent, false)
        // 2. 从卡片中找到用于填充内容的容器
        val previewContainer = cardView.findViewById<LinearLayout>(R.id.variable_preview_container)


        when (type) {
            "字典" -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any?> ?: emptyMap()
                if (map.isEmpty()) return null

                map.forEach { (key, mapValue) ->
                    val summaryLine = PillUtil.buildSpannable(context, "$key: ", PillUtil.Pill(mapValue.toString(), "value"))
                    val renderedLine = PillRenderer.renderPills(context, summaryLine, allSteps, step)
                    val textView = createTextView(context, renderedLine ?: "")
                    previewContainer.addView(textView)
                }
            }
            "列表" -> {
                @Suppress("UNCHECKED_CAST")
                val list = value as? List<Any?> ?: emptyList()
                if (list.isEmpty()) return null

                list.forEachIndexed { index, item ->
                    val summaryLine = PillUtil.buildSpannable(context, "${index + 1}. ", PillUtil.Pill(item.toString(), "value"))
                    val renderedLine = PillRenderer.renderPills(context, summaryLine, allSteps, step)
                    val textView = createTextView(context, renderedLine ?: "")
                    previewContainer.addView(textView)
                }
            }
            else -> return null
        }

        // 3. 只有当容器内确实有内容时，才返回整个卡片视图
        return if (previewContainer.childCount > 0) cardView else null
    }

    private fun createTextView(context: Context, text: CharSequence): TextView {
        return TextView(context).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
    }

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?, onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        throw NotImplementedError("VariableValueUIProvider does not create a custom editor.")
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        throw NotImplementedError("VariableValueUIProvider does not read from a custom editor.")
    }
}