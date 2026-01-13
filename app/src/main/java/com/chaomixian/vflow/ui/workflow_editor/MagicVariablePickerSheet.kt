// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/MagicVariablePickerSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    val originDescription: String,
    val typeId: String = "vflow.type.any"
) : Parcelable

/**
 * RecyclerView 列表项的密封类，支持两种类型：
 * 1. ClearAction: 一个特殊操作项，用于清除当前输入框的魔法变量连接。
 * 2. VariableGroup: 代表一个完整的变量分组，包含标题和变量列表，将渲染在一个卡片中。
 */
sealed class PickerListItem {
    object ClearAction : PickerListItem()
    data class VariableGroup(val title: String, val variables: List<MagicVariableItem>) : PickerListItem()
}

/**
 * 魔法变量选择器底部表单 (BottomSheetDialogFragment)。
 * 显示可用魔法变量的分组列表以及一个“清除”选项。
 */
class MagicVariablePickerSheet : BottomSheetDialogFragment() {

    /** 选择回调：当用户选择一个变量或清除操作时触发。null 表示清除了选择。 */
    var onSelection: ((MagicVariableItem?) -> Unit)? = null

    companion object {
        /**
         * 创建 MagicVariablePickerSheet 实例，并接收分组后的变量数据和过滤条件。
         */
        fun newInstance(
            stepVariables: Map<String, List<MagicVariableItem>>,
            namedVariables: Map<String, List<MagicVariableItem>>,
            acceptsMagicVariable: Boolean,
            acceptsNamedVariable: Boolean
        ): MagicVariablePickerSheet {
            return MagicVariablePickerSheet().apply {
                arguments = Bundle().apply {
                    putSerializable("stepVariables", HashMap(stepVariables))
                    putSerializable("namedVariables", HashMap(namedVariables))
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

        val acceptsMagic = arguments?.getBoolean("acceptsMagic", true) ?: true
        val acceptsNamed = arguments?.getBoolean("acceptsNamed", true) ?: true

        // 将分组数据转换为 RecyclerView 的列表项
        val items = mutableListOf<PickerListItem>().apply {
            add(PickerListItem.ClearAction) // 总是添加“清除”选项

            // 添加命名变量分组
            if (acceptsNamed) {
                @Suppress("UNCHECKED_CAST")
                val namedVariables = arguments?.getSerializable("namedVariables") as? Map<String, List<MagicVariableItem>>
                namedVariables?.forEach { (groupName, variableList) ->
                    add(PickerListItem.VariableGroup(groupName, variableList))
                }
            }

            // 添加步骤输出变量分组
            if (acceptsMagic) {
                @Suppress("UNCHECKED_CAST")
                val stepVariables = arguments?.getSerializable("stepVariables") as? Map<String, List<MagicVariableItem>>
                stepVariables?.forEach { (groupName, variableList) ->
                    add(PickerListItem.VariableGroup(groupName, variableList))
                }
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = MagicVariableAdapter(items) { selectedItem ->
            if (selectedItem == null) {
                onSelection?.invoke(null)
                dismiss()
            } else {
                handleVariableSelection(selectedItem)
            }
        }
        return view
    }

    private fun handleVariableSelection(item: MagicVariableItem) {
        val type = VTypeRegistry.getType(item.typeId)
        val properties = type.properties

        if (properties.isEmpty()) {
            onSelection?.invoke(item)
            dismiss()
        } else {
            val options = mutableListOf<String>()
            options.add("使用 ${item.variableName} 本身")
            properties.forEach { prop -> options.add("${prop.displayName} (${prop.name})") }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择 ${item.variableName} 的属性")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which == 0) {
                        onSelection?.invoke(item)
                    } else {
                        val prop = properties[which - 1]
                        val oldRef = item.variableReference
                        // 智能拼接属性
                        val newRef = when {
                            oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                                oldRef.removeSuffix("}}") + ".${prop.name}}}"
                            }
                            oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                                oldRef.removeSuffix("]]") + ".${prop.name}]]"
                            }
                            else -> oldRef
                        }

                        val newItem = item.copy(
                            variableReference = newRef,
                            variableName = "${item.variableName} 的 ${prop.displayName}"
                        )
                        onSelection?.invoke(newItem)
                    }
                    dismiss()
                }
                .show()
        }
    }
}

/**
 * MagicVariablePickerSheet 中 RecyclerView 的适配器。
 * 支持“清除操作”项和“变量分组卡片”项两种视图类型。
 */
class MagicVariableAdapter(private val items: List<PickerListItem>, private val onVariableClick: (MagicVariableItem?) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object { private const val TYPE_ACTION = 0; private const val TYPE_GROUP = 1 }
    override fun getItemViewType(position: Int) = if (items[position] is PickerListItem.ClearAction) TYPE_ACTION else TYPE_GROUP
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == TYPE_ACTION) ActionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_magic_variable_action, parent, false)) else GroupViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_magic_variable_group_card, parent, false), onVariableClick)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ActionViewHolder) { holder.bind("清除 / 使用静态值"); holder.itemView.setOnClickListener { onVariableClick(null) } }
        else if (holder is GroupViewHolder) { holder.bind(items[position] as PickerListItem.VariableGroup) }
    }
    override fun getItemCount() = items.size
    class GroupViewHolder(view: View, private val onVariableClick: (MagicVariableItem?) -> Unit) : RecyclerView.ViewHolder(view) {
        private val titleTextView: TextView = view.findViewById(R.id.group_title)
        private val variablesContainer: LinearLayout = view.findViewById(R.id.variables_container)
        fun bind(group: PickerListItem.VariableGroup) {
            titleTextView.text = group.title
            variablesContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            group.variables.forEach { variableItem ->
                val itemView = inflater.inflate(R.layout.item_magic_variable, variablesContainer, false)
                val nameTextView: TextView = itemView.findViewById(R.id.variable_name)
                val originTextView: TextView = itemView.findViewById(R.id.variable_origin)
                nameTextView.text = variableItem.variableName
                originTextView.text = variableItem.originDescription
                itemView.setOnClickListener { onVariableClick(variableItem) }
                variablesContainer.addView(itemView)
            }
        }
    }
    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val actionTextView: TextView = view.findViewById(R.id.action_text)
        fun bind(text: String) { actionTextView.text = text }
    }
}