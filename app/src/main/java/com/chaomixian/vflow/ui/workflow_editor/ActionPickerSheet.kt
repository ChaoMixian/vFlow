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

// 文件：ActionPickerSheet.kt
// 描述：用于选择新动作模块的底部动作表单。

/**
 * 动作模块选择器底部表单。
 * 显示按类别分组的可用模块列表，供用户选择添加到工作流中。
 */
class ActionPickerSheet : BottomSheetDialogFragment() {

    /** 当用户选择一个模块时的回调。 */
    var onActionSelected: ((ActionModule) -> Unit)? = null

    /** 创建并返回底部表单的视图。 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_action_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_action_picker)

        // 从模块注册表获取按类别分组的模块
        val categorizedModules = ModuleRegistry.getModulesByCategory()
        val pickerItems = mutableListOf<PickerItem>() // 用于Adapter的数据列表

        // 将模块数据转换为 Adapter 使用的 PickerItem 列表 (含类别标题和模块项)
        categorizedModules.forEach { (category, modules) ->
            pickerItems.add(PickerItem.Category(category)) // 添加类别项
            modules.forEach { module ->
                pickerItems.add(PickerItem.Action(module)) // 添加模块项
            }
        }

        recyclerView.adapter = ActionPickerAdapter(pickerItems) { module ->
            onActionSelected?.invoke(module) // 触发回调
            dismiss() // 关闭底部表单
        }
        return view
    }
}

/** RecyclerView 列表项的密封类定义 (类别或动作模块)。 */
sealed class PickerItem {
    data class Category(val name: String) : PickerItem() // 代表一个类别标题
    data class Action(val module: ActionModule) : PickerItem() // 代表一个可选择的动作模块
}

/**
 * ActionPickerSheet 中 RecyclerView 的适配器。
 * 处理类别标题和动作模块两种视图类型。
 */
class ActionPickerAdapter(
    private val items: List<PickerItem>,
    private val onClick: (ActionModule) -> Unit // 模块点击回调
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY = 0 // 视图类型：类别
        private const val TYPE_ACTION = 1   // 视图类型：动作模块
    }

    /** 根据位置返回列表项的视图类型。 */
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PickerItem.Category -> TYPE_CATEGORY
            is PickerItem.Action -> TYPE_ACTION
        }
    }

    /** 创建 ViewHolder 实例。 */
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

    /** 将数据绑定到 ViewHolder。 */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PickerItem.Category -> (holder as CategoryViewHolder).bind(item)
            is PickerItem.Action -> (holder as ActionViewHolder).bind(item.module, onClick)
        }
    }

    /** 返回列表项总数。 */
    override fun getItemCount() = items.size

    /** 类别项的 ViewHolder。 */
    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.text_view_category_name)
        fun bind(item: PickerItem.Category) {
            name.text = item.name
        }
    }

    /** 动作模块项的 ViewHolder。 */
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