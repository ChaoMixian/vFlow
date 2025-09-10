// 文件: ActionPickerSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*

class ActionPickerSheet : BottomSheetDialogFragment() {
    // 统一的回调，返回被选中的模块。由调用者（如WorkflowEditorActivity）处理后续逻辑。
    var onActionSelected: ((ActionModule) -> Unit)? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var progressBar: ProgressBar
    private lateinit var noResultsView: TextView
    private lateinit var titleView: TextView

    private val allPickerItems = mutableListOf<PickerItem>()
    private var filteredPickerItems = mutableListOf<PickerItem>()
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_action_picker, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_action_picker)
        searchView = view.findViewById(R.id.search_view_actions)
        progressBar = view.findViewById(R.id.progress_bar)
        noResultsView = view.findViewById(R.id.text_view_no_results)
        titleView = view.findViewById(R.id.text_view_bottom_sheet_title)

        setupRecyclerView()
        setupSearch()
        loadModules()

        return view
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(context, 4) // 使用网格布局，每行4个
        recyclerView.adapter = ActionPickerAdapter(filteredPickerItems) { module ->
            // 无论选中什么，都通过一个统一的回调返回模块本身
            onActionSelected?.invoke(module)
            dismiss()
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                debounceHandler.removeCallbacksAndMessages(null)
                debounceHandler.postDelayed({
                    filterModules(newText.orEmpty())
                }, 100) // 300毫秒延迟
                return true
            }
        })
    }

    private fun loadModules() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        // 异步加载模块
        lifecycleScope.launch(Dispatchers.IO) {
            val isTriggerPicker = arguments?.getBoolean("is_trigger_picker", false) ?: false

            val categorizedModules = if (isTriggerPicker) {
                // 只获取“触发器”分类的模块
                ModuleRegistry.getModulesByCategory().filterKeys { it == "触发器" }
            } else {
                // 排除“触发器”分类，获取所有其他模块
                ModuleRegistry.getModulesByCategory().filterKeys { it != "触发器" }
            }

            val items = mutableListOf<PickerItem>()
            categorizedModules.forEach { (category, modules) ->
                items.add(PickerItem.Category(category))
                modules.forEach { module ->
                    items.add(PickerItem.Action(module))
                }
            }
            allPickerItems.clear()
            allPickerItems.addAll(items)
            filteredPickerItems.clear()
            filteredPickerItems.addAll(items)

            withContext(Dispatchers.Main) {
                titleView.text = if (isTriggerPicker) "选择一个触发器" else "选择一个动作"
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                updateAdapterData()
            }
        }
    }

    private fun filterModules(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase().trim()

            if (lowerCaseQuery.isEmpty()) {
                filteredPickerItems.clear()
                filteredPickerItems.addAll(allPickerItems)
            } else {
                val newFilteredList = mutableListOf<PickerItem>()
                var currentCategory: PickerItem.Category? = null

                allPickerItems.forEach { item ->
                    if (item is PickerItem.Category) {
                        currentCategory = item
                    } else if (item is PickerItem.Action) {
                        if (item.module.metadata.name.lowercase().contains(lowerCaseQuery) ||
                            item.module.metadata.description.lowercase().contains(lowerCaseQuery)) {
                            // 如果该分类还没被添加，则先添加分类
                            if (currentCategory != null && !newFilteredList.contains(currentCategory!!)) {
                                newFilteredList.add(currentCategory!!)
                            }
                            newFilteredList.add(item)
                        }
                    }
                }
                filteredPickerItems.clear()
                filteredPickerItems.addAll(newFilteredList)
            }

            withContext(Dispatchers.Main) {
                updateAdapterData()
            }
        }
    }

    private fun updateAdapterData() {
        // 更新GridLayout的SpanSizeLookup以实现分类标题占满一行
        (recyclerView.layoutManager as? GridLayoutManager)?.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (filteredPickerItems.getOrNull(position) is PickerItem.Category) {
                        4 // 分类标题占满4列
                    } else {
                        1 // 动作占1列
                    }
                }
            }

        (recyclerView.adapter as? ActionPickerAdapter)?.updateItems(filteredPickerItems)
        noResultsView.visibility = if (filteredPickerItems.filterIsInstance<PickerItem.Action>().isEmpty()) View.VISIBLE else View.GONE
    }
}

sealed class PickerItem {
    data class Category(val name: String) : PickerItem()
    data class Action(val module: ActionModule) : PickerItem()
}

class ActionPickerAdapter(
    private var items: List<PickerItem>,
    private val onClick: (ActionModule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_ACTION = 1
    }

    fun updateItems(newItems: List<PickerItem>) {
        this.items = newItems
        notifyDataSetChanged()
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
            val view = inflater.inflate(R.layout.sheet_action_picker_grid_item, parent, false)
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

            // 动态取色应用到图标
            val context = itemView.context
            val colorStateList = ContextCompat.getColorStateList(context, com.google.android.material.R.color.material_dynamic_secondary60)
            DrawableCompat.setTintList(icon.drawable, colorStateList)

            itemView.setOnClickListener { onClick(module) }
        }
    }
}