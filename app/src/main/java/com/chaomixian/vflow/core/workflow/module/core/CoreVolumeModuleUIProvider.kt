// 文件: main/java/com/chaomixian/vflow/core/workflow/module/core/CoreVolumeModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音量控制模块的自定义 UI 提供者。
 * 提供调音台式的界面，使用 Material 3 Segmented Buttons 支持同时控制多个音频流。
 */
class CoreVolumeModuleUIProvider : ModuleUIProvider {

    // 音频流的最大音量（实际值）
    private val streamMaxVolumes = mapOf(
        "music" to 160,
        "notification" to 16,
        "ring" to 16,
        "system" to 16,
        "alarm" to 16
    )

    /**
     * 将实际音量值转换为百分比（0-100）
     */
    private fun actualVolumeToPercent(stream: String, actualVolume: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0
    }

    data class StreamConfig(
        val streamType: Int,
        val streamName: String,
        val btnKeep: MaterialButton,
        val btnSet: MaterialButton,
        val btnMute: MaterialButton,
        val btnUnmute: MaterialButton,
        val sliderContainer: View,
        val slider: Slider,
        val valueText: TextView,
        val currentText: TextView
    )

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val musicConfig: StreamConfig
        val notificationConfig: StreamConfig
        val ringConfig: StreamConfig
        val systemConfig: StreamConfig
        val alarmConfig: StreamConfig
        val refreshButton: Button

