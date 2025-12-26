// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/InvokeModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class InvokeModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val typeGroup: ChipGroup = view.findViewById(R.id.cg_invoke_type)
        val chipUri: Chip = view.findViewById(R.id.chip_uri)
        val chipActivity: Chip = view.findViewById(R.id.chip_activity)
        val chipBroadcast: Chip = view.findViewById(R.id.chip_broadcast)
        val chipService: Chip = view.findViewById(R.id.chip_service)

        val uriLayout: TextInputLayout = view.findViewById(R.id.til_uri)
        val uriEdit: TextInputEditText = view.findViewById(R.id.et_uri)

        val actionLayout: TextInputLayout = view.findViewById(R.id.til_action)
        val actionEdit: TextInputEditText = view.findViewById(R.id.et_action)

        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced)
        val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)

        val packageEdit: TextInputEditText = view.findViewById(R.id.et_package)
        val classEdit: TextInputEditText = view.findViewById(R.id.et_class)
        val typeEdit: TextInputEditText = view.findViewById(R.id.et_type)
        val flagsEdit: TextInputEditText = view.findViewById(R.id.et_flags)

        // Extras Editor
        val extrasRecyclerView: RecyclerView = view.findViewById(R.id.recycler_view_dictionary)
        val extrasAddButton: Button = view.findViewById(R.id.button_add_kv_pair)
        var extrasAdapter: DictionaryKVAdapter? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "mode", "uri", "action", "package", "class", "type", "flags", "extras", "show_advanced"
    )

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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_invoke_editor, parent, false)
        val holder = ViewHolder(view)

        // --- 恢复状态 ---
        val mode = currentParameters["mode"] as? String ?: "链接/Uri"
        when (mode) {
            "Activity" -> holder.chipActivity.isChecked = true
            "Broadcast" -> holder.chipBroadcast.isChecked = true
            "Service" -> holder.chipService.isChecked = true
            else -> holder.chipUri.isChecked = true
        }

        holder.uriEdit.setText(currentParameters["uri"] as? String ?: "")
        holder.actionEdit.setText(currentParameters["action"] as? String ?: "")
        holder.packageEdit.setText(currentParameters["package"] as? String ?: "")
        holder.classEdit.setText(currentParameters["class"] as? String ?: "")
        holder.typeEdit.setText(currentParameters["type"] as? String ?: "")
        holder.flagsEdit.setText(currentParameters["flags"] as? String ?: "")

        val showAdvanced = currentParameters["show_advanced"] as? Boolean ?: false
        holder.advancedContainer.isVisible = showAdvanced
        holder.expandArrow.rotation = if (showAdvanced) 180f else 0f

        // 初始化 Extras 适配器
        val currentExtras = (currentParameters["extras"] as? Map<*, *>)
            ?.map { it.key.toString() to it.value.toString() }
            ?.toMutableList() ?: mutableListOf()

        holder.extrasAdapter = DictionaryKVAdapter(currentExtras) { key ->
            // 支持魔法变量
            if (key.isNotBlank()) onMagicVariableRequested?.invoke("extras.$key")
        }
        holder.extrasRecyclerView.adapter = holder.extrasAdapter
        holder.extrasRecyclerView.layoutManager = LinearLayoutManager(context)
        holder.extrasAddButton.setOnClickListener { holder.extrasAdapter?.addItem() }

        // --- UI 逻辑更新 ---
        fun updateUiVisibility() {
            val currentMode = when {
                holder.chipActivity.isChecked -> "Activity"
                holder.chipBroadcast.isChecked -> "Broadcast"
                holder.chipService.isChecked -> "Service"
                else -> "链接/Uri"
            }
            if (currentMode == "链接/Uri") {
                holder.uriLayout.isVisible = true
                holder.actionLayout.isVisible = false
            } else {
                holder.uriLayout.isVisible = false
                holder.actionLayout.isVisible = true
            }
        }
        updateUiVisibility()

        // --- 监听器 ---
        holder.typeGroup.setOnCheckedStateChangeListener { _, _ ->
            updateUiVisibility()
            onParametersChanged()
        }

        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            onParametersChanged()
        }

        val textWatcher = { _: CharSequence? -> onParametersChanged() }
        holder.uriEdit.doAfterTextChanged(textWatcher)
        holder.actionEdit.doAfterTextChanged(textWatcher)
        holder.packageEdit.doAfterTextChanged(textWatcher)
        holder.classEdit.doAfterTextChanged(textWatcher)
        holder.typeEdit.doAfterTextChanged(textWatcher)
        holder.flagsEdit.doAfterTextChanged(textWatcher)

        // 监听 extras 的变化比较麻烦，这里简化为每次 readFromEditor 时读取最新数据
        // DictionaryKVAdapter 内部其实没有直接的回调，通常依赖于外部保存时读取

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val mode = when {
            h.chipActivity.isChecked -> "Activity"
            h.chipBroadcast.isChecked -> "Broadcast"
            h.chipService.isChecked -> "Service"
            else -> "链接/Uri"
        }

        return mapOf(
            "mode" to mode,
            "uri" to h.uriEdit.text.toString(),
            "action" to h.actionEdit.text.toString(),
            "package" to h.packageEdit.text.toString(),
            "class" to h.classEdit.text.toString(),
            "type" to h.typeEdit.text.toString(),
            "flags" to h.flagsEdit.text.toString(),
            "extras" to (h.extrasAdapter?.getItemsAsMap() ?: emptyMap<String, String>()),
            "show_advanced" to h.advancedContainer.isVisible
        )
    }
}