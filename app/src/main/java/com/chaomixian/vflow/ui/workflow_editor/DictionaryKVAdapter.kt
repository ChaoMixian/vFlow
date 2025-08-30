package com.chaomixian.vflow.ui.workflow_editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R

/**
 * 用于在 ActionEditorSheet 中编辑字典键值对的适配器。
 */
class DictionaryKVAdapter(
    private val data: MutableList<Pair<String, String>>
) : RecyclerView.Adapter<DictionaryKVAdapter.ViewHolder>() {

    // 从UI读取所有键值对，构建成Map
    fun getItemsAsMap(): Map<String, String> {
        return data.associate { it }
    }

    fun addItem() {
        data.add("" to "")
        notifyItemInserted(data.size - 1)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyEditText: EditText = view.findViewById(R.id.edit_text_key)
        val valueEditText: EditText = view.findViewById(R.id.edit_text_value)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_kv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary_kv, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.keyEditText.setText(item.first)
        holder.valueEditText.setText(item.second)

        // 移除旧的监听器，防止重复触发
        holder.keyEditText.removeTextChangedListener(holder.itemView.tag as? android.text.TextWatcher)
        holder.valueEditText.removeTextChangedListener(holder.itemView.tag as? android.text.TextWatcher)

        // 监听文本变化并更新数据源
        val textWatcher = holder.keyEditText.doAfterTextChanged {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data[holder.adapterPosition] = it.toString() to data[holder.adapterPosition].second
            }
        }
        holder.valueEditText.doAfterTextChanged {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data[holder.adapterPosition] = data[holder.adapterPosition].first to it.toString()
            }
        }
        holder.itemView.tag = textWatcher // 存储监听器以便后续移除

        holder.deleteButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
            }
        }
    }

    override fun getItemCount() = data.size
}