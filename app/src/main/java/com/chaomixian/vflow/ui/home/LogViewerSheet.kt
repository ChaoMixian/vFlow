// 文件: main/java/com/chaomixian/vflow/ui/home/LogViewerSheet.kt
package com.chaomixian.vflow.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.LogEntry
import com.chaomixian.vflow.core.logging.LogManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewerSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_log_viewer, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_logs)
        setupRecyclerView()
        loadLogs()
        return view
    }

    private fun setupRecyclerView() {
        // 初始为空列表
        adapter = LogAdapter(emptyList()) { logEntry ->
            // 在 Fragment 实例中调用，使用 requireContext() 传递上下文
            showLogDetailDialog(requireContext(), logEntry)
        }
        recyclerView.adapter = adapter
    }

    private fun loadLogs() {
        // 获取所有日志
        val logs = LogManager.getAllLogs()
        adapter.updateData(logs)
    }

    companion object {
        // 静态方法，接受 Context 参数，供外部（如 HomeFragment）调用
        fun showLogDetailDialog(context: Context, log: LogEntry) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_log_detail, null)

            val tvTitle = dialogView.findViewById<TextView>(R.id.tv_detail_title)
            val tvTime = dialogView.findViewById<TextView>(R.id.tv_detail_time)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tv_detail_message)
            val tvId = dialogView.findViewById<TextView>(R.id.tv_detail_id)
            val tvFullLog = dialogView.findViewById<TextView>(R.id.tv_detail_full_log)
            val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)

            tvTitle.text = "${log.workflowName} - 日志"

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            tvTime.text = "执行时间: ${dateFormat.format(Date(log.timestamp))}"

            tvMessage.text = log.message ?: "无详细信息"
            tvId.text = "工作流ID: ${log.workflowId}"

            // 使用 isNullOrEmpty() 安全检查，防止空指针异常
            tvFullLog.text = if (!log.detailedLog.isNullOrEmpty()) log.detailedLog else "(无详细执行日志)"

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create()

            btnClose.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }
}