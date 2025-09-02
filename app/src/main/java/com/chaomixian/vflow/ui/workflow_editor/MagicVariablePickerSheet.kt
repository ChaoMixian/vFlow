// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/MagicVariablePickerSheet.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize

/**
 * 代表一个可供选择的魔法变量的数据类。
 */
@Parcelize
data class MagicVariableItem(
    val variableReference: String,
    val variableName: String,
    val originModuleName: String
) : Parcelable

/**
 * 列表项的密封类，用于支持不同类型的项（变量 或 操作）。
 */
sealed class PickerListItem {
    object ClearSelection : PickerListItem()
    data class Variable(val item: MagicVariableItem) : PickerListItem()
}

class MagicVariablePickerSheet : BottomSheetDialogFragment() {

    // 回调现在可以返回一个可空的 MagicVariableItem，null 代表清除操作
    var onSelection: ((MagicVariableItem?) -> Unit)? = null

    companion object {
        fun newInstance(availableVariables: List<MagicVariableItem>): MagicVariablePickerSheet {
            return MagicVariablePickerSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList("variables", ArrayList(availableVariables))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_magic_variable_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_magic_variables)

        val variables = arguments?.getParcelableArrayList<MagicVariableItem>("variables") ?: emptyList()

        // 构建列表项，在最前面添加“清除”选项
        val items = mutableListOf<PickerListItem>().apply {
            add(PickerListItem.ClearSelection)
            addAll(variables.map { PickerListItem.Variable(it) })
        }

        recyclerView.adapter = MagicVariableAdapter(items) { selectedItem ->
            when (selectedItem) {
                is PickerListItem.ClearSelection -> onSelection?.invoke(null)
                is PickerListItem.Variable -> onSelection?.invoke(selectedItem.item)
            }
            dismiss()
        }
        return view
    }
}

/**
 * 魔法变量选择器的 RecyclerView 适配器 (支持多视图类型)。
 */
class MagicVariableAdapter(
    private val items: List<PickerListItem>,
    private val onClick: (PickerListItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ACTION = 0
        private const val TYPE_VARIABLE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PickerListItem.ClearSelection -> TYPE_ACTION
            is PickerListItem.Variable -> TYPE_VARIABLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ACTION -> {
                val view = inflater.inflate(R.layout.item_magic_variable_action, parent, false)
                ActionViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_magic_variable, parent, false)
                VariableViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> {
                holder.bind("清除 / 使用静态值")
                holder.itemView.setOnClickListener { onClick(item) }
            }
            is VariableViewHolder -> {
                val variableItem = (item as PickerListItem.Variable).item
                holder.bind(variableItem)
                holder.itemView.setOnClickListener { onClick(item) }
            }
        }
    }

    override fun getItemCount() = items.size

    class VariableViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.variable_name)
        private val originTextView: TextView = view.findViewById(R.id.variable_origin)
        fun bind(item: MagicVariableItem) {
            nameTextView.text = item.variableName
            originTextView.text = "来自: ${item.originModuleName}"
        }
    }

    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val actionTextView: TextView = view.findViewById(R.id.action_text)
        fun bind(text: String) {
            actionTextView.text = text
        }
    }
}