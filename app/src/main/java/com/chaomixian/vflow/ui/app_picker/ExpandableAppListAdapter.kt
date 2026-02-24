// 文件: main/java/com/chaomixian/vflow/ui/app_picker/ExpandableAppListAdapter.kt
// 描述: 可展开的应用列表适配器，支持显示应用下的Activity列表
package com.chaomixian.vflow.ui.app_picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import java.util.Locale

/**
 * Activity项数据
 */
data class ActivityItem(
    val name: String,
    val label: String,
    val isExported: Boolean
)

/**
 * 可展开的应用列表适配器
 */
class ExpandableAppListAdapter(
    private val mode: AppPickerMode,
    private val onAppClick: (AppInfo) -> Unit,
    private val onActivityClick: (AppInfo, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_APP = 0
        private const val VIEW_TYPE_ACTIVITY = 1
    }

    private var apps: List<AppInfo> = emptyList()
    private val expandedApps = mutableSetOf<String>()
    private val appActivities = mutableMapOf<String, List<ActivityItem>>()
    private var searchQuery: String = ""

    // 用于展平显示的数据
    private val displayItems = mutableListOf<DisplayItem>()

    data class DisplayItem(
        val type: Int,
        val appInfo: AppInfo? = null,
        val activityItem: ActivityItem? = null
    )

    fun updateData(newApps: List<AppInfo>) {
        apps = newApps
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun isExpanded(appInfo: AppInfo): Boolean {
        return expandedApps.contains(appInfo.packageName)
    }

    fun expand(appInfo: AppInfo, activities: List<ActivityItem>) {
        appActivities[appInfo.packageName] = activities
        expandedApps.add(appInfo.packageName)
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun expandAll(appsToExpand: List<AppInfo>, activitiesMap: Map<String, List<ActivityItem>>) {
        for (app in appsToExpand) {
            expandedApps.add(app.packageName)
            activitiesMap[app.packageName]?.let {
                appActivities[app.packageName] = it
            }
        }
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun collapse(appInfo: AppInfo) {
        expandedApps.remove(appInfo.packageName)
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    fun collapseAll() {
        expandedApps.clear()
        rebuildDisplayItems()
        notifyDataSetChanged()
    }

    private fun rebuildDisplayItems() {
        displayItems.clear()
        val lowercaseQuery = searchQuery.lowercase(Locale.getDefault())

        for (app in apps) {
            val appMatches = lowercaseQuery.isEmpty() ||
                    app.appName.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(lowercaseQuery)

            val activities = appActivities[app.packageName] ?: emptyList()

            // 过滤 Activity（如果有搜索词）
            val filteredActivities = if (lowercaseQuery.isEmpty()) {
                activities
            } else {
                activities.filter { activity ->
                    activity.name.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                            activity.label.lowercase(Locale.getDefault()).contains(lowercaseQuery)
                }
            }

            // 如果应用匹配，或者有匹配的 Activity，则显示
            val showApp = appMatches || (mode == AppPickerMode.SELECT_ACTIVITY && filteredActivities.isNotEmpty())

            if (showApp) {
                displayItems.add(DisplayItem(VIEW_TYPE_APP, appInfo = app))
                // 如果有匹配的 Activity 或者应用匹配，展开显示
                if (mode == AppPickerMode.SELECT_ACTIVITY && expandedApps.contains(app.packageName)) {
                    for (activity in filteredActivities) {
                        displayItems.add(DisplayItem(VIEW_TYPE_ACTIVITY, appInfo = app, activityItem = activity))
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return displayItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_APP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_expandable, parent, false)
                AppViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_activity_simple, parent, false)
                ActivityViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayItems[position]
        when (item.type) {
            VIEW_TYPE_APP -> {
                (holder as AppViewHolder).bind(item.appInfo!!, isExpanded(item.appInfo!!))
            }
            VIEW_TYPE_ACTIVITY -> {
                (holder as ActivityViewHolder).bind(item.appInfo!!, item.activityItem!!)
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expand_icon)

        fun bind(appInfo: AppInfo, isExpanded: Boolean) {
            appIcon.setImageDrawable(appInfo.icon)
            appName.text = appInfo.appName
            packageName.text = appInfo.packageName

            // 根据模式显示展开图标
            expandIcon.isVisible = mode == AppPickerMode.SELECT_ACTIVITY
            expandIcon.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )

            itemView.setOnClickListener { onAppClick(appInfo) }
        }
    }

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val activityName: TextView = itemView.findViewById(R.id.activity_name)
        private val activityLabel: TextView = itemView.findViewById(R.id.activity_label)
        private val activityWarning: TextView = itemView.findViewById(R.id.activity_warning)

        fun bind(appInfo: AppInfo, activityItem: ActivityItem) {
            activityName.text = activityItem.name
            activityLabel.text = activityItem.label
            activityLabel.isVisible = activityItem.label.isNotEmpty() && activityItem.label != activityItem.name
            activityWarning.isVisible = !activityItem.isExported

            itemView.setOnClickListener { onActivityClick(appInfo, activityItem.name) }
        }
    }
}
