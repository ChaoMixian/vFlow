package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.speech.voice.VoiceTemplateEnrollment
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.speech.voice.VoiceTriggerModelManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.VFlowTheme
import kotlinx.coroutines.launch

class VoiceTriggerRecorderActivity : BaseActivity() {
    companion object {
        const val EXTRA_RESULT_TEMPLATE_1 = "voice_trigger_template_1"
        const val EXTRA_RESULT_TEMPLATE_2 = "voice_trigger_template_2"
        const val EXTRA_RESULT_TEMPLATE_3 = "voice_trigger_template_3"

        fun createIntent(context: Context): Intent {
            return Intent(context, VoiceTriggerRecorderActivity::class.java)
        }
    }

    private var template1 by mutableStateOf<FloatArray?>(null)
    private var template2 by mutableStateOf<FloatArray?>(null)
    private var template3 by mutableStateOf<FloatArray?>(null)
    private var recordingStep by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!VoiceTriggerModelManager(this).isModelInstalled()) {
            Toast.makeText(this, R.string.voice_trigger_model_missing_prompt, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            VFlowTheme {
                VoiceTriggerRecorderScreen(
                    recordingStep = recordingStep,
                    step1Ready = template1 != null,
                    step2Ready = template2 != null,
                    step3Ready = template3 != null,
                    onBack = { finish() },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onSave = { saveAndFinish() },
                    onRecordStep = ::recordStep,
                )
            }
        }
    }

    private fun recordStep(step: Int) {
        if (recordingStep != 0) return
        val modelFile = VoiceTriggerModelManager(this).modelFile()
        val audioSource = VoiceTriggerConfig.readAudioSource(this)
        recordingStep = step
        lifecycleScope.launch {
            val featureVector = runCatching {
                VoiceTemplateEnrollment.recordOneTemplate(
                    modelFile = modelFile,
                    audioSource = audioSource,
                )
            }.getOrNull()
            if (featureVector == null) {
                Toast.makeText(
                    this@VoiceTriggerRecorderActivity,
                    R.string.voice_trigger_record_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                when (step) {
                    1 -> template1 = featureVector
                    2 -> template2 = featureVector
                    3 -> template3 = featureVector
                }
            }
            recordingStep = 0
        }
    }

    private fun saveAndFinish() {
        val first = template1
        val second = template2
        val third = template3
        if (recordingStep != 0 || first == null || second == null || third == null) return
        val result = Intent().apply {
            putExtra(EXTRA_RESULT_TEMPLATE_1, first)
            putExtra(EXTRA_RESULT_TEMPLATE_2, second)
            putExtra(EXTRA_RESULT_TEMPLATE_3, third)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

@Composable
private fun VoiceTriggerRecorderScreen(
    recordingStep: Int,
    step1Ready: Boolean,
    step2Ready: Boolean,
    step3Ready: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onRecordStep: (Int) -> Unit,
) {
    val statusText = if (recordingStep == 0) {
        stringResource(R.string.voice_trigger_record_status_ready)
    } else {
        stringResource(R.string.voice_trigger_record_status_recording, recordingStep)
    }
    val hintText = if (recordingStep == 0) {
        stringResource(R.string.voice_trigger_record_hint_ready)
    } else {
        stringResource(R.string.voice_trigger_record_hint_recording)
    }
    val canSave = recordingStep == 0 && step1Ready && step2Ready && step3Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_trigger_record_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            VoiceTriggerRecordButton(
                step = 1,
                ready = step1Ready,
                recordingStep = recordingStep,
                onClick = onRecordStep,
            )
            Spacer(modifier = Modifier.height(12.dp))
            VoiceTriggerRecordButton(
                step = 2,
                ready = step2Ready,
                recordingStep = recordingStep,
                onClick = onRecordStep,
            )
            Spacer(modifier = Modifier.height(12.dp))
            VoiceTriggerRecordButton(
                step = 3,
                ready = step3Ready,
                recordingStep = recordingStep,
                onClick = onRecordStep,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.voice_trigger_record_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_save))
                }
                TextButton(
                    onClick = onCancel,
                    enabled = recordingStep == 0
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

@Composable
private fun VoiceTriggerRecordButton(
    step: Int,
    ready: Boolean,
    recordingStep: Int,
    onClick: (Int) -> Unit,
) {
    val text = when {
        recordingStep == step -> stringResource(R.string.voice_trigger_record_step_recording)
        ready -> stringResource(R.string.voice_trigger_record_step_done)
        else -> stringResource(R.string.voice_trigger_record_step_label, step)
    }
    OutlinedButton(
        onClick = { onClick(step) },
        enabled = recordingStep == 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}
