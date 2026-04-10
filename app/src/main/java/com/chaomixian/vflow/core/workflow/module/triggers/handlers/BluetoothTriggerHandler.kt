// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BluetoothTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.triggers.BluetoothTriggerModule
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BluetoothTriggerHandler : ListeningTriggerHandler() {
    private var bluetoothReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "BluetoothTriggerHandler"
    }

    override fun startListening(context: Context) {
        if (bluetoothReceiver != null) return
        DebugLogger.d(TAG, "启动蓝牙事件监听...")

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
                DebugLogger.d(TAG, "蓝牙事件监听已停止。")
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
                    BluetoothAdapter.STATE_ON -> BluetoothTriggerModule.STATE_EVENT_ON
                    BluetoothAdapter.STATE_OFF -> BluetoothTriggerModule.STATE_EVENT_OFF
                    else -> null
                }
                if (event != null) {
                    findAndExecuteWorkflows(context, BluetoothTriggerModule.TRIGGER_TYPE_STATE, event, null)
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    val event = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                        BluetoothTriggerModule.DEVICE_EVENT_CONNECTED
                    } else {
                        BluetoothTriggerModule.DEVICE_EVENT_DISCONNECTED
                    }
                    findAndExecuteWorkflows(context, BluetoothTriggerModule.TRIGGER_TYPE_DEVICE, event, device)
                }
            }
        }
    }

    private fun findAndExecuteWorkflows(context: Context, triggerType: String, event: String, device: BluetoothDevice?) {
        triggerScope.launch {
            val inputsById = BluetoothTriggerModule().getInputs().associateBy { it.id }
            listeningTriggers.forEach { trigger ->
                val config = trigger.parameters
                val rawTriggerType = config["trigger_type"] as? String ?: BluetoothTriggerModule.TRIGGER_TYPE_STATE
                val configTriggerType = inputsById["trigger_type"]?.normalizeEnumValue(rawTriggerType) ?: rawTriggerType

                if (configTriggerType != triggerType) return@forEach

                if (triggerType == BluetoothTriggerModule.TRIGGER_TYPE_STATE) {
                    val rawEvent = config["state_event"] as? String ?: BluetoothTriggerModule.STATE_EVENT_ON
                    val configEvent = inputsById["state_event"]?.normalizeEnumValue(rawEvent) ?: rawEvent
                    if (configEvent == event) {
                        DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}'，事件: 蓝牙 $event")
                        executeTrigger(context, trigger)
                    }
                } else { // 设备连接
                    val rawEvent = config["device_event"] as? String ?: BluetoothTriggerModule.DEVICE_EVENT_CONNECTED
                    val configEvent = inputsById["device_event"]?.normalizeEnumValue(rawEvent) ?: rawEvent
                    val configAddress = config["device_address"] as? String
                    if (configEvent == event && (configAddress == BluetoothTriggerModule.ANY_DEVICE_ADDRESS || configAddress == device?.address)) {
                        DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}'，事件: $event '${device?.name}'")
                        val triggerData = VDictionary(mapOf(
                            "name" to VString(device?.name ?: ""),
                            "address" to VString(device?.address ?: "")
                        ))
                        executeTrigger(context, trigger, triggerData)
                    }
                }
            }
        }
    }
}
