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
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.chaomixian.vflow.ui.workflow_editor.StandardControlFactory

// ViewHolder 用于缓存视图引用
class HttpRequestViewHolder(view: View) : CustomEditorViewHolder(view) {
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
        view.findViewById<View>(R.id.http_query_params_editor).findViewById<Button>(R.id.button_add_kv_pair).setText(
            R.string.button_http_add_query_param
        )
        view.findViewById<View>(R.id.http_headers_editor).findViewById<Button>(R.id.button_add_kv_pair).setText(
            R.string.button_http_add_header
        )
    }
}

class HttpRequestModuleUIProvider : ModuleUIProvider {
    companion object {
        private const val BODY_TYPE_NONE = "none"
        private const val BODY_TYPE_JSON = "json"
        private const val BODY_TYPE_FORM = "form"
        private const val BODY_TYPE_RAW = "raw"
        private const val BODY_TYPE_FILE = "file"
    }

    // URL 不在这里处理，让它使用标准控件
    override fun getHandledInputIds(): Set<String> = setOf("method", "headers", "query_params", "body_type", "body", "show_advanced")

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        // 为复杂的 URL 创建富文本预览框
        val rawUrl = step.parameters["url"]?.toString() ?: ""
        if (VariableResolver.isComplex(rawUrl)) {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
            val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

            val spannable = PillRenderer.renderRichTextToSpannable(context, rawUrl, allSteps)
            textView.text = spannable

            return previewView
        }
        return null
    }

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

        // URL 由标准控件处理，不在自定义UI中创建

        // 初始化 Method Spinner
        holder.methodSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, module.methodOptions).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val bodyTypeLabels = listOf(
            context.getString(R.string.option_vflow_network_http_request_body_none),
            context.getString(R.string.option_vflow_network_http_request_body_json),
            context.getString(R.string.option_vflow_network_http_request_body_form),
            context.getString(R.string.option_vflow_network_http_request_body_raw),
            context.getString(R.string.option_vflow_network_http_request_body_file)
        )
        holder.bodyTypeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, bodyTypeLabels).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 初始化 Query Params Adapter
        val currentQueryParams = (currentParameters["query_params"] as? Map<*, *>)
            ?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()

        holder.queryParamsAdapter = DictionaryKVAdapter(currentQueryParams, allSteps) { key ->
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

        holder.headersAdapter = DictionaryKVAdapter(currentHeaders, allSteps) { key ->
            if (key.isNotBlank()) onMagicVariableRequested?.invoke("headers.$key")
        }
        holder.headersRecyclerView.apply {
            adapter = holder.headersAdapter
            layoutManager = LinearLayoutManager(context)
        }
        holder.headersAddButton.setOnClickListener { holder.headersAdapter?.addItem() }

        // 恢复其他参数
        (currentParameters["method"] as? String)?.let { holder.methodSpinner.setSelection(module.methodOptions.indexOf(it)) }
        val bodyTypeInput = module.getInputs().first { it.id == "body_type" }
        val rawBodyType = currentParameters["body_type"] as? String ?: BODY_TYPE_NONE
        val normalizedBodyType = bodyTypeInput.normalizeEnumValue(rawBodyType) ?: rawBodyType
        holder.bodyTypeSpinner.setSelection(module.bodyTypeOptions.indexOf(normalizedBodyType).coerceAtLeast(0))

        // 更新 Body 编辑器
        updateBodyEditor(context, holder, normalizedBodyType, currentParameters["body"], onMagicVariableRequested)

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

        // URL 由标准控件处理，不在这里读取

        val selectedBodyType = HttpRequestModule().bodyTypeOptions.getOrElse(h.bodyTypeSpinner.selectedItemPosition) { BODY_TYPE_NONE }
        val body = when(selectedBodyType) {
            BODY_TYPE_FORM -> h.bodyAdapter?.getItemsAsMap()
            BODY_TYPE_JSON, BODY_TYPE_RAW, BODY_TYPE_FILE -> h.rawBodyRichTextView?.getRawText()
            else -> null
        }
        return mapOf(
            "method" to h.methodSpinner.selectedItem.toString(),
            "headers" to (h.headersAdapter?.getItemsAsMap() ?: emptyMap<String, String>()),
            "query_params" to (h.queryParamsAdapter?.getItemsAsMap() ?: emptyMap<String, String>()),
            "body_type" to selectedBodyType,
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
            BODY_TYPE_FORM -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.bodyEditorContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)
                addButton.setText(R.string.button_http_add_form_field)

                val currentMap = (currentValue as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMutableList() ?: mutableListOf()

                holder.bodyAdapter = DictionaryKVAdapter(currentMap, holder.allSteps) { key ->
                    if (key.isNotBlank()) onMagicReq?.invoke("body.$key")
                }

                recyclerView.adapter = holder.bodyAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { holder.bodyAdapter?.addItem() }
                holder.bodyEditorContainer.addView(editorView)
            }
            BODY_TYPE_JSON, BODY_TYPE_RAW, BODY_TYPE_FILE -> {
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
                row.findViewById<TextView>(R.id.input_name).visibility = View.GONE

                val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

                magicButton.setOnClickListener { onMagicReq?.invoke("body") }

                val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
                val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

                richTextView.setRichText(currentValue?.toString() ?: "", holder.allSteps ?: emptyList())

                // 为"文件"类型添加类型过滤，只允许图片类型变量
                if (bodyType == BODY_TYPE_FILE) {
                    richTextView.setVariableTypeFilter(setOf(VTypeRegistry.IMAGE.id))
                }

                richTextView.tag = "body"

                holder.rawBodyRichTextView = richTextView

                valueContainer.addView(richEditorLayout)
                holder.bodyEditorContainer.addView(row)
            }
        }
    }
}
