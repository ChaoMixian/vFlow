// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// ViewHolder 用于缓存视图引用
class HttpRequestViewHolder(view: View) : CustomEditorViewHolder(view) {
    val urlEditText: TextInputEditText = view.findViewById(R.id.http_edit_text_url)
    val methodSpinner: Spinner = view.findViewById(R.id.http_spinner_method)

    val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
    val advancedContainer: LinearLayout = view.findViewById(R.id.http_advanced_container)
    val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)

    val bodySectionContainer: LinearLayout = view.findViewById(R.id.http_body_section_container)
    val bodyTypeSpinner: Spinner = view.findViewById(R.id.http_spinner_body_type)
    val bodyEditorContainer: FrameLayout = view.findViewById(R.id.http_body_editor_container)

    val queryParamsAdapter: DictionaryKVAdapter
    val headersAdapter: DictionaryKVAdapter
    var bodyAdapter: DictionaryKVAdapter? = null
    var rawBodyEditText: EditText? = null

    init {
        val queryParamsView = view.findViewById<View>(R.id.http_query_params_editor)
        queryParamsView.findViewById<Button>(R.id.button_add_kv_pair).text = "添加参数"
        queryParamsAdapter = DictionaryKVAdapter(mutableListOf())
        queryParamsView.findViewById<RecyclerView>(R.id.recycler_view_dictionary).apply {
            adapter = queryParamsAdapter
            layoutManager = LinearLayoutManager(context)
        }
        queryParamsView.findViewById<Button>(R.id.button_add_kv_pair).setOnClickListener { queryParamsAdapter.addItem() }

        val headersView = view.findViewById<View>(R.id.http_headers_editor)
        headersView.findViewById<Button>(R.id.button_add_kv_pair).text = "添加请求头"
        headersAdapter = DictionaryKVAdapter(mutableListOf())
        headersView.findViewById<RecyclerView>(R.id.recycler_view_dictionary).apply {
            adapter = headersAdapter
            layoutManager = LinearLayoutManager(context)
        }
        headersView.findViewById<Button>(R.id.button_add_kv_pair).setOnClickListener { headersAdapter.addItem() }
    }
}

class HttpRequestModuleUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("url", "method", "headers", "query_params", "body_type", "body", "timeout")

    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>, onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_http_request_editor, parent, false)
        val holder = HttpRequestViewHolder(view)
        val module = HttpRequestModule()

        // 初始化基础视图
        holder.urlEditText.setText(currentParameters["url"] as? String ?: "")
        holder.methodSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, module.methodOptions).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        holder.bodyTypeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, module.bodyTypeOptions).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 恢复参数
        (currentParameters["method"] as? String)?.let { holder.methodSpinner.setSelection(module.methodOptions.indexOf(it)) }
        (currentParameters["headers"] as? Map<*, *>)?.let { map ->
            val list = map.map { it.key.toString() to it.value.toString() }.toMutableList()
            holder.headersAdapter.addItem().let { holder.headersAdapter.updateItems(list) }
        }
        (currentParameters["query_params"] as? Map<*, *>)?.let { map ->
            val list = map.map { it.key.toString() to it.value.toString() }.toMutableList()
            holder.queryParamsAdapter.addItem().let { holder.queryParamsAdapter.updateItems(list) }
        }
        (currentParameters["body_type"] as? String)?.let { holder.bodyTypeSpinner.setSelection(module.bodyTypeOptions.indexOf(it)) }
        updateBodyEditor(context, holder, currentParameters["body_type"] as? String, currentParameters["body"])

        // 高级选项默认为折叠状态 (HTTP 模块不持久化此状态)
        holder.advancedContainer.isVisible = false
        holder.expandArrow.rotation = 0f

        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            // 不调用 onParametersChanged，因为此状态不保存
        }

        holder.methodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val method = module.methodOptions[p2]
                holder.bodySectionContainer.isVisible = method == "POST" || method == "PUT" || method == "PATCH"
                onParametersChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        holder.bodyTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                updateBodyEditor(context, holder, module.bodyTypeOptions[p2], null)
                onParametersChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as HttpRequestViewHolder
        val body = when(h.bodyTypeSpinner.selectedItem.toString()) {
            "JSON", "表单" -> h.bodyAdapter?.getItemsAsMap()
            "原始文本" -> h.rawBodyEditText?.text?.toString()
            else -> null
        }
        return mapOf(
            "url" to h.urlEditText.text.toString(),
            "method" to h.methodSpinner.selectedItem.toString(),
            "headers" to h.headersAdapter.getItemsAsMap(),
            "query_params" to h.queryParamsAdapter.getItemsAsMap(),
            "body_type" to h.bodyTypeSpinner.selectedItem.toString(),
            "body" to body
        )
    }

    private fun updateBodyEditor(context: Context, holder: HttpRequestViewHolder, bodyType: String?, currentValue: Any?) {
        holder.bodyEditorContainer.removeAllViews()
        holder.bodyAdapter = null
        holder.rawBodyEditText = null

        when (bodyType) {
            "JSON", "表单" -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.bodyEditorContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)
                addButton.text = "添加字段"

                val currentMap = (currentValue as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()
                holder.bodyAdapter = DictionaryKVAdapter(currentMap)
                recyclerView.adapter = holder.bodyAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { holder.bodyAdapter?.addItem() }
                holder.bodyEditorContainer.addView(editorView)
            }
            "原始文本" -> {
                val textInputLayout = TextInputLayout(context).apply {
                    hint = "请求体内容"
                }
                holder.rawBodyEditText = TextInputEditText(context).apply {
                    minLines = 5
                    gravity = android.view.Gravity.TOP
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setText(currentValue as? String ?: "")
                }
                textInputLayout.addView(holder.rawBodyEditText)
                holder.bodyEditorContainer.addView(textInputLayout)
            }
        }
    }

    private fun DictionaryKVAdapter.updateItems(items: MutableList<Pair<String, String>>) {
        (this::class.java.getDeclaredField("data").apply { isAccessible = true }.get(this) as MutableList<*>).clear()
        (this::class.java.getDeclaredField("data").apply { isAccessible = true }.get(this) as MutableList<Pair<String,String>>).addAll(items)
        notifyDataSetChanged()
    }
}