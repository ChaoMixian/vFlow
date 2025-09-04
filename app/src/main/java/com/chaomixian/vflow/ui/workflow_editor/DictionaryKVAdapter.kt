// 文件：DictionaryKVAdapter.kt
// 描述：用于在 ActionEditorSheet 中编辑字典 (Map)
// 类型参数的键值对的 RecyclerView 适配器。

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
 * 字典键值对 (K-V) 编辑的 RecyclerView.Adapter。
 * @param data 存储键值对的可变列表，每个元素是一个 Pair<String, String>。
 */
class DictionaryKVAdapter(
    private val data: MutableList<Pair<String, String>> // 键值对数据列表
) : RecyclerView.Adapter<DictionaryKVAdapter.ViewHolder>() {

    /** 将当前列表中的所有键值对转换为 Map<String, String>。 */
    fun getItemsAsMap(): Map<String, String> {
        return data.associate { it } // 使用 Pair 的扩展函数直接转换为 Map
    }

    /** 添加一个新的空键值对到列表末尾。 */
    fun addItem() {
        data.add("" to "") // 添加空字符串对
        notifyItemInserted(data.size - 1) // 通知适配器有新项插入
    }

    /** ViewHolder 定义，缓存视图引用。 */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyEditText: EditText = view.findViewById(R.id.edit_text_key)
        val valueEditText: EditText = view.findViewById(R.id.edit_text_value)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_kv)
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
        holder.keyEditText.setText(item.first) // 设置键
        holder.valueEditText.setText(item.second) // 设置值

        // 移除旧的 TextWatcher，防止重复监听和更新
        // (itemView.tag 用于存储 TextWatcher 实例，这里假设只存一个，可能需要更健壮的方案)
        (holder.keyEditText.tag as? android.text.TextWatcher)?.let { holder.keyEditText.removeTextChangedListener(it) }
        (holder.valueEditText.tag as? android.text.TextWatcher)?.let { holder.valueEditText.removeTextChangedListener(it) }

        // 监听键 EditText 的文本变化，并更新数据源
        val keyWatcher = holder.keyEditText.doAfterTextChanged {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) { // 确保 ViewHolder 仍然有效
                data[holder.adapterPosition] = it.toString() to data[holder.adapterPosition].second
            }
        }
        holder.keyEditText.tag = keyWatcher // 存储新的 TextWatcher

        // 监听值 EditText 的文本变化，并更新数据源
        val valueWatcher = holder.valueEditText.doAfterTextChanged {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data[holder.adapterPosition] = data[holder.adapterPosition].first to it.toString()
            }
        }
        holder.valueEditText.tag = valueWatcher

        // 删除按钮点击事件
        holder.deleteButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
                // notifyItemRangeChanged(holder.adapterPosition, data.size) // 可选：更新后续项的position
            }
        }
    }

    /** 返回数据项总数。 */
    override fun getItemCount() = data.size
}