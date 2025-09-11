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
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize

/**
 * 代表一个可供选择的变量的数据模型。
 * @param variableReference 变量的引用字符串。
 * 对于魔法变量, 格式为 "{{stepId.outputId}}";
 * 对于命名变量, 格式为 "[[variableName]]"。
 * @param variableName 变量的可读名称。
 * @param originDescription 描述变量来源的文本, 如 "来自: 查找文本" 或 "命名变量 (数字)"。
 */
@Parcelize
data class MagicVariableItem(
    val variableReference: String,
    val variableName: String,
    val originDescription: String
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
        /**
         * 创建 MagicVariablePickerSheet 实例，并接收过滤条件。
         * @param availableVariables 所有可用的变量列表。
         * @param acceptsMagicVariable 是否显示来自步骤输出的魔法变量。
         * @param acceptsNamedVariable 是否显示用户创建的命名变量。
         */
        fun newInstance(
            availableVariables: List<MagicVariableItem>,
            acceptsMagicVariable: Boolean,
            acceptsNamedVariable: Boolean
        ): MagicVariablePickerSheet {
            return MagicVariablePickerSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList("variables", ArrayList(availableVariables))
                    putBoolean("acceptsMagic", acceptsMagicVariable)
                    putBoolean("acceptsNamed", acceptsNamedVariable)
                }
            }
        }
    }

    /** 创建并返回底部表单的视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_magic_variable_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_magic_variables)

        val allVariables = arguments?.getParcelableArrayList<MagicVariableItem>("variables") ?: emptyList()
        val acceptsMagic = arguments?.getBoolean("acceptsMagic", true) ?: true
        val acceptsNamed = arguments?.getBoolean("acceptsNamed", true) ?: true

        // 根据传入的标志过滤变量列表
        val filteredVariables = allVariables.filter {
            (acceptsMagic && it.variableReference.isMagicVariable()) ||
                    (acceptsNamed && it.variableReference.isNamedVariable())
        }

        // 构建列表项：首项为“清除选择”，其余为过滤后的可用变量
        val items = mutableListOf<PickerListItem>().apply {
            add(PickerListItem.ClearSelection) // 添加清除操作项
            addAll(filteredVariables.map { PickerListItem.Variable(it) }) // 添加所有可用变量
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

    /**
     * ViewHolder 更新，以显示新的 originDescription。
     */
    class VariableViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.variable_name)
        private val originTextView: TextView = view.findViewById(R.id.variable_origin)
        fun bind(item: MagicVariableItem) {
            nameTextView.text = item.variableName
            // 直接显示 originDescription，内容已在 Activity 中准备好
            originTextView.text = item.originDescription
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