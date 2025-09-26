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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*

class ActionPickerSheet : BottomSheetDialogFragment() {
    var onActionSelected: ((ActionModule) -> Unit)? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var progressBar: ProgressBar
    private lateinit var noResultsView: TextView
    private lateinit var titleView: TextView

    private var allModuleGroups: Map<String, List<ActionModule>> = emptyMap()
    private var filteredModuleGroups: Map<String, List<ActionModule>> = emptyMap()

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
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = ActionPickerGroupAdapter(filteredModuleGroups) { module ->
            onActionSelected?.invoke(module)
            dismiss()
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                debounceHandler.removeCallbacksAndMessages(null)
                debounceHandler.postDelayed({ filterModules(newText.orEmpty()) }, 100)
                return true
            }
        })
    }

    private fun loadModules() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val isTriggerPicker = arguments?.getBoolean("is_trigger_picker", false) ?: false
            val categorizedModules = if (isTriggerPicker) {
                ModuleRegistry.getModulesByCategory().filterKeys { it == "触发器" }
            } else {
                ModuleRegistry.getModulesByCategory().filterKeys { it != "触发器" }
            }

            allModuleGroups = categorizedModules
            filteredModuleGroups = categorizedModules

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

            filteredModuleGroups = if (lowerCaseQuery.isEmpty()) {
                allModuleGroups
            } else {
                allModuleGroups.mapValues { (_, modules) ->
                    modules.filter {
                        it.metadata.name.lowercase().contains(lowerCaseQuery) ||
                                it.metadata.description.lowercase().contains(lowerCaseQuery)
                    }
                }.filterValues { it.isNotEmpty() }
            }

            withContext(Dispatchers.Main) {
                updateAdapterData()
            }
        }
    }

    private fun updateAdapterData() {
        (recyclerView.adapter as? ActionPickerGroupAdapter)?.updateData(filteredModuleGroups)
        noResultsView.visibility = if (filteredModuleGroups.isEmpty()) View.VISIBLE else View.GONE
    }
}


/**
 * 外部适配器，用于显示分组卡片列表。
 */
class ActionPickerGroupAdapter(
    private var moduleGroups: Map<String, List<ActionModule>>,
    private val onActionClick: (ActionModule) -> Unit
) : RecyclerView.Adapter<ActionPickerGroupAdapter.GroupViewHolder>() {

    private var groupList: List<Pair<String, List<ActionModule>>> = moduleGroups.toList()

    fun updateData(newGroups: Map<String, List<ActionModule>>) {
        this.moduleGroups = newGroups
        this.groupList = newGroups.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_picker_group_card, parent, false)
        return GroupViewHolder(view, onActionClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val (categoryName, modules) = groupList[position]
        holder.bind(categoryName, modules)
    }

    override fun getItemCount(): Int = groupList.size

    class GroupViewHolder(
        itemView: View,
        private val onActionClick: (ActionModule) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameTextView: TextView = itemView.findViewById(R.id.text_view_category_name)
        private val actionsRecyclerView: RecyclerView = itemView.findViewById(R.id.recycler_view_actions_grid)

        init {
            // 设置内部 RecyclerView 的布局管理器
            actionsRecyclerView.layoutManager = GridLayoutManager(itemView.context, 4)
            // 优化嵌套滚动
            actionsRecyclerView.isNestedScrollingEnabled = false
        }

        fun bind(categoryName: String, modules: List<ActionModule>) {
            categoryNameTextView.text = categoryName
            // 为内部 RecyclerView 设置适配器
            actionsRecyclerView.adapter = ActionPickerItemAdapter(modules, onActionClick)
        }
    }
}

/**
 * 内部适配器，用于在卡片内部的网格中显示模块项。
 */
class ActionPickerItemAdapter(
    private val modules: List<ActionModule>,
    private val onActionClick: (ActionModule) -> Unit
) : RecyclerView.Adapter<ActionPickerItemAdapter.ActionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.sheet_action_picker_grid_item, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(modules[position], onActionClick)
    }

    override fun getItemCount(): Int = modules.size

    class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.text_view_action_name)
        private val icon: ImageView = itemView.findViewById(R.id.icon_action_type)

        fun bind(module: ActionModule, onClick: (ActionModule) -> Unit) {
            name.text = module.metadata.name
            icon.setImageResource(module.metadata.iconRes)

            val context = itemView.context
            val colorStateList = ContextCompat.getColorStateList(context, com.google.android.material.R.color.material_dynamic_secondary60)
            DrawableCompat.setTintList(icon.drawable, colorStateList)

            itemView.setOnClickListener { onClick(module) }
        }
    }
}