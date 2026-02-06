package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import java.io.File

class PlayAudioViewHolder(
    view: View,
    val audioTypeToggle: MaterialButtonToggleGroup,
    val systemSoundContainer: View,
    val localFileContainer: View,
    val selectedSystemSoundText: TextView,
    val selectSystemSoundButton: Button,
    val selectLocalFileButton: Button,
    val selectedFileText: TextView,
    val volumeSlider: Slider,
    val volumeValueText: TextView,
    val awaitToggle: MaterialButtonToggleGroup
) : CustomEditorViewHolder(view)

class PlayAudioUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("audioType", "systemSound", "localFile", "volume", "await")

    private var currentAudioType: String = "system"

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (resultCode: Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_play_audio_editor, parent, false)

        val audioTypeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_audio_type)
        val systemSoundContainer = view.findViewById<View>(R.id.container_system_sound)
        val localFileContainer = view.findViewById<View>(R.id.container_local_file)
        val selectedSystemSoundText = view.findViewById<TextView>(R.id.text_selected_system_sound)
        val selectSystemSoundButton = view.findViewById<Button>(R.id.button_select_system_sound)
        val selectLocalFileButton = view.findViewById<Button>(R.id.button_select_local_file)
        val selectedFileText = view.findViewById<TextView>(R.id.text_selected_file)
        val volumeSlider = view.findViewById<Slider>(R.id.slider_volume)
        val volumeValueText = view.findViewById<TextView>(R.id.text_volume_value)
        val awaitToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_await)

        val holder = PlayAudioViewHolder(
            view, audioTypeToggle, systemSoundContainer, localFileContainer,
            selectedSystemSoundText, selectSystemSoundButton, selectLocalFileButton,
            selectedFileText, volumeSlider, volumeValueText, awaitToggle
        )

        // 恢复当前状态
        currentAudioType = currentParameters["audioType"] as? String ?: "system"
        val systemSound = currentParameters["systemSound"] as? String ?: "notification"
        val localFile = currentParameters["localFile"] as? String ?: ""
        val volume = currentParameters["volume"] as? Number ?: 100
        val awaitComplete = currentParameters["await"] as? Boolean ?: true

        updateAudioTypeUI(context, holder, currentAudioType)
        updateSystemSoundUI(context, holder, systemSound)
        updateLocalFileUI(context, holder, localFile)
        updateVolumeUI(holder, volume)
        updateAwaitUI(holder, awaitComplete)

        // 音频类型切换
        audioTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newType = when (checkedId) {
                    R.id.btn_system -> "system"
                    R.id.btn_local -> "local"
                    else -> "system"
                }
                if (newType != currentAudioType) {
                    currentAudioType = newType
                    updateAudioTypeUI(context, holder, newType)
                    onParametersChanged()
                }
            }
        }

        // 选择系统音频
        selectSystemSoundButton.setOnClickListener {
            showSystemSoundPicker(context) { soundType ->
                updateSystemSoundUI(context, holder, soundType)
                onParametersChanged()
            }
        }

        // 选择本地文件
        selectLocalFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    val filePath = getFilePathFromUri(context, uri)
                    updateLocalFileUI(context, holder, filePath)
                    onParametersChanged()
                }
            }
        }

        // 音量滑块
        volumeSlider.addOnChangeListener { _, value, _ ->
            volumeValueText.text = "${value.toInt()}%"
            onParametersChanged()
        }

        // 等待播放完成切换
        awaitToggle.addOnButtonCheckedListener { _, _, _ ->
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val audioHolder = holder as? PlayAudioViewHolder ?: return emptyMap()
        val audioType = when (audioHolder.audioTypeToggle.checkedButtonId) {
            R.id.btn_system -> "system"
            R.id.btn_local -> "local"
            else -> "system"
        }

        return mapOf(
            "audioType" to audioType,
            "systemSound" to getSystemSoundFromIndex(currentSystemSoundIndex),
            "localFile" to currentFilePath,
            "volume" to audioHolder.volumeSlider.value.toInt(),
            "await" to (audioHolder.awaitToggle.checkedButtonId == R.id.btn_await_yes)
        )
    }

    private var currentSystemSoundIndex: Int = 0
    private var currentFilePath: String = ""

    private fun updateAudioTypeUI(context: Context, holder: PlayAudioViewHolder, audioType: String) {
        when (audioType) {
            "system" -> {
                holder.audioTypeToggle.check(R.id.btn_system)
                holder.systemSoundContainer.visibility = View.VISIBLE
                holder.localFileContainer.visibility = View.GONE
            }
            "local" -> {
                holder.audioTypeToggle.check(R.id.btn_local)
                holder.systemSoundContainer.visibility = View.GONE
                holder.localFileContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun updateSystemSoundUI(context: Context, holder: PlayAudioViewHolder, soundType: String) {
        currentSystemSoundIndex = getSystemSoundIndex(soundType)
        val soundNames = listOf(
            context.getString(R.string.sound_notification),
            context.getString(R.string.sound_alarm),
            context.getString(R.string.sound_ringtone),
            context.getString(R.string.sound_notification_2),
            context.getString(R.string.sound_notification_3),
            context.getString(R.string.sound_notification_4),
            context.getString(R.string.sound_notification_5)
        )
        holder.selectedSystemSoundText.text = soundNames.getOrElse(currentSystemSoundIndex) { soundType }
    }

    private fun updateLocalFileUI(context: Context, holder: PlayAudioViewHolder, filePath: String) {
        currentFilePath = filePath
        holder.selectedFileText.text = if (filePath.isNotBlank()) {
            filePath.substringAfterLast("/")
        } else {
            context.getString(R.string.text_not_selected)
        }
    }

    private fun updateVolumeUI(holder: PlayAudioViewHolder, volume: Number) {
        holder.volumeSlider.value = volume.toFloat().coerceIn(0f, 100f)
        holder.volumeValueText.text = "${volume}%"
    }

    private fun updateAwaitUI(holder: PlayAudioViewHolder, awaitComplete: Boolean) {
        holder.awaitToggle.check(if (awaitComplete) R.id.btn_await_yes else R.id.btn_await_no)
    }

    private fun showSystemSoundPicker(context: Context, onSoundSelected: (String) -> Unit) {
        val soundTypes = arrayOf(
            context.getString(R.string.sound_notification),
            context.getString(R.string.sound_alarm),
            context.getString(R.string.sound_ringtone),
            context.getString(R.string.sound_notification_2),
            context.getString(R.string.sound_notification_3),
            context.getString(R.string.sound_notification_4),
            context.getString(R.string.sound_notification_5)
        )

        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.param_vflow_device_play_audio_system_sound_name)
            .setItems(soundTypes) { _, which ->
                onSoundSelected(getSystemSoundFromIndex(which))
            }
            .show()
    }

    private fun getSystemSoundIndex(soundType: String): Int {
        return when (soundType) {
            "notification" -> 0
            "alarm" -> 1
            "ringtone" -> 2
            "notification_2" -> 3
            "notification_3" -> 4
            "notification_4" -> 5
            "notification_5" -> 6
            else -> 0
        }
    }

    private fun getSystemSoundFromIndex(index: Int): String {
        return when (index) {
            0 -> "notification"
            1 -> "alarm"
            2 -> "ringtone"
            3 -> "notification_2"
            4 -> "notification_3"
            5 -> "notification_4"
            6 -> "notification_5"
            else -> "notification"
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri?): String {
        if (uri == null) return ""

        return try {
            // Android 11+ 需要使用 MediaStore 获取真实路径
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val contentResolver = context.contentResolver
                val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null

                        // 保存到应用缓存目录
                        name?.let {
                            val cacheDir = File(context.cacheDir, "audio")
                            if (!cacheDir.exists()) cacheDir.mkdirs()
                            val destFile = File(cacheDir, it)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            return destFile.absolutePath
                        }
                    }
                }
            }

            uri.path ?: ""
        } catch (e: Exception) {
            uri.path ?: ""
        }
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, Intent?) -> Unit) -> Unit)?
    ): View? = null
}
