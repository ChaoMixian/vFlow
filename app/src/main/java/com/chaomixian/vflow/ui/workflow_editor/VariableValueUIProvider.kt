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
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 一个可复用的预览 helper，专门用于在步骤卡片中
 * 详细地以内联方式显示字典和列表的内容。
 */
object VariableValueUIProvider {

    private fun buildSummaryLine(context: Context, prefix: String, value: Any?): CharSequence {
        return PillUtil.buildSpannable(
            context,
            prefix,
            PillUtil.Pill(value?.toString() ?: "", "value")
        )
    }

    fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val type = CreateVariableModule.TYPE_INPUT_DEFINITION.normalizeEnumValueOrNull(step.parameters["type"]?.toString())
            ?: CreateVariableModule.TYPE_STRING
        val value = step.parameters["value"]

        if (value is String && (value.isMagicVariable() || value.isNamedVariable())) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val cardView = inflater.inflate(R.layout.partial_variable_preview, parent, false)
        val previewContainer = cardView.findViewById<LinearLayout>(R.id.variable_preview_container)

        when (type) {
            CreateVariableModule.TYPE_DICTIONARY -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any?> ?: emptyMap()
                if (map.isEmpty()) return null

                map.forEach { (key, mapValue) ->
                    val summaryLine = buildSummaryLine(context, "$key: ", mapValue)
                    val renderedLine = PillRenderer.renderDisplayText(
                        context = context,
                        content = summaryLine,
                        allSteps = allSteps,
                        style = PillRenderer.DisplayStyle.SUMMARY
                    )
                    previewContainer.addView(createTextView(context, renderedLine ?: ""))
                }
            }
            CreateVariableModule.TYPE_LIST -> {
                @Suppress("UNCHECKED_CAST")
                val list = value as? List<Any?> ?: emptyList()
                if (list.isEmpty()) return null

                list.forEachIndexed { index, item ->
                    val summaryLine = buildSummaryLine(context, "${index + 1}. ", item)
                    val renderedLine = PillRenderer.renderDisplayText(
                        context = context,
                        content = summaryLine,
                        allSteps = allSteps,
                        style = PillRenderer.DisplayStyle.SUMMARY
                    )
                    previewContainer.addView(createTextView(context, renderedLine ?: ""))
                }
            }
            else -> return null
        }

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

}
