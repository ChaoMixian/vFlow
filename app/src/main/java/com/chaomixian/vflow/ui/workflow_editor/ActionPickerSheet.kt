package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ActionPickerSheet : BottomSheetDialogFragment() {

    // 回调函数，当用户选择一个模块时触发
    var onActionSelected: ((ActionModule) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_action_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_action_picker)

        // 从 ModuleRegistry 获取按类别分组的模块
        val categorizedModules = ModuleRegistry.getModulesByCategory()
        val pickerItems = mutableListOf<PickerItem>()

        // 将模块转换为可供 Adapter 使用的列表项
        categorizedModules.forEach { (category, modules) ->
            pickerItems.add(PickerItem.Category(category))
            modules.forEach { module ->
                pickerItems.add(PickerItem.Action(module))
            }
        }

        recyclerView.adapter = ActionPickerAdapter(pickerItems) { module ->
            onActionSelected?.invoke(module)
            dismiss()
        }
        return view
    }
}

// 用于 RecyclerView 的数据类
sealed class PickerItem {
    data class Category(val name: String) : PickerItem()
    data class Action(val module: ActionModule) : PickerItem()
}

// RecyclerView 的适配器
class ActionPickerAdapter(
    private val items: List<PickerItem>,
    private val onClick: (ActionModule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_ACTION = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PickerItem.Category -> TYPE_CATEGORY
            is PickerItem.Action -> TYPE_ACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CATEGORY) {
            val view = inflater.inflate(R.layout.item_action_picker_category, parent, false)
            CategoryViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_action_picker, parent, false)
            ActionViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PickerItem.Category -> (holder as CategoryViewHolder).bind(item)
            is PickerItem.Action -> (holder as ActionViewHolder).bind(item.module, onClick)
        }
    }

    override fun getItemCount() = items.size

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.text_view_category_name)
        fun bind(item: PickerItem.Category) {
            name.text = item.name
        }
    }

    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.text_view_action_name)
        private val icon: ImageView = view.findViewById(R.id.icon_action_type)
        fun bind(module: ActionModule, onClick: (ActionModule) -> Unit) {
            name.text = module.metadata.name
            icon.setImageResource(module.metadata.iconRes)
            itemView.setOnClickListener { onClick(module) }
        }
    }
}