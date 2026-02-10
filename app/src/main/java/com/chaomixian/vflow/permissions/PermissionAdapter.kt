package com.chaomixian.vflow.permissions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.google.android.material.color.MaterialColors

// 文件：PermissionAdapter.kt
// 描述：用于在权限请求页面显示权限列表的 RecyclerView 适配器。

/**
 * 权限列表的 RecyclerView.Adapter。
 * @param permissions 要显示的权限列表。
 * @param onGrantClick 当用户点击"授权"按钮时的回调。
 */
class PermissionAdapter(
    private val permissions: List<Permission>,
    private val onGrantClick: (Permission) -> Unit
) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

    /** ViewHolder 定义，缓存视图引用。 */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_permission_name)
        val status: TextView = view.findViewById(R.id.text_permission_status)
        val description: TextView = view.findViewById(R.id.text_permission_description)
        val grantButton: Button = view.findViewById(R.id.button_grant_permission)
    }

    /** 创建 ViewHolder 实例。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permission, parent, false)
        return ViewHolder(view)
    }

    /** 将数据绑定到 ViewHolder。 */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val permission = permissions[position]
        val context = holder.itemView.context

        holder.name.text = permission.getLocalizedName(context)
        holder.description.text = permission.getLocalizedDescription(context)

        val isGranted = PermissionManager.isGranted(context, permission)

        if (isGranted) {
            holder.status.text = context.getString(R.string.permission_status_granted)
            holder.status.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0))
            holder.grantButton.text = context.getString(R.string.permission_button_granted)
            holder.grantButton.alpha = 0.7f
            holder.grantButton.setOnClickListener { onGrantClick(permission) }
        } else {
            holder.status.text = context.getString(R.string.permission_status_denied)
            holder.status.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0))
            holder.grantButton.text = context.getString(R.string.permission_button_grant)
            holder.grantButton.alpha = 1.0f
            holder.grantButton.setOnClickListener { onGrantClick(permission) }
        }
    }

    /** 返回数据项总数。 */
    override fun getItemCount() = permissions.size
}