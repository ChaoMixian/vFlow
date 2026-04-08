package com.chaomixian.vflow.core.logging

import com.chaomixian.vflow.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LogMessageFormatterTest {

    @Test
    fun `resolves structured failure message from key and args`() {
        val entry = LogEntry(
            workflowId = "wf-1",
            workflowName = "Workflow",
            timestamp = 0L,
            status = LogStatus.FAILURE,
            messageKey = LogMessageKey.EXECUTION_FAILED_AT_STEP,
            messageArgs = listOf("3", "Tap")
        )

        val message = LogMessageFormatter.resolve(entry, ::resolveString)

        assertEquals("Failed at step #3 (Tap)", message)
    }

    @Test
    fun `localizes legacy chinese failure message`() {
        val entry = LogEntry(
            workflowId = "wf-1",
            workflowName = "Workflow",
            timestamp = 0L,
            status = LogStatus.FAILURE,
            message = "在步骤 #2 (点击) 执行失败"
        )

        val message = LogMessageFormatter.resolve(entry, ::resolveString)

        assertEquals("Failed at step #2 (点击)", message)
    }

    @Test
    fun `falls back to original message when pattern is unknown`() {
        val entry = LogEntry(
            workflowId = "wf-1",
            workflowName = "Workflow",
            timestamp = 0L,
            status = LogStatus.SUCCESS,
            message = "Custom message"
        )

        val message = LogMessageFormatter.resolve(entry, ::resolveString)

        assertEquals("Custom message", message)
    }

    private fun resolveString(resId: Int, formatArgs: Array<out Any>): String {
        return when (resId) {
            R.string.log_message_execution_completed -> "Execution completed"
            R.string.log_message_execution_cancelled -> "Execution stopped"
            R.string.log_message_execution_failed_at_step -> "Failed at step #${formatArgs[0]} (${formatArgs[1]})"
            R.string.ui_inspector_unknown -> "Unknown"
            else -> error("Unexpected resource id: $resId")
        }
    }
}
