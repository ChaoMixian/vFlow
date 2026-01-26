// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/module/core/CoreTouchReplayModuleUIProvider.kt

package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.TouchRecordingData
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.overlay.TouchRecordOverlay
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * CoreTouchReplayModule 的自定义 UI 提供者。
 * 提供触摸录制界面和回放速度控制。
 */
class CoreTouchReplayModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val cardRecordingStatus: MaterialCardView = view.findViewById(R.id.card_recording_status)
        val tvRecordingStatus: android.widget.TextView = view.findViewById(R.id.tv_recording_status)
        val btnRecord: MaterialButton = view.findViewById(R.id.btn_record)
        val sliderSpeed: Slider = view.findViewById(R.id.slider_speed)
        val tvSpeedValue: android.widget.TextView = view.findViewById(R.id.tv_speed_value)

        var recordingData: String = ""
        var onParametersChangedCallback: (() -> Unit)? = null
        var scope: CoroutineScope? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf("speed", "recording_data")

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_touch_replay_editor, parent, false)
        val holder = ViewHolder(view)
        holder.onParametersChangedCallback = onParametersChanged
        holder.scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // 恢复录制数据
        val recordingData = currentParameters["recording_data"] as? String ?: ""
        holder.recordingData = recordingData
        updateRecordingPreview(holder)

        // 恢复速度设置
        val speed = (currentParameters["speed"] as? Number)?.toFloat() ?: 1.0f
        holder.sliderSpeed.value = speed.coerceIn(0.5f, 2.0f)
        holder.tvSpeedValue.text = "${String.format("%.1f", speed)}x"

        // 录制按钮
        holder.btnRecord.setOnClickListener {
            startRecording(context, holder)
        }

        // 速度变化监听
        holder.sliderSpeed.addOnChangeListener { _, value, _ ->
            holder.tvSpeedValue.text = "${String.format("%.1f", value)}x"
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "recording_data" to h.recordingData,
            "speed" to h.sliderSpeed.value.toDouble()
        )
    }

    private fun startRecording(context: Context, holder: ViewHolder) {
        // 检查权限
        if (!PermissionManager.isGranted(context, PermissionManager.OVERLAY)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        val holderRef = WeakReference(holder)
        val contextRef = WeakReference(context)

        holder.scope?.launch {
            try {
                val overlay = TouchRecordOverlay(context, showHint = true)
                val result = overlay.startRecording()

                withContext(Dispatchers.Main) {
                    val h = holderRef.get()
                    if (h != null && result != null) {
                        h.recordingData = result.toJson()
                        updateRecordingPreview(h)
                        h.onParametersChangedCallback?.invoke()
                    }

                    contextRef.get()?.let {
                        val eventCount = result?.events?.size ?: 0
                        if (eventCount > 0) {
                            Toast.makeText(it, "录制完成：$eventCount 个事件", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(it, "录制已取消", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    contextRef.get()?.let {
                        Toast.makeText(it, "录制失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateRecordingPreview(holder: ViewHolder) {
        val data = TouchRecordingData.fromJson(holder.recordingData)
        if (data == null) {
            holder.tvRecordingStatus.text = "未录制\n点击下方按钮录制触摸操作"
        } else {
            holder.tvRecordingStatus.text = "已录制 ${data.events.size} 个事件\n" +
                "时长：${data.duration / 1000} 秒 | 屏幕尺寸：${data.screenW}x${data.screenH}"
        }
    }
}
