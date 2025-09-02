package com.chaomixian.vflow.ui.workflow_list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.model.Workflow

class WorkflowListAdapter(
    private var workflows: List<Workflow>,
    private val onEdit: (Workflow) -> Unit,
    private val onDelete: (Workflow) -> Unit,
    private val onDuplicate: (Workflow) -> Unit,
    private val onExport: (Workflow) -> Unit,
    private val onExecute: (Workflow) -> Unit
) : RecyclerView.Adapter<WorkflowListAdapter.WorkflowViewHolder>() {

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

        // 核心修改：将点击事件绑定到整个卡片包装器上
        // 由于按钮是 clickable 的子视图，它们的点击事件会被优先消费，不会触发这里的 onEdit
        holder.clickableWrapper.setOnClickListener { onEdit(workflow) }

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

        holder.executeButton.setOnClickListener { onExecute(workflow) }
    }

    override fun getItemCount() = workflows.size

    class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
        val description: TextView = itemView.findViewById(R.id.text_view_workflow_description)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val executeButton: ImageButton = itemView.findViewById(R.id.button_execute_workflow)
        // 核心修改：获取新的可点击视图的引用
        val clickableWrapper: RelativeLayout = itemView.findViewById(R.id.clickable_wrapper)

        fun bind(workflow: Workflow) {
            name.text = workflow.name
            val stepCount = workflow.steps.size - 1
            description.text = "包含 ${stepCount.coerceAtLeast(0)} 个步骤"
        }
    }
}