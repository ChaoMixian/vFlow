package com.chaomixian.vflow.ui.workflow_list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.model.Workflow

class WorkflowListAdapter(
    private var workflows: List<Workflow>, // 改为 var 以便更新
    private val onEdit: (Workflow) -> Unit,
    private val onDelete: (Workflow) -> Unit,
    private val onDuplicate: (Workflow) -> Unit,
    private val onExport: (Workflow) -> Unit,
    private val onExecute: (Workflow) -> Unit // 新增：执行按钮回调
) : RecyclerView.Adapter<WorkflowListAdapter.WorkflowViewHolder>() {

    // 新增：用于在删除或复制后刷新列表
    fun updateData(newWorkflows: List<Workflow>) {
        workflows = newWorkflows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow, parent, false)
        return WorkflowViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        val workflow = workflows[position]
        holder.bind(workflow)

        // 编辑操作绑定在信息区域
        holder.infoContainer.setOnClickListener { onEdit(workflow) }

        // “更多”按钮弹出菜单
        holder.moreOptionsButton.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.workflow_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_delete -> {
                        onDelete(workflow)
                        true
                    }
                    R.id.menu_duplicate -> {
                        onDuplicate(workflow)
                        true
                    }
                    R.id.menu_export_single -> {
                        onExport(workflow)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // 绑定执行按钮
        holder.executeButton.setOnClickListener { onExecute(workflow) }
    }

    override fun getItemCount() = workflows.size

    class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
        val description: TextView = itemView.findViewById(R.id.text_view_workflow_description)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val executeButton: ImageButton = itemView.findViewById(R.id.button_execute_workflow) // 新增
        val infoContainer: LinearLayout = itemView.findViewById(R.id.workflow_info_container)

        fun bind(workflow: Workflow) {
            name.text = workflow.name
            val stepCount = workflow.steps.size - 1 // 减去触发器
            description.text = "包含 ${stepCount.coerceAtLeast(0)} 个步骤"
        }
    }
}