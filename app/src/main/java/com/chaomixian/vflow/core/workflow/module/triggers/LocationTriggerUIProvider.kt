// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerUIProvider.kt
// 描述: 位置触发器 UI 提供者，使用原生位置选择
package com.chaomixian.vflow.core.workflow.module.triggers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LocationTriggerUIProvider : ModuleUIProvider {

    private class EditorViewHolder(view: View) : CustomEditorViewHolder(view) {
        val chipGroup: ChipGroup = view.findViewById(R.id.chip_group_event)
        val chipEnter: Chip = view.findViewById(R.id.chip_enter)
        val chipExit: Chip = view.findViewById(R.id.chip_exit)

        val latitudeInput: EditText = view.findViewById(R.id.input_latitude)
        val longitudeInput: EditText = view.findViewById(R.id.input_longitude)
        val radiusSlider: Slider = view.findViewById(R.id.slider_radius)
        val radiusText: TextView = view.findViewById(R.id.text_radius)
        val locationNameInput: EditText = view.findViewById(R.id.input_location_name)
        val getCurrentLocationButton: Button = view.findViewById(R.id.button_get_current_location)
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("event", "latitude", "longitude", "radius", "location_name")
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_location_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)

        // 恢复已有参数
        val event = currentParameters["event"] as? String ?: "进入时"
        val latitude = currentParameters["latitude"] as? Double ?: 39.9042
        val longitude = currentParameters["longitude"] as? Double ?: 116.4074
        val radius = currentParameters["radius"] as? Double ?: 500.0
        val locationName = currentParameters["location_name"] as? String ?: ""

        // 设置事件选择
        if (event == "进入时") {
            holder.chipEnter.isChecked = true
        } else {
            holder.chipExit.isChecked = true
        }

        // 设置坐标
        holder.latitudeInput.setText(latitude.toString())
        holder.longitudeInput.setText(longitude.toString())

        // 设置半径
        holder.radiusSlider.value = radius.toFloat()
        holder.radiusText.text = "${radius.toInt()} 米"

        // 设置位置名称
        holder.locationNameInput.setText(locationName)

        // 监听事件变化
        holder.chipGroup.setOnCheckedChangeListener { _, _ ->
            onParametersChanged()
        }

        // 监听半径变化
        holder.radiusSlider.addOnChangeListener { _, value, _ ->
            holder.radiusText.text = "${value.toInt()} 米"
            onParametersChanged()
        }

        // 监听文本输入变化
        holder.latitudeInput.setOnTextChangedListener {
            onParametersChanged()
        }
        holder.longitudeInput.setOnTextChangedListener {
            onParametersChanged()
        }
        holder.locationNameInput.setOnTextChangedListener {
            onParametersChanged()
        }

        // 获取当前位置按钮
        holder.getCurrentLocationButton.setOnClickListener {
            showLocationOptionsDialog(context, holder, onParametersChanged)
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val editorHolder = holder as EditorViewHolder

        val event = if (editorHolder.chipEnter.isChecked) "进入时" else "离开时"
        val latitude = editorHolder.latitudeInput.text.toString().toDoubleOrNull() ?: 39.9042
        val longitude = editorHolder.longitudeInput.text.toString().toDoubleOrNull() ?: 116.4074
        val radius = editorHolder.radiusSlider.value.toDouble()
        val locationName = editorHolder.locationNameInput.text.toString()

        return mapOf(
            "event" to event,
            "latitude" to latitude,
            "longitude" to longitude,
            "radius" to radius,
            "location_name" to locationName
        )
    }

    private fun showLocationOptionsDialog(context: Context, holder: EditorViewHolder, onParametersChanged: () -> Unit) {
        val options = mutableListOf("使用当前位置")

        // 检查是否可以使用GPSProvider
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasGPS = locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)

        if (hasGPS) {
            options.add("使用 GPS 精确定位")
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("获取位置")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> getCurrentLocation(context, holder, false, onParametersChanged)
                    1 -> getCurrentLocation(context, holder, true, onParametersChanged)
                }
            }
            .show()
    }

    private fun getCurrentLocation(context: Context, holder: EditorViewHolder, useGPS: Boolean, onParametersChanged: () -> Unit) {
        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder(context)
                .setTitle("需要位置权限")
                .setMessage("请先授予应用位置权限，然后重试。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 优先使用 GPS，其次使用网络定位
            val provider = if (useGPS && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                MaterialAlertDialogBuilder(context)
                    .setTitle("无法获取位置")
                    .setMessage("请确保已开启位置服务（GPS 或网络定位）")
                    .setPositiveButton("确定", null)
                    .show()
                return
            }

            val location = locationManager.getLastKnownLocation(provider)
            if (location != null) {
                holder.latitudeInput.setText(String.format("%.6f", location.latitude))
                holder.longitudeInput.setText(String.format("%.6f", location.longitude))
                onParametersChanged()

                MaterialAlertDialogBuilder(context)
                    .setTitle("位置获取成功")
                    .setMessage("已获取当前位置：\n纬度: ${String.format("%.6f", location.latitude)}\n经度: ${String.format("%.6f", location.longitude)}")
                    .setPositiveButton("确定", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(context)
                    .setTitle("无法获取位置")
                    .setMessage("请稍后重试，或确保已开启定位服务")
                    .setPositiveButton("确定", null)
                    .show()
            }
        } catch (e: SecurityException) {
            MaterialAlertDialogBuilder(context)
                .setTitle("权限错误")
                .setMessage("缺少位置权限")
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(context)
                .setTitle("错误")
                .setMessage("获取位置失败: ${e.message}")
                .setPositiveButton("确定", null)
                .show()
        }
    }
}

// 扩展函数：为 EditText 添加文本变化监听
private fun EditText.setOnTextChangedListener(callback: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            callback()
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
