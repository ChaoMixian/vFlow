// 文件: main/java/com/chaomixian/vflow/ui/app_picker/ActivityListAdapter.kt
// 描述: Activity选择界面的 RecyclerView 适配器。
package com.chaomixian.vflow.ui.app_picker

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R

class ActivityListAdapter(
    private val activities: List<ActivityInfo>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ActivityListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val activityName: TextView = view.findViewById(R.id.activity_name)
        val activityLabel: TextView = view.findViewById(R.id.activity_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 第一个位置为 "启动应用"
        if (position == 0) {
            holder.activityName.text = "启动应用"
            holder.activityLabel.visibility = View.GONE
            // [修改] 为整个列表项设置点击事件
            holder.itemView.setOnClickListener { onItemClick("LAUNCH") }
            return
        }

        holder.activityLabel.visibility = View.VISIBLE
        // 减去 "启动应用" 占用的位置
        val activity = activities[position - 1]
        holder.activityName.text = activity.name
        holder.activityLabel.text = activity.loadLabel(holder.itemView.context.packageManager).toString()

        // [修改] 为整个列表项设置点击事件
        holder.itemView.setOnClickListener { onItemClick(activity.name) }
    }

    // 列表数量 = activities 数量 + 1个 "启动应用"
    override fun getItemCount() = activities.size + 1
}