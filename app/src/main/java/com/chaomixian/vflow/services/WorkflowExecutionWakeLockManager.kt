package com.chaomixian.vflow.services

import android.content.Context
import android.os.PowerManager
import com.chaomixian.vflow.ui.common.OverlayUiPreferences
import java.util.concurrent.ConcurrentHashMap

object WorkflowExecutionWakeLockManager {
    private const val WAKE_LOCK_TAG_PREFIX = "vFlow:WorkflowExecution:"
    private val activeWakeLocks = ConcurrentHashMap<String, PowerManager.WakeLock>()

    fun acquireIfEnabled(context: Context, executionInstanceId: String) {
        if (!OverlayUiPreferences.isKeepDeviceAwakeDuringWorkflowEnabled(context)) {
            return
        }
        if (activeWakeLocks.containsKey(executionInstanceId)) {
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$WAKE_LOCK_TAG_PREFIX$executionInstanceId"
        )
        wakeLock.acquire()
        activeWakeLocks[executionInstanceId] = wakeLock
    }

    fun release(executionInstanceId: String) {
        val wakeLock = activeWakeLocks.remove(executionInstanceId) ?: return
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
