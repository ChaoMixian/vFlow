// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/FlashlightModule.kt
// 描述: 手电筒模块，使用Android CameraManager API控制设备手电筒
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.module.InputDefinition.Companion.slider
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 手电筒模块
 * 使用Android CameraManager API控制设备手电筒
 * 支持切换、开启、关闭三种操作模式
 * Android 13+ 支持强度控制
 */
class FlashlightModule : BaseModule() {

    override val id = "vflow.device.flashlight"

    override val metadata = ActionMetadata(
        name = "手电筒",
        nameStringRes = R.string.module_vflow_device_flashlight_name,
        description = "控制设备手电筒，支持切换、开启、关闭三种模式",
        descriptionStringRes = R.string.module_vflow_device_flashlight_desc,
        iconRes = R.drawable.rounded_flashlight_on_24,
        category = "应用与系统"
    )

    // 操作模式常量
    companion object {
        const val MODE_TOGGLE = "toggle"  // 切换
        const val MODE_ON = "on"          // 开启
        const val MODE_OFF = "off"        // 关闭
        const val PREFS_NAME = "flashlight_prefs"
        const val PREFS_LAST_STATE = "last_flashlight_state"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // 操作模式
        InputDefinition(
            id = "mode",
            name = "操作模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_TOGGLE,
            options = listOf(MODE_TOGGLE, MODE_ON, MODE_OFF),
            optionsStringRes = listOf(
                R.string.option_vflow_device_flashlight_toggle,
                R.string.option_vflow_device_flashlight_on,
                R.string.option_vflow_device_flashlight_off
            ),
            inputStyle = InputStyle.CHIP_GROUP,
            nameStringRes = R.string.param_vflow_device_flashlight_mode_name
        ),

        // 手电筒强度（仅Android 13+且在"开启"模式下有效）
        InputDefinition(
            id = "strengthPercent",
            name = "手电筒强度",
            staticType = ParameterType.NUMBER,
            defaultValue = 50,
            sliderConfig = slider(1f, 100f, 1f),
            inputStyle = InputStyle.SLIDER,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_flashlight_strength_name
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val mode = step?.parameters?.get("mode") as? String ?: MODE_TOGGLE
        val isOnMode = mode == MODE_ON
        val supportsStrength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        return getInputs().map { input ->
            if (input.id == "strengthPercent") {
                input.copy(
                    isHidden = !isOnMode,
                    hint = if (supportsStrength) {
                        "强度百分比 (1-100)"
                    } else {
                        "当前系统不支持 (需要 Android 13+)"
                    }
                )
            } else {
                input
            }
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = "vflow.type.boolean",
            nameStringRes = R.string.output_vflow_device_flashlight_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_TOGGLE

        val modeText = when (mode) {
            MODE_TOGGLE -> context.getString(R.string.summary_vflow_device_flashlight_toggle)
            MODE_ON -> {
                val strength = (step.parameters["strengthPercent"] as? Number)?.toInt() ?: 50
                context.getString(R.string.summary_vflow_device_flashlight_on, strength)
            }
            MODE_OFF -> context.getString(R.string.summary_vflow_device_flashlight_off)
            else -> context.getString(R.string.summary_vflow_device_flashlight_toggle)
        }

        val modePill = PillUtil.Pill(modeText, "mode", isModuleOption = true)
        return PillUtil.buildSpannable(context, metadata.getLocalizedName(context) + ": ", modePill)
    }

    /**
     * 获取带有闪光灯的后置相机ID
     */
    private fun getBackCameraIdWithFlash(cameraManager: CameraManager): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)

                if (facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash == true) {
                    return id
                }
            }
            null
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取保存的手电筒状态
     */
    private fun getLastTorchState(): Boolean {
        return try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(PREFS_LAST_STATE, false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 保存手电筒状态
     */
    private fun saveTorchState(isOn: Boolean) {
        try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREFS_LAST_STATE, isOn).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val step = context.allSteps[context.currentStepIndex]
        val mode = step.parameters["mode"] as? String ?: MODE_TOGGLE

        // 获取 CameraManager
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_flashlight_failed),
                appContext.getString(R.string.error_vflow_device_flashlight_no_camera)
            )

        // 获取后置相机ID
        val cameraId = getBackCameraIdWithFlash(cameraManager)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_flashlight_unavailable),
                appContext.getString(R.string.error_vflow_device_flashlight_no_camera)
            )

        // 获取相机特性
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        return try {
            when (mode) {
                MODE_TOGGLE -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_flashlight_toggling)))

                    // 读取上次保存的状态
                    val lastState = getLastTorchState()
                    val targetState = !lastState

                    // 执行切换
                    if (targetState) {
                        turnOnFlashlight(cameraManager, cameraId, characteristics, step)
                    } else {
                        cameraManager.setTorchMode(cameraId, false)
                    }

                    // 保存新状态
                    saveTorchState(targetState)
                }

                MODE_ON -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_flashlight_turning_on)))

                    turnOnFlashlight(cameraManager, cameraId, characteristics, step)
                    saveTorchState(true)
                }

                MODE_OFF -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_flashlight_turning_off)))

                    cameraManager.setTorchMode(cameraId, false)
                    saveTorchState(false)
                }

                else -> {
                    return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_device_flashlight_failed),
                        appContext.getString(R.string.error_vflow_device_flashlight_unknown_mode, mode)
                    )
                }
            }

            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_flashlight_completed), 100))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: CameraAccessException) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_flashlight_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_device_flashlight_unknown_mode, "CameraAccessException")
            )
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_flashlight_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_device_flashlight_unknown_mode, "Unknown Exception")
            )
        }
    }

    /**
     * 打开手电筒（根据系统版本选择合适的方法）
     */
    private fun turnOnFlashlight(
        cameraManager: CameraManager,
        cameraId: String,
        characteristics: CameraCharacteristics,
        step: ActionStep
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 支持强度控制
            val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)

            if (maxLevel != null && maxLevel > 1) {
                // 设备支持强度控制
                val strengthPercent = (step.parameters["strengthPercent"] as? Number)?.toInt() ?: 50
                val strengthLevel = ((strengthPercent / 100.0) * maxLevel).toInt().coerceIn(1, maxLevel)
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel)
            } else {
                // 设备不支持强度控制，使用默认方法
                cameraManager.setTorchMode(cameraId, true)
            }
        } else {
            // Android 12 及以下，使用传统方法
            cameraManager.setTorchMode(cameraId, true)
        }
    }
}
