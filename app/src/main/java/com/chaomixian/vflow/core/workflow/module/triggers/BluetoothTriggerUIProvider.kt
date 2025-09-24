// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/BluetoothTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@SuppressLint("MissingPermission")
class BluetoothTriggerUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val triggerTypeRg: RadioGroup = view.findViewById(R.id.rg_trigger_type)
        val stateChangeRb: RadioButton = view.findViewById(R.id.rb_state_change)
        val deviceConnectionRb: RadioButton = view.findViewById(R.id.rb_device_connection)

        val stateOptionsContainer: View = view.findViewById(R.id.container_state_options)
        val stateEventCg: ChipGroup = view.findViewById(R.id.cg_state_event)
        val stateOnChip: Chip = view.findViewById(R.id.chip_state_on)
        val stateOffChip: Chip = view.findViewById(R.id.chip_state_off)

        val deviceOptionsContainer: View = view.findViewById(R.id.container_device_options)
        val deviceEventCg: ChipGroup = view.findViewById(R.id.cg_device_event)
        val deviceConnectChip: Chip = view.findViewById(R.id.chip_device_connect)
        val deviceDisconnectChip: Chip = view.findViewById(R.id.chip_device_disconnect)
        val selectedDeviceTv: TextView = view.findViewById(R.id.tv_selected_device)
        val selectDeviceBtn: Button = view.findViewById(R.id.btn_select_device)

        var selectedDeviceAddress: String? = "any"
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "trigger_type", "state_event", "device_event", "device_address", "device_name"
    )

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((inputId: String) -> Unit)?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_bluetooth_trigger_editor, parent, false)
        val holder = ViewHolder(view)

        // Restore state
        val triggerType = currentParameters["trigger_type"] as? String ?: "蓝牙状态"
        if (triggerType == "蓝牙状态") {
            holder.stateChangeRb.isChecked = true
        } else {
            holder.deviceConnectionRb.isChecked = true
        }

        val stateEvent = currentParameters["state_event"] as? String ?: "开启时"
        if (stateEvent == "开启时") holder.stateOnChip.isChecked = true else holder.stateOffChip.isChecked = true

        val deviceEvent = currentParameters["device_event"] as? String ?: "连接时"
        if (deviceEvent == "连接时") holder.deviceConnectChip.isChecked = true else holder.deviceDisconnectChip.isChecked = true

        holder.selectedDeviceAddress = currentParameters["device_address"] as? String ?: "any"
        holder.selectedDeviceTv.text = currentParameters["device_name"] as? String ?: "任何设备"

        updateVisibility(holder)

        // Listeners
        holder.triggerTypeRg.setOnCheckedChangeListener { _, _ ->
            updateVisibility(holder)
            onParametersChanged()
        }
        holder.stateEventCg.setOnCheckedStateChangeListener { _, _ -> onParametersChanged() }
        holder.deviceEventCg.setOnCheckedStateChangeListener { _, _ -> onParametersChanged() }

        holder.selectDeviceBtn.setOnClickListener {
            showDevicePicker(context, holder, onParametersChanged)
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val triggerType = if (h.stateChangeRb.isChecked) "蓝牙状态" else "设备连接"

        return mapOf(
            "trigger_type" to triggerType,
            "state_event" to if(h.stateOnChip.isChecked) "开启时" else "关闭时",
            "device_event" to if(h.deviceConnectChip.isChecked) "连接时" else "断开时",
            "device_address" to h.selectedDeviceAddress,
            "device_name" to h.selectedDeviceTv.text.toString()
        )
    }

    private fun updateVisibility(holder: ViewHolder) {
        val isStateChange = holder.stateChangeRb.isChecked
        holder.stateOptionsContainer.isVisible = isStateChange
        holder.deviceOptionsContainer.isVisible = !isStateChange
    }

    private fun showDevicePicker(context: Context, holder: ViewHolder, onParametersChanged: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "需要蓝牙权限才能获取设备列表", Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(context, "该设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList = mutableListOf<Pair<String, String>>()
        deviceList.add("任何设备" to "any")
        pairedDevices.forEach { device ->
            deviceList.add(device.name to device.address)
        }

        val deviceNames = deviceList.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("选择一个蓝牙设备")
            .setItems(deviceNames) { _, which ->
                val selected = deviceList[which]
                holder.selectedDeviceTv.text = selected.first
                holder.selectedDeviceAddress = selected.second
                onParametersChanged()
            }
            .show()
    }

    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep, onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?): View? = null
}