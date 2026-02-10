// 文件: main/java/com/chaomixian/vflow/core/types/complex/VImage.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcelable
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
import androidx.core.net.toUri

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
        private val registry = PropertyRegistry().apply {
            register("width", "w", "宽度", getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._width?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("height", "h", "高度", getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._height?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("path", "路径", getter = { host ->
                val img = host as VImage
                val uri = Uri.parse(img.uriString)
                if (uri.scheme == "file") VString(uri.path ?: "") else VString(img.uriString)
            })
            register("uri", getter = { host ->
                VString((host as VImage).uriString)
            })
            register("size", "大小", "filesize", getter = { host ->
                val img = host as VImage
                img.readImageMetadata()
                img._size?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("name", "文件名", "filename", getter = { host ->
                val img = host as VImage
                val name = img.uriString.toUri().lastPathSegment ?: "unknown.jpg"
                VString(name)
            })
        }
    }
}