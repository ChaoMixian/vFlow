// 文件: main/java/com/chaomixian/vflow/ui/app_picker/AppListAdapter.kt
// 描述: 应用选择界面的 RecyclerView 适配器。
//      它负责将应用信息（图标、名称）绑定到列表的每一行视图上。
package com.chaomixian.vflow.ui.app_picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R

/**
 * AppListAdapter 类
 * 这是一个为 RecyclerView 服务的适配器，用于显示应用列表。
 * @param apps 应用信息的数据列表，包含了每个应用的名称、包名和图标。
 * @param onItemClick 一个回调函数，当用户点击列表中的任何一项时会被调用。
 */
class AppListAdapter(
    private var apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    /**
     * ViewHolder 内部类
     * 它的作用是缓存列表项视图（item_app.xml）中的子视图引用（例如 TextView 和 ImageView），
     * 这样就无需在每次更新列表项时都通过 findViewById 来查找它们，从而提高性能。
     * @param view 列表项的根视图。
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon) // 应用图标
        val appName: TextView = view.findViewById(R.id.app_name) // 应用名称
        val packageName: TextView = view.findViewById(R.id.package_name) // 应用包名
    }

    /**
     * 当 RecyclerView 需要创建一个新的 ViewHolder 时调用。
     * @param parent ViewHolder 将被添加到的父视图组（即 RecyclerView）。
     * @param viewType 视图类型，在有多种列表项布局时使用。
     * @return 返回一个新的 ViewHolder 实例。
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 使用 LayoutInflater 从 XML 布局文件 (R.layout.item_app) 创建一个视图实例。
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        // 使用创建的视图初始化 ViewHolder。
        return ViewHolder(view)
    }

    /**
     * 当 RecyclerView 需要将数据绑定到 ViewHolder 上以显示特定位置的列表项时调用。
     * @param holder 需要绑定数据的 ViewHolder。
     * @param position 列表项在数据源中的位置（索引）。
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 从数据列表中获取对应位置的应用信息。
        val app = apps[position]
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        holder.appIcon.setImageDrawable(app.icon)
        // 为整个列表项（itemView）设置点击监听器。
        // 当用户点击时，调用我们从构造函数传入的 onItemClick 回调，并把当前应用信息传递过去。
        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    /**
     * 返回数据源中的项目总数。
     * RecyclerView 需要知道这个数量来确定要显示多少个列表项。
     */
    override fun getItemCount() = apps.size
}