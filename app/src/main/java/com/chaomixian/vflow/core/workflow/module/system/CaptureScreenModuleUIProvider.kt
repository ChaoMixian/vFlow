// 文件: CaptureScreenModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import coil.load
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.overlay.RegionSelectionOverlay
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.io.File
import java.lang.ref.WeakReference

class CaptureScreenModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val cardRegionPreview: MaterialCardView = view.findViewById(R.id.card_region_preview)
        val ivRegionPreview: ImageView = view.findViewById(R.id.iv_region_preview)
        val layoutPlaceholder: LinearLayout = view.findViewById(R.id.layout_placeholder)
        val layoutRegionInfo: LinearLayout = view.findViewById(R.id.layout_region_info)
        val tvRegionInfo: TextView = view.findViewById(R.id.tv_region_info)
        val btnSelectRegion: MaterialButton = view.findViewById(R.id.btn_select_region)
        val btnClearRegion: MaterialButton = view.findViewById(R.id.btn_clear_region)

        var region: String = ""
        var screenshotUri: Uri? = null
        var onParametersChangedCallback: (() -> Unit)? = null
        var scope: CoroutineScope? = null
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("region")
    }

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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_capture_screen_editor, parent, false)
        val holder = ViewHolder(view)
        holder.onParametersChangedCallback = onParametersChanged
        holder.scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // 恢复区域参数
        val region = currentParameters["region"] as? String ?: ""
        holder.region = region

        if (region.isNotEmpty()) {
            // 解析区域，如果有截图则显示预览
            updateRegionInfo(holder, region)
        }

        // 点击预览区域弹出输入框
        holder.cardRegionPreview.setOnClickListener {
            showRegionInputDialog(context, holder)
        }

        // 选择区域按钮
        holder.btnSelectRegion.setOnClickListener {
            selectRegion(context, holder)
        }

        // 清除区域按钮
        holder.btnClearRegion.setOnClickListener {
            holder.region = ""
            holder.screenshotUri = null
            holder.ivRegionPreview.isVisible = false
            holder.layoutPlaceholder.isVisible = true
            holder.layoutRegionInfo.isVisible = false
            holder.onParametersChangedCallback?.invoke()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf("region" to h.region)
    }

    /**
     * 显示区域输入对话框
     */
    private fun showRegionInputDialog(context: Context, holder: ViewHolder) {
        // 创建输入框
        val textInputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "区域坐标 (left,top,right,bottom)"
        }

        val textInputEditText = TextInputEditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(holder.region)
        }

        textInputLayout.addView(textInputEditText)

        MaterialAlertDialogBuilder(context)
            .setTitle("设置截屏区域")
            .setMessage("请输入区域坐标\n例如：100,100,500,600")
            .setView(textInputLayout)
            .setPositiveButton("确定") { dialog, _ ->
                val input = textInputEditText.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    // 验证格式
                    val parts = input.split(",")
                    if (parts.size == 4 && parts.all { it.trim().toIntOrNull() != null }) {
                        holder.region = input
                        updateRegionInfo(holder, input)
                        holder.onParametersChangedCallback?.invoke()
                        dialog.dismiss()
                    } else {
                        textInputLayout.error = "格式错误，请输入4个数字，用逗号分隔"
                    }
                } else {
                    holder.region = ""
                    holder.screenshotUri = null
                    holder.ivRegionPreview.isVisible = false
                    holder.layoutPlaceholder.isVisible = true
                    holder.layoutRegionInfo.isVisible = false
                    holder.onParametersChangedCallback?.invoke()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun selectRegion(context: Context, holder: ViewHolder) {
        DebugLogger.i("CaptureScreenModuleUIProvider", "用户点击区域选择按钮")

        // 检查悬浮窗权限
        if (!PermissionManager.isGranted(context, PermissionManager.OVERLAY)) {
            DebugLogger.w("CaptureScreenModuleUIProvider", "悬浮窗权限未授予")
            Toast.makeText(context, "需要悬浮窗权限才能选择区域", Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("CaptureScreenModuleUIProvider", "悬浮窗权限检查通过")

        // 检查 Shell 权限
        val shellPermissions = ShellManager.getRequiredPermissions(context)
        val hasShellPermission = shellPermissions.all { PermissionManager.isGranted(context, it) }
        if (!hasShellPermission) {
            DebugLogger.w("CaptureScreenModuleUIProvider", "Shell 权限未授予: $shellPermissions")
            Toast.makeText(context, "需要 Shizuku 或 Root 权限才能截图", Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("CaptureScreenModuleUIProvider", "Shell 权限检查通过")

        // 使用弱引用避免内存泄漏
        val contextRef = WeakReference(context)
        val holderRef = WeakReference(holder)

        holder.scope?.launch {
            try {
                // 使用外部存储目录，确保 shell 可以写入
                val screenshotDir = StorageManager.tempDir
                DebugLogger.i("CaptureScreenModuleUIProvider", "创建 RegionSelectionOverlay，使用目录: ${screenshotDir.absolutePath}")
                val overlay = RegionSelectionOverlay(context, screenshotDir)
                val result = overlay.captureAndSelectRegion()

                DebugLogger.i("CaptureScreenModuleUIProvider", "区域选择流程结束，result: $result")

                withContext(Dispatchers.Main) {
                    val ctx = contextRef.get()
                    val h = holderRef.get()

                    if (ctx != null && h != null && result != null) {
                        h.region = result.region
                        h.screenshotUri = result.screenshotUri

                        // 更新预览
                        if (result.screenshotUri != null) {
                            h.ivRegionPreview.isVisible = true
                            h.layoutPlaceholder.isVisible = false
                            h.ivRegionPreview.load(result.screenshotUri) {
                                crossfade(true)
                                error(R.drawable.rounded_broken_image_24)
                            }
                        }

                        // 显示区域信息
                        updateRegionInfo(h, result.region)

                        h.onParametersChangedCallback?.invoke()
                        DebugLogger.i("CaptureScreenModuleUIProvider", "区域信息已更新")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("CaptureScreenModuleUIProvider", "区域选择失败", e)
                withContext(Dispatchers.Main) {
                    contextRef.get()?.let {
                        Toast.makeText(it, "区域选择失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateRegionInfo(holder: ViewHolder, region: String) {
        holder.layoutRegionInfo.isVisible = true
        holder.tvRegionInfo.text = "区域: $region"

        // 如果有截图，显示裁剪后的预览
        if (holder.screenshotUri != null) {
            holder.ivRegionPreview.isVisible = true
            holder.layoutPlaceholder.isVisible = false

            // 这里可以进一步优化，显示裁剪后的图片而不是全屏截图
            // 但为了简化，先显示全屏截图
        }
    }
}

/**
 * 区域选择结果
 */
data class RegionSelectionResult(
    val region: String,          // 格式: "left,top,right,bottom"
    val screenshotUri: Uri?      // 全屏截图的 URI
)
