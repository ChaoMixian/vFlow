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
    var onActionSelected: ((ActionModule) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_action_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_action_picker)
        val title = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)

        // [修改] 根据参数判断是选择触发器还是动作
        val isTriggerPicker = arguments?.getBoolean("is_trigger_picker", false) ?: false

        val categorizedModules = if (isTriggerPicker) {
            title.text = "选择一个触发器"
            // 只获取“触发器”分类的模块
            ModuleRegistry.getModulesByCategory().filterKeys { it == "触发器" }
        } else {
            title.text = "选择一个动作"
            // 排除“触发器”分类，获取所有其他模块
            ModuleRegistry.getModulesByCategory().filterKeys { it != "触发器" }
        }

        val pickerItems = mutableListOf<PickerItem>()
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
// PickerItem 和 ActionPickerAdapter 类保持不变
sealed class PickerItem {
    data class Category(val name: String) : PickerItem()
    data class Action(val module: ActionModule) : PickerItem()
}

class ActionPickerAdapter(
    private val items: List<PickerItem>,
    private val onClick: (ActionModule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // ... 此适配器代码完全不变
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