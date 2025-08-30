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
 * 用于在编辑器中选择魔法变量的底部弹窗。
 */
class MagicVariablePickerSheet : BottomSheetDialogFragment() {

    // 当用户选择一个变量时触发的回调
    var onVariableSelected: ((MagicVariableItem) -> Unit)? = null

    companion object {
        fun newInstance(availableVariables: List<MagicVariableItem>): MagicVariablePickerSheet {
            val fragment = MagicVariablePickerSheet()
            fragment.arguments = Bundle().apply {
                putParcelableArrayList("variables", ArrayList(availableVariables))
            }
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_magic_variable_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_magic_variables)

        val variables = arguments?.getParcelableArrayList<MagicVariableItem>("variables") ?: emptyList<MagicVariableItem>()

        recyclerView.adapter = MagicVariableAdapter(variables) { selectedVariable ->
            onVariableSelected?.invoke(selectedVariable)
            dismiss()
        }
        return view
    }
}

/**
 * 代表一个可供选择的魔法变量的数据类。
 * @param variableReference 变量的引用字符串，例如 "{{step-uuid.output-id}}"
 * @param variableName 显示给用户的变量名，例如 "找到的元素"
 * @param originModuleName 产生此变量的模块的名称，例如 "查找文本"
 */
@Parcelize
data class MagicVariableItem(
    val variableReference: String,
    val variableName: String,
    val originModuleName: String
) : Parcelable

/**
 * 魔法变量选择器的 RecyclerView 适配器。
 */
class MagicVariableAdapter(
    private val variables: List<MagicVariableItem>,
    private val onClick: (MagicVariableItem) -> Unit
) : RecyclerView.Adapter<MagicVariableAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.variable_name)
        val originTextView: TextView = view.findViewById(R.id.variable_origin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_magic_variable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = variables[position]
        holder.nameTextView.text = item.variableName
        holder.originTextView.text = "来自: ${item.originModuleName}"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = variables.size
}