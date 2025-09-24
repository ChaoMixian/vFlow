// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BluetoothTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.DictionaryVariable
import com.chaomixian.vflow.core.module.TextVariable
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BluetoothTriggerHandler : ListeningTriggerHandler() {
    private var bluetoothReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "BluetoothTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.bluetooth"

    override fun startListening(context: Context) {
        if (bluetoothReceiver != null) return
        Log.d(TAG, "启动蓝牙事件监听...")

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleBluetoothEvent(context, intent)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    override fun stopListening(context: Context) {
        bluetoothReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "蓝牙事件监听已停止。")
            } finally {
                bluetoothReceiver = null
            }
        }
    }

    private fun handleBluetoothEvent(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val event = when (state) {
                    BluetoothAdapter.STATE_ON -> "开启时"
                    BluetoothAdapter.STATE_OFF -> "关闭时"
                    else -> null
                }
                if (event != null) {
                    findAndExecuteWorkflows(context, "蓝牙状态", event, null)
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    val event = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) "连接时" else "断开时"
                    findAndExecuteWorkflows(context, "设备连接", event, device)
                }
            }
        }
    }

    private fun findAndExecuteWorkflows(context: Context, triggerType: String, event: String, device: BluetoothDevice?) {
        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val configTriggerType = config["trigger_type"] as? String

                if (configTriggerType != triggerType) return@forEach

                if (triggerType == "蓝牙状态") {
                    val configEvent = config["state_event"] as? String
                    if (configEvent == event) {
                        Log.i(TAG, "触发工作流 '${workflow.name}'，事件: 蓝牙 $event")
                        WorkflowExecutor.execute(workflow, context.applicationContext)
                    }
                } else { // 设备连接
                    val configEvent = config["device_event"] as? String
                    val configAddress = config["device_address"] as? String
                    if (configEvent == event && (configAddress == "any" || configAddress == device?.address)) {
                        Log.i(TAG, "触发工作流 '${workflow.name}'，事件: $event '${device?.name}'")
                        val triggerData = DictionaryVariable(mapOf(
                            "name" to TextVariable(device?.name ?: ""),
                            "address" to TextVariable(device?.address ?: "")
                        ))
                        WorkflowExecutor.execute(workflow, context.applicationContext, triggerData)
                    }
                }
            }
        }
    }
}