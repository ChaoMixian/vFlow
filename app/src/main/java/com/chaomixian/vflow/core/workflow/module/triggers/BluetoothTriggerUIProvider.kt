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

        var selectedDeviceAddress: String? = BluetoothTriggerModule.ANY_DEVICE_ADDRESS
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "trigger_type", "state_event", "device_event", "device_address", "device_name"
    )

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_bluetooth_trigger_editor, parent, false)
        val holder = ViewHolder(view)
        val inputsById = BluetoothTriggerModule().getInputs().associateBy { it.id }

        // Restore state
        val rawTriggerType = currentParameters["trigger_type"] as? String ?: BluetoothTriggerModule.TRIGGER_TYPE_STATE
        val triggerType = inputsById["trigger_type"]?.normalizeEnumValue(rawTriggerType) ?: rawTriggerType
        if (triggerType == BluetoothTriggerModule.TRIGGER_TYPE_STATE) {
            holder.stateChangeRb.isChecked = true
        } else {
            holder.deviceConnectionRb.isChecked = true
        }

        val rawStateEvent = currentParameters["state_event"] as? String ?: BluetoothTriggerModule.STATE_EVENT_ON
        val stateEvent = inputsById["state_event"]?.normalizeEnumValue(rawStateEvent) ?: rawStateEvent
        if (stateEvent == BluetoothTriggerModule.STATE_EVENT_ON) holder.stateOnChip.isChecked = true else holder.stateOffChip.isChecked = true

        val rawDeviceEvent = currentParameters["device_event"] as? String ?: BluetoothTriggerModule.DEVICE_EVENT_CONNECTED
        val deviceEvent = inputsById["device_event"]?.normalizeEnumValue(rawDeviceEvent) ?: rawDeviceEvent
        if (deviceEvent == BluetoothTriggerModule.DEVICE_EVENT_CONNECTED) holder.deviceConnectChip.isChecked = true else holder.deviceDisconnectChip.isChecked = true

        val deviceAddress = currentParameters["device_address"] as? String ?: BluetoothTriggerModule.ANY_DEVICE_ADDRESS
        val deviceName = currentParameters["device_name"] as? String
        holder.selectedDeviceAddress = deviceAddress
        holder.selectedDeviceTv.text = if (deviceAddress == BluetoothTriggerModule.ANY_DEVICE_ADDRESS || (deviceAddress.isNullOrBlank() && deviceName.isNullOrBlank())) {
            context.getString(R.string.summary_vflow_trigger_bluetooth_any_device)
        } else {
            deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress
        }

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
        val triggerType = if (h.stateChangeRb.isChecked) {
            BluetoothTriggerModule.TRIGGER_TYPE_STATE
        } else {
            BluetoothTriggerModule.TRIGGER_TYPE_DEVICE
        }

        return mapOf(
            "trigger_type" to triggerType,
            "state_event" to if (h.stateOnChip.isChecked) BluetoothTriggerModule.STATE_EVENT_ON else BluetoothTriggerModule.STATE_EVENT_OFF,
            "device_event" to if (h.deviceConnectChip.isChecked) BluetoothTriggerModule.DEVICE_EVENT_CONNECTED else BluetoothTriggerModule.DEVICE_EVENT_DISCONNECTED,
            "device_address" to h.selectedDeviceAddress,
            "device_name" to if (h.selectedDeviceAddress == BluetoothTriggerModule.ANY_DEVICE_ADDRESS) "" else h.selectedDeviceTv.text.toString()
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
            Toast.makeText(context, R.string.toast_bluetooth_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(context, R.string.toast_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList = mutableListOf<Pair<String, String>>()
        deviceList.add(
            context.getString(R.string.summary_vflow_trigger_bluetooth_any_device) to BluetoothTriggerModule.ANY_DEVICE_ADDRESS
        )
        pairedDevices.forEach { device ->
            deviceList.add((device.name ?: device.address) to device.address)
        }

        val deviceNames = deviceList.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_bluetooth_select_device_title)
            .setItems(deviceNames) { _, which ->
                val selected = deviceList[which]
                holder.selectedDeviceTv.text = selected.first
                holder.selectedDeviceAddress = selected.second
                onParametersChanged()
            }
            .show()
    }

    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>, onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?): View? = null
}
