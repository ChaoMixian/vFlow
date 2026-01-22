// 文件：DictionaryKVAdapter.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.pill.PillVariableResolver
import com.google.android.material.textfield.TextInputLayout

/**
 * 字典键值对 (K-V) 编辑的 RecyclerView.Adapter。
 * @param data 存储键值对的可变列表，每个元素是一个 Pair<String, String>。
 * @param allSteps 工作流中的所有步骤，用于解析魔法变量和命名变量。
 * @param onMagicClick 当点击魔法变量按钮时的回调（传入键名）。
 */
class DictionaryKVAdapter(
    private val data: MutableList<Pair<String, String>>,
    private val allSteps: List<ActionStep>? = null,
    private val onMagicClick: ((key: String) -> Unit)? = null
) : RecyclerView.Adapter<DictionaryKVAdapter.ViewHolder>() {

    /** 将当前列表中的所有键值对转换为 Map<String, String>。 */
    fun getItemsAsMap(): Map<String, String> {
        return data.filter { it.first.isNotBlank() }.associate { it }
    }

    /** 添加一个新的空键值对到列表末尾。 */
    fun addItem() {
        data.add("" to "")
        notifyItemInserted(data.size - 1)
    }

    /** ViewHolder 定义，缓存视图引用。 */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyEditText: EditText = view.findViewById(R.id.edit_text_key)
        val valueContainer: ViewGroup = view.findViewById(R.id.value_container)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_kv)
        val magicButton: ImageButton = view.findViewById(R.id.button_magic_variable_for_value)
    }

    /** 创建 ViewHolder 实例。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary_kv, parent, false)
        return ViewHolder(view)
    }

    /** 将数据绑定到 ViewHolder。 */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.keyEditText.setText(item.first)

        holder.valueContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        // 检查是否是变量引用（魔法变量或命名变量）
        if (item.second.isMagicVariable() || item.second.isNamedVariable()) {
            val pillView = inflater.inflate(R.layout.magic_variable_pill, holder.valueContainer, false)
            val textView = pillView.findViewById<TextView>(R.id.pill_text)

            // 尝试使用 PillVariableResolver 解析变量以获取友好的显示名称
            val displayName = if (allSteps != null) {
                PillVariableResolver.resolveVariable(holder.itemView.context, item.second, allSteps)?.displayName
            } else {
                null
            } ?: run {
                // Fallback: 提取变量名作为显示文本
                when {
                    item.second.isMagicVariable() -> {
                        val content = item.second.removeSurrounding("{{", "}}")
                        val parts = content.split('.')
                        if (parts.size >= 2) "${parts[0]}.${parts[1]}" else content
                    }
                    item.second.isNamedVariable() -> {
                        item.second.removeSurrounding("[[", "]]")
                    }
                    else -> item.second
                }
            }

            textView.text = displayName
            holder.valueContainer.addView(pillView)
        } else {
            // 使用和XML中一致的 TextInputEditText，并设置好布局参数
            val textInputLayout = TextInputLayout(holder.itemView.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                hint = "值"
                val editText = com.google.android.material.textfield.TextInputEditText(this.context)
                editText.setText(item.second)
                addView(editText)

                (editText.tag as? android.text.TextWatcher)?.let { editText.removeTextChangedListener(it) }
                val valueWatcher = editText.doAfterTextChanged { text ->
                    if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                        data[holder.adapterPosition] = data[holder.adapterPosition].first to text.toString()
                    }
                }
                editText.tag = valueWatcher
            }
            holder.valueContainer.addView(textInputLayout)
        }

        (holder.keyEditText.tag as? android.text.TextWatcher)?.let { holder.keyEditText.removeTextChangedListener(it) }
        val keyWatcher = holder.keyEditText.doAfterTextChanged {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data[holder.adapterPosition] = it.toString() to data[holder.adapterPosition].second
            }
        }
        holder.keyEditText.tag = keyWatcher

        // 删除按钮点击事件
        holder.deleteButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
            }
        }

        holder.magicButton.isVisible = onMagicClick != null
        holder.magicButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                val currentKey = holder.keyEditText.text.toString()
                if (currentKey.isNotBlank()) {
                    onMagicClick?.invoke(currentKey)
                } else {
                    Toast.makeText(holder.itemView.context, "请先填写键的名称", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 返回数据项总数。 */
    override fun getItemCount() = data.size
}