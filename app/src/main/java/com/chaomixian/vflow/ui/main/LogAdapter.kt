// 文件: main/java/com/chaomixian/vflow/ui/main/LogAdapter.kt
package com.chaomixian.vflow.ui.main

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.LogEntry
import com.chaomixian.vflow.core.logging.LogStatus
import com.google.android.material.color.MaterialColors

class LogAdapter(
    private var logs: List<LogEntry>,
    private val onItemClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    fun updateData(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.bind(log, onItemClick)
    }

    override fun getItemCount() = logs.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.log_status_icon)
        private val nameView: TextView = itemView.findViewById(R.id.log_workflow_name)
        private val messageView: TextView = itemView.findViewById(R.id.log_message)
        private val timestampView: TextView = itemView.findViewById(R.id.log_timestamp)

        fun bind(log: LogEntry, onItemClick: (LogEntry) -> Unit) {
            val context = itemView.context

            nameView.text = log.workflowName
            messageView.text = log.message
            timestampView.text = DateUtils.getRelativeTimeSpanString(
                log.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            when (log.status) {
                LogStatus.SUCCESS -> {
                    iconView.setImageResource(R.drawable.ic_log_success)
                    iconView.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0))
                }
                LogStatus.FAILURE, LogStatus.CANCELLED -> {
                    iconView.setImageResource(R.drawable.ic_log_failure)
                    iconView.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0))
                }
            }

            itemView.setOnClickListener { onItemClick(log) }
        }
    }
}