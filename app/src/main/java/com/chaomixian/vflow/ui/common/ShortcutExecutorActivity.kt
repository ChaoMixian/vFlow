package com.chaomixian.vflow.ui.common

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager

/**
 * 一个透明的 Activity，用于接收并执行来自桌面快捷方式的请求。
 */
class ShortcutExecutorActivity : AppCompatActivity() {

    companion object {
        const val ACTION_EXECUTE_WORKFLOW = "com.chaomixian.vflow.EXECUTE_WORKFLOW_SHORTCUT"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == ACTION_EXECUTE_WORKFLOW) {
            val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
            if (workflowId != null) {
                val workflowManager = WorkflowManager(applicationContext)
                val workflow = workflowManager.getWorkflow(workflowId)
                if (workflow != null) {
                    // 显示提示并执行工作流
                    Toast.makeText(
                        applicationContext,
                        getString(com.chaomixian.vflow.R.string.shortcut_executing, workflow.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    WorkflowExecutor.execute(workflow, applicationContext)
                } else {
                    Toast.makeText(applicationContext, "错误：找不到要执行的工作流", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 无论结果如何，都立即关闭这个透明的 Activity
        finish()
    }
}