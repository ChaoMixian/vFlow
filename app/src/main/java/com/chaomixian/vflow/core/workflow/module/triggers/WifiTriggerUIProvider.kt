// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/WifiTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.WifiTriggerReceiver
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class WifiTriggerUIProvider : ModuleUIProvider {

    private class EditorViewHolder(view: View) : CustomEditorViewHolder(view) {
        val eventChipGroup: ChipGroup = view.findViewById(R.id.chip_group_wifi_event)
        val connectChip: Chip = view.findViewById(R.id.chip_connect)
        val disconnectChip: Chip = view.findViewById(R.id.chip_disconnect)
        val networkTextView: TextView = view.findViewById(R.id.text_selected_wifi_network)
        val selectNetworkButton: Button = view.findViewById(R.id.button_select_wifi_network)
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_wifi_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)

        // 恢复事件选择
        val currentEvent = currentParameters["event"] as? String ?: "连接到"
        if (currentEvent == "连接到") {
            holder.connectChip.isChecked = true
        } else {
            holder.disconnectChip.isChecked = true
        }

        // 恢复网络选择
        val currentTarget = currentParameters["network_target"] as? String ?: WifiTriggerReceiver.ANY_WIFI_TARGET
        holder.networkTextView.text = if (currentTarget == WifiTriggerReceiver.ANY_WIFI_TARGET) "任意 Wi-Fi" else currentTarget

        // 设置监听器
        holder.eventChipGroup.setOnCheckedStateChangeListener { _, _ -> onParametersChanged() }
        val clickListener = View.OnClickListener { showNetworkSelectionDialog(context, holder, onParametersChanged) }
        holder.selectNetworkButton.setOnClickListener(clickListener)
        holder.networkTextView.setOnClickListener(clickListener) // 让文本本身也可以点击

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as EditorViewHolder
        val event = if (h.connectChip.isChecked) "连接到" else "断开连接"
        val selectedNetworkText = h.networkTextView.text.toString()
        val networkTarget = if (selectedNetworkText == "任意 Wi-Fi") WifiTriggerReceiver.ANY_WIFI_TARGET else selectedNetworkText

        return mapOf("event" to event, "network_target" to networkTarget)
    }

    /**
     * 显示网络选择对话框。
     */
    @SuppressLint("MissingPermission")
    private fun showNetworkSelectionDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val options = mutableListOf("任意 Wi-Fi")

        // 尝试获取当前连接的Wi-Fi SSID
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                val connectionInfo = wifiManager.connectionInfo
                val ssid = connectionInfo?.ssid?.trim('"')
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    options.add(ssid)
                }
            }
        } catch (e: Exception) {
            // 忽略权限等错误
        }

        options.add("手动输入...")

        AlertDialog.Builder(context)
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

    /**
     * 显示手动输入SSID的对话框。
     */
    private fun showManualSsidInputDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val editText = EditText(context).apply {
            hint = "输入Wi-Fi名称(SSID)"
        }
        val container = FrameLayout(context).apply {
            setPadding(48, 16, 48, 16)
            addView(editText)
        }

        AlertDialog.Builder(context)
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


    override fun getHandledInputIds(): Set<String> {
        return setOf("event", "network_target")
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return null
    }
}