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
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((inputId: String) -> Unit)?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_wifi_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)

        // Restore state
        val triggerType = currentParameters["trigger_type"] as? String ?: "网络连接"
        if (triggerType == "网络连接") holder.connectionRb.isChecked = true else holder.stateRb.isChecked = true

        val connectionEvent = currentParameters["connection_event"] as? String ?: "连接到"
        if (connectionEvent == "连接到") holder.connectChip.isChecked = true else holder.disconnectChip.isChecked = true

        val stateEvent = currentParameters["state_event"] as? String ?: "开启时"
        if (stateEvent == "开启时") holder.stateOnChip.isChecked = true else holder.stateOffChip.isChecked = true

        val currentTarget = currentParameters["network_target"] as? String ?: WifiTriggerHandler.ANY_WIFI_TARGET
        holder.networkTextView.text = if (currentTarget == WifiTriggerHandler.ANY_WIFI_TARGET) "任意 Wi-Fi" else currentTarget

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
        val triggerType = if (h.connectionRb.isChecked) "网络连接" else "Wi-Fi状态"
        val selectedNetworkText = h.networkTextView.text.toString()

        return mapOf(
            "trigger_type" to triggerType,
            "connection_event" to if (h.connectChip.isChecked) "连接到" else "断开连接",
            "state_event" to if (h.stateOnChip.isChecked) "开启时" else "关闭时",
            "network_target" to if (selectedNetworkText == "任意 Wi-Fi") WifiTriggerHandler.ANY_WIFI_TARGET else selectedNetworkText
        )
    }

    private fun updateVisibility(holder: EditorViewHolder) {
        val isConnectionChange = holder.connectionRb.isChecked
        holder.connectionOptionsContainer.isVisible = isConnectionChange
        holder.stateOptionsContainer.isVisible = !isConnectionChange
    }

    @SuppressLint("MissingPermission")
    private fun showNetworkSelectionDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val options = mutableListOf("任意 Wi-Fi")
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                val connectionInfo = wifiManager.connectionInfo
                val ssid = connectionInfo?.ssid?.trim('"')
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    options.add(ssid)
                }
            }
        } catch (e: Exception) { /* ignore */ }
        options.add("手动输入...")

        MaterialAlertDialogBuilder(context)
            .setTitle("选择网络")
            .setItems(options.toTypedArray()) { _, which ->
                when (val selected = options[which]) {
                    "手动输入..." -> showManualSsidInputDialog(context, holder, onParametersChanged)
                    else -> {
                        holder.networkTextView.text = selected
                        onParametersChanged()
                    }
                }
            }
            .show()
    }

    private fun showManualSsidInputDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val editText = EditText(context).apply { hint = "输入Wi-Fi名称(SSID)" }
        val container = FrameLayout(context).apply {
            setPadding(48, 16, 48, 16)
            addView(editText)
        }
        // [关键] 使用 MaterialAlertDialogBuilder
        MaterialAlertDialogBuilder(context)
            .setTitle("手动输入SSID")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val ssid = editText.text.toString().trim()
                if (ssid.isNotBlank()) {
                    holder.networkTextView.text = ssid
                    onParametersChanged()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "trigger_type", "connection_event", "state_event", "network_target"
    )

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}