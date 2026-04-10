// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/WifiTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.WifiTriggerHandler
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WifiTriggerUIProvider : ModuleUIProvider {
    companion object {
        private const val MANUAL_INPUT_SENTINEL = "__manual_input__"
    }

    private class EditorViewHolder(view: View) : CustomEditorViewHolder(view) {
        val triggerTypeRg: RadioGroup = view.findViewById(R.id.rg_trigger_type)
        val connectionRb: RadioButton = view.findViewById(R.id.rb_connection_change)
        val stateRb: RadioButton = view.findViewById(R.id.rb_state_change)

        val connectionOptionsContainer: View = view.findViewById(R.id.container_connection_options)
        val connectionEventCg: ChipGroup = view.findViewById(R.id.cg_connection_event)
        val connectChip: Chip = view.findViewById(R.id.chip_connect)
        val disconnectChip: Chip = view.findViewById(R.id.chip_disconnect)
        val networkTextView: TextView = view.findViewById(R.id.text_selected_wifi_network)
        val selectNetworkButton: Button = view.findViewById(R.id.button_select_wifi_network)

        val stateOptionsContainer: View = view.findViewById(R.id.container_state_options)
        val stateEventCg: ChipGroup = view.findViewById(R.id.cg_state_event)
        val stateOnChip: Chip = view.findViewById(R.id.chip_state_on)
        val stateOffChip: Chip = view.findViewById(R.id.chip_state_off)
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_wifi_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)
        val inputsById = WifiTriggerModule().getInputs().associateBy { it.id }

        // Restore state
        val rawTriggerType = currentParameters["trigger_type"] as? String ?: WifiTriggerModule.TRIGGER_TYPE_CONNECTION
        val triggerType = inputsById["trigger_type"]?.normalizeEnumValue(rawTriggerType) ?: rawTriggerType
        if (triggerType == WifiTriggerModule.TRIGGER_TYPE_CONNECTION) holder.connectionRb.isChecked = true else holder.stateRb.isChecked = true

        val rawConnectionEvent = currentParameters["connection_event"] as? String ?: WifiTriggerModule.CONNECTION_EVENT_CONNECTED
        val connectionEvent = inputsById["connection_event"]?.normalizeEnumValue(rawConnectionEvent) ?: rawConnectionEvent
        if (connectionEvent == WifiTriggerModule.CONNECTION_EVENT_CONNECTED) holder.connectChip.isChecked = true else holder.disconnectChip.isChecked = true

        val rawStateEvent = currentParameters["state_event"] as? String ?: WifiTriggerModule.STATE_EVENT_ON
        val stateEvent = inputsById["state_event"]?.normalizeEnumValue(rawStateEvent) ?: rawStateEvent
        if (stateEvent == WifiTriggerModule.STATE_EVENT_ON) holder.stateOnChip.isChecked = true else holder.stateOffChip.isChecked = true

        val currentTarget = currentParameters["network_target"] as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET
        updateSelectedNetworkText(context, holder, currentTarget)

        updateVisibility(holder)

        // Listeners
        holder.triggerTypeRg.setOnCheckedChangeListener { _, _ ->
            updateVisibility(holder)
            onParametersChanged()
        }
        holder.connectionEventCg.setOnCheckedChangeListener { _, _ -> onParametersChanged() }
        holder.stateEventCg.setOnCheckedChangeListener { _, _ -> onParametersChanged() }
        val networkClickListener = View.OnClickListener { showNetworkSelectionDialog(context, holder, onParametersChanged) }
        holder.selectNetworkButton.setOnClickListener(networkClickListener)
        holder.networkTextView.setOnClickListener(networkClickListener)

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as EditorViewHolder
        val triggerType = if (h.connectionRb.isChecked) WifiTriggerModule.TRIGGER_TYPE_CONNECTION else WifiTriggerModule.TRIGGER_TYPE_STATE
        val selectedNetworkTarget = h.networkTextView.tag as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET

        return mapOf(
            "trigger_type" to triggerType,
            "connection_event" to if (h.connectChip.isChecked) WifiTriggerModule.CONNECTION_EVENT_CONNECTED else WifiTriggerModule.CONNECTION_EVENT_DISCONNECTED,
            "state_event" to if (h.stateOnChip.isChecked) WifiTriggerModule.STATE_EVENT_ON else WifiTriggerModule.STATE_EVENT_OFF,
            "network_target" to selectedNetworkTarget
        )
    }

    private fun updateVisibility(holder: EditorViewHolder) {
        val isConnectionChange = holder.connectionRb.isChecked
        holder.connectionOptionsContainer.isVisible = isConnectionChange
        holder.stateOptionsContainer.isVisible = !isConnectionChange
    }

    @SuppressLint("MissingPermission")
    private fun showNetworkSelectionDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val options = mutableListOf(
            context.getString(R.string.summary_vflow_trigger_wifi_any_wifi) to WifiTriggerHandler.ANY_WIFI_TARGET
        )
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                val connectionInfo = wifiManager.connectionInfo
                val ssid = connectionInfo?.ssid?.trim('"')
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    options.add(ssid to ssid)
                }
            }
        } catch (e: Exception) { /* ignore */ }
        options.add(context.getString(R.string.option_wifi_trigger_manual_input) to MANUAL_INPUT_SENTINEL)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.button_select_network)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                val selected = options[which]
                if (selected.second == MANUAL_INPUT_SENTINEL) {
                    showManualSsidInputDialog(context, holder, onParametersChanged)
                } else {
                    updateSelectedNetworkText(context, holder, selected.second)
                    onParametersChanged()
                }
            }
            .show()
    }

    private fun showManualSsidInputDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val editText = EditText(context).apply {
            hint = context.getString(R.string.hint_wifi_trigger_manual_ssid)
        }
        val container = FrameLayout(context).apply {
            setPadding(48, 16, 48, 16)
            addView(editText)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_wifi_trigger_manual_ssid_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ssid = editText.text.toString().trim()
                if (ssid.isNotBlank()) {
                    updateSelectedNetworkText(context, holder, ssid)
                    onParametersChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateSelectedNetworkText(context: Context, holder: EditorViewHolder, target: String) {
        holder.networkTextView.tag = target
        holder.networkTextView.text = if (target == WifiTriggerHandler.ANY_WIFI_TARGET) {
            context.getString(R.string.summary_vflow_trigger_wifi_any_wifi)
        } else {
            target
        }
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "trigger_type", "connection_event", "state_event", "network_target"
    )

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