        init {
            musicConfig = StreamConfig(
                streamType = 3, streamName = "music",
                btnKeep = view.findViewById(R.id.btn_music_keep),
                btnSet = view.findViewById(R.id.btn_music_set),
                btnMute = view.findViewById(R.id.btn_music_mute),
                btnUnmute = view.findViewById(R.id.btn_music_unmute),
                sliderContainer = view.findViewById(R.id.container_music_slider),
                slider = view.findViewById(R.id.slider_music),
                valueText = view.findViewById(R.id.tv_music_value),
                currentText = view.findViewById(R.id.tv_music_current)
            )
            notificationConfig = StreamConfig(
                streamType = 5, streamName = "notification",
                btnKeep = view.findViewById(R.id.btn_notification_keep),
                btnSet = view.findViewById(R.id.btn_notification_set),
                btnMute = view.findViewById(R.id.btn_notification_mute),
                btnUnmute = view.findViewById(R.id.btn_notification_unmute),
                sliderContainer = view.findViewById(R.id.container_notification_slider),
                slider = view.findViewById(R.id.slider_notification),
                valueText = view.findViewById(R.id.tv_notification_value),
                currentText = view.findViewById(R.id.tv_notification_current)
            )
            ringConfig = StreamConfig(
                streamType = 2, streamName = "ring",
                btnKeep = view.findViewById(R.id.btn_ring_keep),
                btnSet = view.findViewById(R.id.btn_ring_set),
                btnMute = view.findViewById(R.id.btn_ring_mute),
                btnUnmute = view.findViewById(R.id.btn_ring_unmute),
                sliderContainer = view.findViewById(R.id.container_ring_slider),
                slider = view.findViewById(R.id.slider_ring),
                valueText = view.findViewById(R.id.tv_ring_value),
                currentText = view.findViewById(R.id.tv_ring_current)
            )
            systemConfig = StreamConfig(
                streamType = 1, streamName = "system",
                btnKeep = view.findViewById(R.id.btn_system_keep),
                btnSet = view.findViewById(R.id.btn_system_set),
                btnMute = view.findViewById(R.id.btn_system_mute),
                btnUnmute = view.findViewById(R.id.btn_system_unmute),
                sliderContainer = view.findViewById(R.id.container_system_slider),
                slider = view.findViewById(R.id.slider_system),
                valueText = view.findViewById(R.id.tv_system_value),
                currentText = view.findViewById(R.id.tv_system_current)
            )
            alarmConfig = StreamConfig(
                streamType = 4, streamName = "alarm",
                btnKeep = view.findViewById(R.id.btn_alarm_keep),
                btnSet = view.findViewById(R.id.btn_alarm_set),
                btnMute = view.findViewById(R.id.btn_alarm_mute),
                btnUnmute = view.findViewById(R.id.btn_alarm_unmute),
                sliderContainer = view.findViewById(R.id.container_alarm_slider),
                slider = view.findViewById(R.id.slider_alarm),
                valueText = view.findViewById(R.id.tv_alarm_value),
                currentText = view.findViewById(R.id.tv_alarm_current)
            )
            refreshButton = view.findViewById(R.id.btn_refresh_volumes)
        }
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "music_action", "music_value",
        "notification_action", "notification_value",
        "ring_action", "ring_value",
        "system_action", "system_value",
        "alarm_action", "alarm_value"
    )

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((android.content.Intent, (resultCode: Int, data: android.content.Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((android.content.Intent, (Int, android.content.Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_volume_editor, parent, false)
        val holder = ViewHolder(view)

        val configs = listOf(
            holder.musicConfig,
            holder.notificationConfig,
            holder.ringConfig,
            holder.systemConfig,
            holder.alarmConfig
        )

        // 为每个音频流设置监听器
        for (config in configs) {
            val paramActionId = "${config.streamName}_action"
            val paramValueId = "${config.streamName}_value"

            // 恢复操作类型
            val action = currentParameters[paramActionId] as? String ?: "keep"
            when (action) {
                "set" -> config.btnSet.isChecked = true
                "mute" -> config.btnMute.isChecked = true
                "unmute" -> config.btnUnmute.isChecked = true
                else -> config.btnKeep.isChecked = true
            }

            // 恢复音量值
            val value = (currentParameters[paramValueId] as? Number)?.toInt() ?: 50
            config.slider.value = value.toFloat()
            config.valueText.text = value.toString()

            // 初始化滑块显示状态
            updateSliderVisibility(config)

            // 监听按钮组变化
            val toggleGroup = config.btnKeep.parent as MaterialButtonToggleGroup
            toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    // 根据 checkedId 判断是否选中了"设置"按钮
                    config.sliderContainer.isVisible = (checkedId == config.btnSet.id)
                    onParametersChanged()
                }
            }

            // 监听滑块变化
            config.slider.addOnChangeListener { _, value, _ ->
                config.valueText.text = value.toInt().toString()
                onParametersChanged()
            }
        }

        // 刷新按钮：读取当前音量
        holder.refreshButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val volumes = withContext(Dispatchers.IO) {
                        VFlowCoreBridge.getAllVolumes()
                    }
                    if (volumes != null) {
                        // 将实际音量转换为百分比显示
                        val musicPercent = actualVolumeToPercent("music", volumes.musicCurrent)
                        val musicMax = 100
                        holder.musicConfig.currentText.text = "${musicPercent}%"

                        val notificationPercent = actualVolumeToPercent("notification", volumes.notificationCurrent)
                        holder.notificationConfig.currentText.text = "${notificationPercent}%"

                        val ringPercent = actualVolumeToPercent("ring", volumes.ringCurrent)
                        holder.ringConfig.currentText.text = "${ringPercent}%"

                        val systemPercent = actualVolumeToPercent("system", volumes.systemCurrent)
                        holder.systemConfig.currentText.text = "${systemPercent}%"

                        val alarmPercent = actualVolumeToPercent("alarm", volumes.alarmCurrent)
                        holder.alarmConfig.currentText.text = "${alarmPercent}%"
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }

        // 自动加载一次当前音量
        holder.refreshButton.performClick()

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val params = mutableMapOf<String, Any?>()

        val configs = listOf(
            h.musicConfig,
            h.notificationConfig,
            h.ringConfig,
            h.systemConfig,
            h.alarmConfig
        )

        for (config in configs) {
            val streamName = config.streamName
            val actionParamId = "${streamName}_action"
            val valueParamId = "${streamName}_value"

            val action = when {
                config.btnSet.isChecked -> "set"
                config.btnMute.isChecked -> "mute"
                config.btnUnmute.isChecked -> "unmute"
                else -> "keep"
            }

            params[actionParamId] = action
            params[valueParamId] = config.slider.value.toInt()
        }

        return params
    }

    private fun updateSliderVisibility(config: StreamConfig) {
        config.sliderContainer.isVisible = config.btnSet.isChecked
    }

    private fun getStreamName(streamType: Int): String {
        return when (streamType) {
            3 -> "music"
            5 -> "notification"
            2 -> "ring"
            1 -> "system"
            4 -> "alarm"
            else -> "unknown"
        }
    }
}
