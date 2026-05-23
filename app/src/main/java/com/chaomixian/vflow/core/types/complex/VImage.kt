// 文件: main/java/com/chaomixian/vflow/core/types/complex/VImage.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * 图像对象。
 * 包装一个图片 URI，并提供 width, height, path, size 等属性访问。
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
@Parcelize
data class VImage(val uriString: String) : EnhancedBaseVObject(), Parcelable {
    override val type get() = VTypeRegistry.IMAGE
    override val raw get() = uriString
    override val propertyRegistry get() = Companion.registry

    // 缓存尺寸信息，避免重复IO（不序列化，因为可以重新计算）
    @IgnoredOnParcel
    private var _width: Int? = null
    @IgnoredOnParcel
    private var _height: Int? = null
    @IgnoredOnParcel
    private var _size: Long? = null

    override fun asString(): String = uriString

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = uriString.isNotEmpty()

    private fun readImageMetadata() {
        if (_width != null) return // 已加载

        try {
            val context = LogManager.applicationContext
            val uri = Uri.parse(uriString)

            // 1. 获取尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            _width = options.outWidth
            _height = options.outHeight

            // 2. 获取文件大小
            if (uri.scheme == "file") {
                _size = File(uri.path!!).length()
            } else {
                // ContentUri 获取大小稍微复杂一点，这里简化处理，仅支持File
                // 实际项目中可查询 ContentResolver
                _size = 0L
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败时保持 null
        }
    }

    companion object {
        // 属性注册表：所有 VImage 实例共享
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            register("width", "w", "宽度", returnType = VTypeRegistry.NUMBER, displayName = "宽度", nameStringRes = R.string.vtype_image_width, getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._width?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("height", "h", "高度", returnType = VTypeRegistry.NUMBER, displayName = "高度", nameStringRes = R.string.vtype_image_height, getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._height?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("path", "路径", returnType = VTypeRegistry.STRING, displayName = "文件路径", nameStringRes = R.string.vtype_image_path, getter = { host ->
                val img = host as VImage
                VString(VFilePropertySupport.path(img.uriString))
            })
            register("uri", returnType = VTypeRegistry.STRING, displayName = "URI地址", nameStringRes = R.string.vtype_image_uri, getter = { host ->
                VString((host as VImage).uriString)
            })
            register("size", "大小", "filesize", returnType = VTypeRegistry.NUMBER, displayName = "文件大小", nameStringRes = R.string.vtype_image_size, getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._size?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("name", "文件名", "filename", returnType = VTypeRegistry.STRING, displayName = "文件名", nameStringRes = R.string.vtype_image_name, getter = { host ->
                val img = host as VImage
                VString(VFilePropertySupport.name(img.uriString))
            })
            register("base64", returnType = VTypeRegistry.STRING, displayName = "Base64", nameStringRes = R.string.vtype_image_base64, getter = { host ->
                VFilePropertySupport.base64((host as VImage).uriString)
                    ?.let { VString(it) }
                    ?: VNull
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
    }
}
