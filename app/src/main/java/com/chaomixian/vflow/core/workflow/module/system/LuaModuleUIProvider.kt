// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/LuaModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.google.android.material.textfield.TextInputLayout

class LuaEditorViewHolder(
    view: View,
    val scriptInput: EditText,
    val inputsAdapter: DictionaryKVAdapter
) : CustomEditorViewHolder(view)

class LuaModuleUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("script", "inputs")

    /**
     * 更新方法签名以匹配 ModuleUIProvider 接口。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    /**
     * 更新方法签名以匹配 ModuleUIProvider 接口。
     */
    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scriptInputLayout = TextInputLayout(context).apply {
            hint = "Lua 脚本"
        }
        val scriptInput = EditText(context).apply {
            minLines = 8
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(currentParameters["script"] as? String ?: "")
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scriptInputLayout.addView(scriptInput)
        view.addView(scriptInputLayout)

        val inputsEditorView = inflater.inflate(R.layout.partial_dictionary_editor, view, false)
        val inputsRecyclerView = inputsEditorView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_view_dictionary)
        val addInputButton = inputsEditorView.findViewById<android.widget.Button>(R.id.button_add_kv_pair)
        addInputButton.text = "添加输入"

        val currentInputs = (currentParameters["inputs"] as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                key?.toString()?.let { kStr ->
                    kStr to (value?.toString() ?: "")
                }
            }
            ?.toMutableList() ?: mutableListOf()

        // 在创建适配器时，传入 allSteps 以支持变量引用的显示名称解析
        val inputsAdapter = DictionaryKVAdapter(currentInputs, allSteps) { key ->
            if (key.isNotBlank()) {
                onMagicVariableRequested?.invoke("inputs.$key")
            }
        }

        inputsRecyclerView.adapter = inputsAdapter
        inputsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        addInputButton.setOnClickListener {
            inputsAdapter.addItem()
        }
        view.addView(inputsEditorView)

        return LuaEditorViewHolder(view, scriptInput, inputsAdapter)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val luaHolder = holder as LuaEditorViewHolder
        val script = luaHolder.scriptInput.text.toString()
        val inputs = luaHolder.inputsAdapter.getItemsAsMap()
        return mapOf("script" to script, "inputs" to inputs)
    }
}