package com.chaomixian.vflow.core.types.complex

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize

@Parcelize
data class VFile(
    val uriString: String,
    val mimeType: String? = null
) : EnhancedBaseVObject(), Parcelable {
    override val type get() = VTypeRegistry.FILE
    override val raw get() = uriString
    override val propertyRegistry get() = Companion.registry

    override fun asString(): String = uriString

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = uriString.isNotEmpty()

    companion object {
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            register("path", "路径", returnType = VTypeRegistry.STRING, displayName = "文件路径", nameStringRes = R.string.vtype_file_path, getter = { host ->
                VString(VFilePropertySupport.path((host as VFile).uriString))
            })
            register("uri", returnType = VTypeRegistry.STRING, displayName = "URI地址", nameStringRes = R.string.vtype_file_uri, getter = { host ->
                VString((host as VFile).uriString)
            })
            register("name", "文件名", "filename", returnType = VTypeRegistry.STRING, displayName = "文件名", nameStringRes = R.string.vtype_file_name, getter = { host ->
                VString(VFilePropertySupport.name((host as VFile).uriString))
            })
            register("extension", "扩展名", "ext", returnType = VTypeRegistry.STRING, displayName = "扩展名", nameStringRes = R.string.vtype_file_extension, getter = { host ->
                VString(VFilePropertySupport.extension((host as VFile).uriString))
            })
            register("size", "大小", "filesize", returnType = VTypeRegistry.NUMBER, displayName = "文件大小", nameStringRes = R.string.vtype_file_size, getter = { host ->
                VFilePropertySupport.size((host as VFile).uriString)
                    ?.let { VNumber(it.toDouble()) }
                    ?: VNull
            })
            register("mimeType", "mime_type", "mime", returnType = VTypeRegistry.STRING, displayName = "MIME 类型", nameStringRes = R.string.vtype_file_mime_type, getter = { host ->
                val file = host as VFile
                VString(VFilePropertySupport.mimeType(file.uriString, file.mimeType))
            })
            register("base64", returnType = VTypeRegistry.STRING, displayName = "Base64", nameStringRes = R.string.vtype_file_base64, getter = { host ->
                VFilePropertySupport.base64((host as VFile).uriString)
                    ?.let { VString(it) }
                    ?: VNull
            })
            register("content", "内容", "文件内容", returnType = VTypeRegistry.STRING, displayName = "文件内容", nameStringRes = R.string.vtype_file_content, getter = { host ->
                VFilePropertySupport.textContent((host as VFile).uriString)
                    ?.let { VString(it) }
                    ?: VNull
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
    }
}
