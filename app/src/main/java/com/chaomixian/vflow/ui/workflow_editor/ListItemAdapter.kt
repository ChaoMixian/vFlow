// 文件路径: main/java/com/chaomixian/vflow/ui/workflow_editor/ListItemAdapter.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.isMagicVariable
import com.google.android.material.textfield.TextInputLayout

/**
 * 用于在编辑器中动态添加/删除/编辑列表项的 RecyclerView.Adapter。
 * @param data 存储列表项的可变列表。
 * @param onMagicClick 当用户点击某一项的魔法变量按钮时触发的回调。
 */
class ListItemAdapter(
    private val data: MutableList<String>,
    private val onMagicClick: (position: Int) -> Unit
) : RecyclerView.Adapter<ListItemAdapter.ViewHolder>() {

    fun addItem() {
        data.add("")
        notifyItemInserted(data.size - 1)
    }

    /**
     * 获取适配器内部的数据列表。
     * @return 当前所有列表项的只读列表。
     */
    fun getItems(): List<String> {
        return data.toList()
    }


    // 更新特定位置的项（通常用于连接魔法变量）
    fun updateItem(position: Int, value: String) {
        if (position >= 0 && position < data.size) {
            data[position] = value
            notifyItemChanged(position)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val valueContainer: ViewGroup = view.findViewById(R.id.value_container)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_item)
        val magicButton: ImageButton = view.findViewById(R.id.button_magic_variable_for_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemValue = data[position]
        holder.valueContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        // 如果值是一个魔法变量引用，则显示一个药丸(Pill)
        if (itemValue.isMagicVariable()) {
            val pillView = inflater.inflate(R.layout.magic_variable_pill, holder.valueContainer, false)
            pillView.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            holder.valueContainer.addView(pillView)
            pillView.setOnClickListener {
                if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                    onMagicClick(holder.adapterPosition)
                }
            }
        } else {
            // 否则，显示一个标准的文本输入框
            val textInputLayout = TextInputLayout(holder.itemView.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                hint = "项目 ${position + 1}"
                val editText = com.google.android.material.textfield.TextInputEditText(this.context)
                editText.setText(itemValue)
                addView(editText)

                // 移除旧的监听器以防重复触发
                (editText.tag as? android.text.TextWatcher)?.let { editText.removeTextChangedListener(it) }
                // 添加文本变化监听器，实时更新数据源
                val watcher = editText.doAfterTextChanged { text ->
                    if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                        data[holder.adapterPosition] = text.toString()
                    }
                }
                editText.tag = watcher
            }
            holder.valueContainer.addView(textInputLayout)
        }

        // 删除按钮的点击事件
        holder.deleteButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                data.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
            }
        }

        // 魔法变量按钮的点击事件
        holder.magicButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                onMagicClick(holder.adapterPosition)
            }
        }
    }

    override fun getItemCount() = data.size
}