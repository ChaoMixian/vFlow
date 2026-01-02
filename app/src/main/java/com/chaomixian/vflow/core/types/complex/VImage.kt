// 文件: main/java/com/chaomixian/vflow/core/types/complex/VImage.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.BitmapFactory
import android.net.Uri
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import java.io.File

/**
 * 图像对象。
 * 包装一个图片 URI，并提供 width, height, path, size 等属性访问。
 */
class VImage(val uriString: String) : BaseVObject() {
    override val type = VTypeRegistry.IMAGE
    override val raw: Any = uriString

    // 缓存尺寸信息，避免重复IO
    private var _width: Int? = null
    private var _height: Int? = null
    private var _size: Long? = null

    override fun asString(): String = uriString

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = uriString.isNotEmpty()

    override fun getProperty(propertyName: String): VObject? {
        // 懒加载图片元数据
        if (_width == null) {
            readImageMetadata()
        }

        return when (propertyName.lowercase()) {
            "width", "w", "宽度" -> _width?.let { VNumber(it.toDouble()) } ?: VNull
            "height", "h", "高度" -> _height?.let { VNumber(it.toDouble()) } ?: VNull
            "path", "路径" -> {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") VString(uri.path ?: "") else VString(uriString)
            }
            "uri" -> VString(uriString)
            "size", "大小" -> _size?.let { VNumber(it.toDouble()) } ?: VNull // 字节数
            "name", "文件名" -> {
                val name = Uri.parse(uriString).lastPathSegment ?: "unknown.jpg"
                VString(name)
            }
            else -> super.getProperty(propertyName)
        }
    }

    private fun readImageMetadata() {
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
}