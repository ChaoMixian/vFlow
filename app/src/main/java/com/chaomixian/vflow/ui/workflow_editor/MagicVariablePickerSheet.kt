// 文件: MagicVariablePickerSheet.kt
// 描述: 用于选择魔法变量的底部动作表单。
//      允许用户从前面步骤的输出中选择一个变量，或选择清除当前连接。

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
 * 代表一个可供选择的魔法变量的数据模型。
 * @param variableReference 魔法变量的引用字符串，如 "{{stepId.outputId}}"。
 * @param variableName 变量的可读名称。
 * @param originModuleName 产生此变量的模块的名称。
 */
@Parcelize
data class MagicVariableItem(
    val variableReference: String,
    val variableName: String,
    val originModuleName: String
) : Parcelable

/**
 * RecyclerView 列表项的密封类，支持两种类型：
 * 1. ClearSelection: 一个特殊操作项，用于清除当前输入框的魔法变量连接。
 * 2. Variable: 代表一个可选择的魔法变量。
 */
sealed class PickerListItem {
    object ClearSelection : PickerListItem() // 代表“清除选择/使用静态值”的操作项
    data class Variable(val item: MagicVariableItem) : PickerListItem() // 代表一个可选择的魔法变量
}

/**
 * 魔法变量选择器底部表单 (BottomSheetDialogFragment)。
 * 显示可用魔法变量列表以及一个“清除”选项。
 */
class MagicVariablePickerSheet : BottomSheetDialogFragment() {

    /** 选择回调：当用户选择一个变量或清除操作时触发。null 表示清除了选择。 */
    var onSelection: ((MagicVariableItem?) -> Unit)? = null

    companion object {
        /** 创建 MagicVariablePickerSheet 实例。 */
        fun newInstance(availableVariables: List<MagicVariableItem>): MagicVariablePickerSheet {
            return MagicVariablePickerSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList("variables", ArrayList(availableVariables))
                }
            }
        }
    }

    /** 创建并返回底部表单的视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_magic_variable_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_magic_variables)

        val variables = arguments?.getParcelableArrayList<MagicVariableItem>("variables") ?: emptyList()

        // 构建列表项：首项为“清除选择”，其余为可用变量
        val items = mutableListOf<PickerListItem>().apply {
            add(PickerListItem.ClearSelection) // 添加清除操作项
            addAll(variables.map { PickerListItem.Variable(it) }) // 添加所有可用变量
        }

        recyclerView.adapter = MagicVariableAdapter(items) { selectedItem ->
            when (selectedItem) {
                is PickerListItem.ClearSelection -> onSelection?.invoke(null) // 清除选择
                is PickerListItem.Variable -> onSelection?.invoke(selectedItem.item) // 选择变量
            }
            dismiss() // 关闭底部表单
        }
        return view
    }
}

/**
 * MagicVariablePickerSheet 中 RecyclerView 的适配器。
 * 支持“清除操作”项和“魔法变量”项两种视图类型。
 */
class MagicVariableAdapter(
    private val items: List<PickerListItem>,
    private val onClick: (PickerListItem) -> Unit // 列表项点击回调
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ACTION = 0    // 视图类型：操作项 (如清除)
        private const val TYPE_VARIABLE = 1  // 视图类型：变量项
    }

    /** 根据位置返回列表项的视图类型。 */
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PickerListItem.ClearSelection -> TYPE_ACTION
            is PickerListItem.Variable -> TYPE_VARIABLE
        }
    }

    /** 创建 ViewHolder 实例。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ACTION -> {
                val view = inflater.inflate(R.layout.item_magic_variable_action, parent, false)
                ActionViewHolder(view)
            }
            else -> { // TYPE_VARIABLE
                val view = inflater.inflate(R.layout.item_magic_variable, parent, false)
                VariableViewHolder(view)
            }
        }
    }

    /** 将数据绑定到 ViewHolder。 */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> {
                holder.bind("清除 / 使用静态值") // 设置操作项文本
                holder.itemView.setOnClickListener { onClick(item) }
            }
            is VariableViewHolder -> {
                val variableItem = (item as PickerListItem.Variable).item
                holder.bind(variableItem) // 绑定变量数据
                holder.itemView.setOnClickListener { onClick(item) }
            }
        }
    }

    /** 返回列表项总数。 */
    override fun getItemCount() = items.size

    /** 魔法变量项的 ViewHolder。 */
    class VariableViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.variable_name)
        private val originTextView: TextView = view.findViewById(R.id.variable_origin)
        fun bind(item: MagicVariableItem) {
            nameTextView.text = item.variableName
            originTextView.text = "来自: ${item.originModuleName}"
        }
    }

    /** 操作项 (如“清除选择”) 的 ViewHolder。 */
    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val actionTextView: TextView = view.findViewById(R.id.action_text)
        fun bind(text: String) {
            actionTextView.text = text
        }
    }
}