// 文件: CaptureScreenModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.overlay.RegionSelectionOverlay
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

        // 点击预览区域也可以选择区域
        holder.cardRegionPreview.setOnClickListener {
            selectRegion(context, holder)
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

    private fun selectRegion(context: Context, holder: ViewHolder) {
        // 检查悬浮窗权限
        if (!PermissionManager.isGranted(context, PermissionManager.OVERLAY)) {
            Toast.makeText(context, "需要悬浮窗权限才能选择区域", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查 Shell 权限
        val shellPermissions = ShellManager.getRequiredPermissions(context)
        val hasShellPermission = shellPermissions.all { PermissionManager.isGranted(context, it) }
        if (!hasShellPermission) {
            Toast.makeText(context, "需要 Shizuku 或 Root 权限才能截图", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用弱引用避免内存泄漏
        val contextRef = WeakReference(context)
        val holderRef = WeakReference(holder)
        val activity = context as? Activity

        holder.scope?.launch {
            try {
                // 最小化当前 Activity
                activity?.moveTaskToBack(true)

                val cacheDir = context.cacheDir
                val overlay = RegionSelectionOverlay(context, cacheDir)
                val result = overlay.captureAndSelectRegion()

                withContext(Dispatchers.Main) {
                    // 恢复 Activity
                    activity?.let { act ->
                        val intent = Intent(act, act::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        act.startActivity(intent)
                    }

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
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("CaptureScreenModuleUIProvider", "区域选择失败", e)
                withContext(Dispatchers.Main) {
                    activity?.let { act ->
                        val intent = Intent(act, act::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        act.startActivity(intent)
                    }

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
