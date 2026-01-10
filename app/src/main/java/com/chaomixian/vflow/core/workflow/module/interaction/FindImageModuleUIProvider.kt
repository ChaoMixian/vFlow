// 文件: FindImageModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.chaomixian.vflow.ui.overlay.ScreenCaptureOverlay
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

class FindImageModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val cardImagePreview: MaterialCardView = view.findViewById(R.id.card_image_preview)
        val ivTemplatePreview: ImageView = view.findViewById(R.id.iv_template_preview)
        val layoutPlaceholder: LinearLayout = view.findViewById(R.id.layout_placeholder)
        val btnPickFromGallery: MaterialButton = view.findViewById(R.id.btn_pick_from_gallery)
        val btnCaptureScreen: MaterialButton = view.findViewById(R.id.btn_capture_screen)
        val cgThreshold: ChipGroup = view.findViewById(R.id.cg_threshold)
        val chipThreshold90: Chip = view.findViewById(R.id.chip_threshold_90)
        val chipThreshold80: Chip = view.findViewById(R.id.chip_threshold_80)
        val chipThreshold70: Chip = view.findViewById(R.id.chip_threshold_70)
        val chipThreshold60: Chip = view.findViewById(R.id.chip_threshold_60)
        val cgOutputFormat: ChipGroup = view.findViewById(R.id.cg_output_format)
        val chipOutputCoordinate: Chip = view.findViewById(R.id.chip_output_coordinate)

        var templateUri: String = ""
        var onParametersChangedCallback: (() -> Unit)? = null
        var scope: CoroutineScope? = null
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("template_uri", "threshold", "output_format")
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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_find_image_editor, parent, false)
        val holder = ViewHolder(view)
        holder.onParametersChangedCallback = onParametersChanged
        holder.scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // 恢复模板图片
        val templateUri = currentParameters["template_uri"] as? String ?: ""
        holder.templateUri = templateUri
        updateImagePreview(context, holder, templateUri)

        // 恢复相似度设置
        val threshold = currentParameters["threshold"] as? String ?: "80% (推荐)"
        when {
            threshold.startsWith("90") -> holder.chipThreshold90.isChecked = true
            threshold.startsWith("80") -> holder.chipThreshold80.isChecked = true
            threshold.startsWith("70") -> holder.chipThreshold70.isChecked = true
            threshold.startsWith("60") -> holder.chipThreshold60.isChecked = true
            else -> holder.chipThreshold80.isChecked = true
        }

        // 点击图片预览区域也可以选择图片
        holder.cardImagePreview.setOnClickListener {
            showImageSourceDialog(context, holder, onStartActivityForResult)
        }

        // 从相册选择
        holder.btnPickFromGallery.setOnClickListener {
            pickImageFromGallery(context, holder, onStartActivityForResult)
        }

        // 截图选取
        holder.btnCaptureScreen.setOnClickListener {
            captureAndCropScreen(context, holder)
        }

        // 相似度变化监听
        holder.cgThreshold.setOnCheckedStateChangeListener { _, _ ->
            onParametersChanged()
        }

        // 输出格式变化监听
        holder.cgOutputFormat.setOnCheckedStateChangeListener { _, _ ->
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder

        val threshold = when {
            h.chipThreshold90.isChecked -> "90% (精确)"
            h.chipThreshold80.isChecked -> "80% (推荐)"
            h.chipThreshold70.isChecked -> "70% (宽松)"
            h.chipThreshold60.isChecked -> "60% (模糊)"
            else -> "80% (推荐)"
        }

        val outputFormat = when {
            h.chipOutputCoordinate.isChecked -> "坐标"
            else -> "坐标"
        }

        return mapOf(
            "template_uri" to h.templateUri,
            "threshold" to threshold,
            "output_format" to outputFormat
        )
    }

    private fun showImageSourceDialog(
        context: Context,
        holder: ViewHolder,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ) {
        val options = arrayOf("从相册选择", "截图选取")
        android.app.AlertDialog.Builder(context)
            .setTitle("选择图片来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery(context, holder, onStartActivityForResult)
                    1 -> captureAndCropScreen(context, holder)
                }
            }
            .show()
    }

    private fun pickImageFromGallery(
        context: Context,
        holder: ViewHolder,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ) {
        if (onStartActivityForResult == null) {
            Toast.makeText(context, "无法启动图片选择器", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        onStartActivityForResult(intent) { resultCode, data ->
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val uri = data.data!!
                // 复制图片到应用缓存目录
                holder.scope?.launch {
                    val savedUri = copyImageToCache(context, uri)
                    if (savedUri != null) {
                        holder.templateUri = savedUri.toString()
                        updateImagePreview(context, holder, savedUri.toString())
                        holder.onParametersChangedCallback?.invoke()
                    }
                }
            }
        }
    }

    private fun captureAndCropScreen(context: Context, holder: ViewHolder) {
        // 检查悬浮窗权限
        if (!PermissionManager.isGranted(context, PermissionManager.OVERLAY)) {
            Toast.makeText(context, "需要悬浮窗权限才能截图", Toast.LENGTH_SHORT).show()
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

        // 保存 Activity 引用，用于截图完成后恢复
        val activity = context as? Activity

        holder.scope?.launch {
            try {
                // 最小化当前 Activity，让用户看到要截图的内容
                activity?.moveTaskToBack(true)

                val cacheDir = context.cacheDir
                // 使用原始 context（Activity context）以保持 Material 主题
                val overlay = ScreenCaptureOverlay(context, cacheDir)
                val resultUri = overlay.captureAndCrop()

                // 确保在主线程更新 UI
                withContext(Dispatchers.Main) {
                    // 恢复 Activity 到前台
                    activity?.let { act ->
                        val intent = Intent(act, act::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        act.startActivity(intent)
                    }

                    val ctx = contextRef.get()
                    val h = holderRef.get()

                    if (ctx != null && h != null && resultUri != null) {
                        h.templateUri = resultUri.toString()
                        updateImagePreview(ctx, h, resultUri.toString())
                        h.onParametersChangedCallback?.invoke()
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("FindImageModuleUIProvider", "截图失败", e)
                withContext(Dispatchers.Main) {
                    // 恢复 Activity 到前台
                    activity?.let { act ->
                        val intent = Intent(act, act::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        act.startActivity(intent)
                    }

                    contextRef.get()?.let {
                        Toast.makeText(it, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun copyImageToCache(context: Context, sourceUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) return@withContext null

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                val outputFile = File(context.cacheDir, "template_$timestamp.png")
                FileOutputStream(outputFile).use { fos ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                }
                bitmap.recycle()

                Uri.fromFile(outputFile)
            } catch (e: Exception) {
                DebugLogger.e("FindImageModuleUIProvider", "复制图片失败", e)
                null
            }
        }
    }

    private fun updateImagePreview(context: Context, holder: ViewHolder, uriString: String) {
        if (uriString.isEmpty()) {
            holder.ivTemplatePreview.isVisible = false
            holder.layoutPlaceholder.isVisible = true
        } else {
            holder.ivTemplatePreview.isVisible = true
            holder.layoutPlaceholder.isVisible = false
            holder.ivTemplatePreview.load(Uri.parse(uriString)) {
                crossfade(true)
                error(R.drawable.rounded_broken_image_24)
            }
        }
    }
}
