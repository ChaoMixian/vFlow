// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/HttpRequestModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import android.content.Intent
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
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextView

// ViewHolder 用于缓存视图引用
class HttpRequestViewHolder(view: View) : CustomEditorViewHolder(view) {
    val urlEditText: RichTextView = view.findViewById(R.id.http_edit_text_url)
    val urlMagicButton: ImageButton = view.findViewById(R.id.btn_url_magic)

    val methodSpinner: Spinner = view.findViewById(R.id.http_spinner_method)

    val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
    val advancedContainer: LinearLayout = view.findViewById(R.id.http_advanced_container)
    val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)

    val bodySectionContainer: LinearLayout = view.findViewById(R.id.http_body_section_container)
    val bodyTypeSpinner: Spinner = view.findViewById(R.id.http_spinner_body_type)
    val bodyEditorContainer: FrameLayout = view.findViewById(R.id.http_body_editor_container)

    // 引用 RecyclerView，以便在 createEditor 中绑定 Adapter
    val queryParamsRecyclerView: RecyclerView = view.findViewById<View>(R.id.http_query_params_editor).findViewById(R.id.recycler_view_dictionary)
    val queryParamsAddButton: Button = view.findViewById<View>(R.id.http_query_params_editor).findViewById(R.id.button_add_kv_pair)

    val headersRecyclerView: RecyclerView = view.findViewById<View>(R.id.http_headers_editor).findViewById(R.id.recycler_view_dictionary)
    val headersAddButton: Button = view.findViewById<View>(R.id.http_headers_editor).findViewById(R.id.button_add_kv_pair)

    var queryParamsAdapter: DictionaryKVAdapter? = null
    var headersAdapter: DictionaryKVAdapter? = null
    var bodyAdapter: DictionaryKVAdapter? = null

    var rawBodyRichTextView: RichTextView? = null

    var allSteps: List<ActionStep>? = null

    init {
        view.findViewById<View>(R.id.http_query_params_editor).findViewById<Button>(R.id.button_add_kv_pair).text = "添加参数"
        view.findViewById<View>(R.id.http_headers_editor).findViewById<Button>(R.id.button_add_kv_pair).text = "添加请求头"
    }
}

class HttpRequestModuleUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("url", "method", "headers", "query_params", "body_type", "body", "timeout", "show_advanced")

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
        holder.allSteps = allSteps
        val module = HttpRequestModule()

        // 初始化 URL
        val urlValue = currentParameters["url"] as? String ?: ""
        holder.urlEditText.setRichText(urlValue) { variableRef ->
            PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, allSteps ?: emptyList()))
        }
        // 绑定 URL 变量按钮
        holder.urlMagicButton.setOnClickListener {
            onMagicVariableRequested?.invoke("url")
        }

        // 初始化 Method Spinner
        holder.methodSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, module.methodOptions).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        holder.bodyTypeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, module.bodyTypeOptions).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 初始化 Query Params Adapter
        val currentQueryParams = (currentParameters["query_params"] as? Map<*, *>)
            ?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()

        holder.queryParamsAdapter = DictionaryKVAdapter(currentQueryParams) { key ->
            if (key.isNotBlank()) onMagicVariableRequested?.invoke("query_params.$key")
        }
        holder.queryParamsRecyclerView.apply {
            adapter = holder.queryParamsAdapter
            layoutManager = LinearLayoutManager(context)
        }
        holder.queryParamsAddButton.setOnClickListener { holder.queryParamsAdapter?.addItem() }

        // 初始化 Headers Adapter
        val currentHeaders = (currentParameters["headers"] as? Map<*, *>)
            ?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()

        holder.headersAdapter = DictionaryKVAdapter(currentHeaders) { key ->
            if (key.isNotBlank()) onMagicVariableRequested?.invoke("headers.$key")
        }
        holder.headersRecyclerView.apply {
            adapter = holder.headersAdapter
            layoutManager = LinearLayoutManager(context)
        }
        holder.headersAddButton.setOnClickListener { holder.headersAdapter?.addItem() }

        // 恢复其他参数
        (currentParameters["method"] as? String)?.let { holder.methodSpinner.setSelection(module.methodOptions.indexOf(it)) }
        (currentParameters["body_type"] as? String)?.let { holder.bodyTypeSpinner.setSelection(module.bodyTypeOptions.indexOf(it)) }

        // 更新 Body 编辑器
        updateBodyEditor(context, holder, currentParameters["body_type"] as? String, currentParameters["body"], onMagicVariableRequested)

        // 恢复高级选项的展开状态
        val showAdvanced = currentParameters["show_advanced"] as? Boolean ?: false
        holder.advancedContainer.isVisible = showAdvanced
        holder.expandArrow.rotation = if (showAdvanced) 180f else 0f

        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            // 触发参数变更，以便保存展开状态
            onParametersChanged()
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
                if (holder.bodyTypeSpinner.tag != p2) {
                    holder.bodyTypeSpinner.tag = p2
                    updateBodyEditor(context, holder, module.bodyTypeOptions[p2], null, onMagicVariableRequested)
                    onParametersChanged()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        holder.bodyTypeSpinner.tag = holder.bodyTypeSpinner.selectedItemPosition

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as HttpRequestViewHolder
        val body = when(h.bodyTypeSpinner.selectedItem.toString()) {
            "JSON", "表单" -> h.bodyAdapter?.getItemsAsMap()
            "原始文本" -> h.rawBodyRichTextView?.getRawText()
            else -> null
        }
        return mapOf(
            // 使用 getRawText() 读取 URL
            "url" to h.urlEditText.getRawText(),
            "method" to h.methodSpinner.selectedItem.toString(),
            "headers" to (h.headersAdapter?.getItemsAsMap() ?: emptyMap<String, String>()),
            "query_params" to (h.queryParamsAdapter?.getItemsAsMap() ?: emptyMap<String, String>()),
            "body_type" to h.bodyTypeSpinner.selectedItem.toString(),
            "body" to body,
            "show_advanced" to h.advancedContainer.isVisible
        )
    }

    private fun updateBodyEditor(
        context: Context,
        holder: HttpRequestViewHolder,
        bodyType: String?,
        currentValue: Any?,
        onMagicReq: ((String) -> Unit)?
    ) {
        holder.bodyEditorContainer.removeAllViews()
        holder.bodyAdapter = null
        holder.rawBodyRichTextView = null

        when (bodyType) {
            "JSON", "表单" -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.bodyEditorContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)
                addButton.text = "添加字段"

                val currentMap = (currentValue as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()

                holder.bodyAdapter = DictionaryKVAdapter(currentMap) { key ->
                    if (key.isNotBlank()) onMagicReq?.invoke("body.$key")
                }

                recyclerView.adapter = holder.bodyAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { holder.bodyAdapter?.addItem() }
                holder.bodyEditorContainer.addView(editorView)
            }
            "原始文本" -> {
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
                row.findViewById<TextView>(R.id.input_name).visibility = View.GONE

                val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

                magicButton.setOnClickListener { onMagicReq?.invoke("body") }

                val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
                val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

                richTextView.setRichText(currentValue?.toString() ?: "") { variableRef ->
                    PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
                }

                richTextView.tag = "rich_text_view_body"

                holder.rawBodyRichTextView = richTextView

                valueContainer.addView(richEditorLayout)
                holder.bodyEditorContainer.addView(row)
            }
        }
    }
}