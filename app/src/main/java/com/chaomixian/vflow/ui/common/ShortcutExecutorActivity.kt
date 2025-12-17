package com.chaomixian.vflow.ui.common

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager

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
                    // ID 存在但找不到对应工作流（可能已被删除，或者外部传入了错误的ID）
                    Toast.makeText(applicationContext, "错误：找不到指定ID的工作流\nID: $workflowId", Toast.LENGTH_LONG).show()
                }
            } else {
                // Intent 中没有 workflow_id 参数
                Toast.makeText(applicationContext, "错误：调用参数缺失 (workflow_id)", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}