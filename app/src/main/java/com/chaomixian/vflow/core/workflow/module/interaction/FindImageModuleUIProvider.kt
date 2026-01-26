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
import com.chaomixian.vflow.core.utils.StorageManager
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
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64

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
                // 编码图片为 Base64
                holder.scope?.launch {
                    val base64String = encodeImageToBase64(context, uri)
                    if (base64String != null) {
                        holder.templateUri = base64String
                        updateImagePreview(context, holder, base64String)
                        holder.onParametersChangedCallback?.invoke()
                    }
                }
            }
        }
    }

    private fun captureAndCropScreen(context: Context, holder: ViewHolder) {
        DebugLogger.i("FindImageModuleUIProvider", "用户点击截图按钮")

        // 检查悬浮窗权限
        if (!PermissionManager.isGranted(context, PermissionManager.OVERLAY)) {
            DebugLogger.w("FindImageModuleUIProvider", "悬浮窗权限未授予")
            Toast.makeText(context, "需要悬浮窗权限才能截图", Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("FindImageModuleUIProvider", "悬浮窗权限检查通过")

        // 检查 Shell 权限
        val shellPermissions = ShellManager.getRequiredPermissions(context)
        val hasShellPermission = shellPermissions.all { PermissionManager.isGranted(context, it) }
        if (!hasShellPermission) {
            DebugLogger.w("FindImageModuleUIProvider", "Shell 权限未授予: $shellPermissions")
            Toast.makeText(context, "需要 Shizuku 或 Root 权限才能截图", Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("FindImageModuleUIProvider", "Shell 权限检查通过")

        // 使用弱引用避免内存泄漏
        val contextRef = WeakReference(context)
        val holderRef = WeakReference(holder)

        holder.scope?.launch {
            try {
                // 使用外部存储目录，确保 shell 可以写入
                val screenshotDir = StorageManager.tempDir
                DebugLogger.i("FindImageModuleUIProvider", "创建 ScreenCaptureOverlay，使用目录: ${screenshotDir.absolutePath}")
                // 使用原始 context（Activity context）以保持 Material 主题
                val overlay = ScreenCaptureOverlay(context, screenshotDir)
                val resultUri = overlay.captureAndCrop()

                DebugLogger.i("FindImageModuleUIProvider", "截图流程结束，resultUri: $resultUri")

                // 将 URI 转换为 Base64
                val base64String = if (resultUri != null) {
                    DebugLogger.i("FindImageModuleUIProvider", "开始将 URI 转换为 Base64")
                    uriToBase64(resultUri)
                } else {
                    DebugLogger.w("FindImageModuleUIProvider", "截图被取消或失败")
                    null
                }

                // 确保在主线程更新 UI
                withContext(Dispatchers.Main) {
                    val ctx = contextRef.get()
                    val h = holderRef.get()

                    if (ctx != null && h != null && base64String != null) {
                        h.templateUri = base64String
                        updateImagePreview(ctx, h, base64String)
                        h.onParametersChangedCallback?.invoke()
                        DebugLogger.i("FindImageModuleUIProvider", "图片预览已更新")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e("FindImageModuleUIProvider", "截图失败", e)
                withContext(Dispatchers.Main) {
                    contextRef.get()?.let {
                        Toast.makeText(it, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun uriToBase64(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(uri.path)
                if (bitmap == null) return@withContext null

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                bitmap.recycle()

                base64String
            } catch (e: Exception) {
                DebugLogger.e("FindImageModuleUIProvider", "URI 转 Base64 失败", e)
                null
            }
        }
    }

    private suspend fun encodeImageToBase64(context: Context, sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) return@withContext null

                // 将 Bitmap 编码为 Base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                bitmap.recycle()

                base64String
            } catch (e: Exception) {
                DebugLogger.e("FindImageModuleUIProvider", "编码图片失败", e)
                null
            }
        }
    }

    private fun updateImagePreview(context: Context, holder: ViewHolder, dataString: String) {
        if (dataString.isEmpty()) {
            holder.ivTemplatePreview.isVisible = false
            holder.layoutPlaceholder.isVisible = true
        } else {
            holder.ivTemplatePreview.isVisible = true
            holder.layoutPlaceholder.isVisible = false

            // 判断是 Base64 还是旧格式的 URI
            if (dataString.startsWith("data:image") || dataString.length > 1000) {
                // Base64 格式: 解码为 Bitmap 后显示
                try {
                    val base64Data = if (dataString.startsWith("data:image")) {
                        dataString.substringAfter("base64,")
                    } else {
                        dataString
                    }
                    val byteArray = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    if (bitmap != null) {
                        holder.ivTemplatePreview.setImageBitmap(bitmap)
                    } else {
                        holder.ivTemplatePreview.setImageResource(R.drawable.rounded_broken_image_24)
                    }
                } catch (e: Exception) {
                    DebugLogger.e("FindImageModuleUIProvider", "加载 Base64 图片失败", e)
                    holder.ivTemplatePreview.setImageResource(R.drawable.rounded_broken_image_24)
                }
            } else {
                // 旧格式 URI
                holder.ivTemplatePreview.load(Uri.parse(dataString)) {
                    crossfade(true)
                    error(R.drawable.rounded_broken_image_24)
                }
            }
        }
    }
}
