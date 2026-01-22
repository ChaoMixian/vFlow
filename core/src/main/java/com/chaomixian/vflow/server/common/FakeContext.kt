// 文件: server/src/main/java/com/chaomixian/vflow/server/common/FakeContext.kt
package com.chaomixian.vflow.server.common

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process

/**
 * 伪装成 com.android.shell 的 Context
 * 基于 scrcpy 的实现，让 vFlow 的 Shell Worker 被系统识别为 Shell 进程
 */
class FakeContext private constructor() : ContextWrapper(Workarounds.getSystemContext()) {

    companion object {
        const val PACKAGE_NAME = "com.android.shell"
        private val INSTANCE = FakeContext()

        @JvmStatic
        fun get(): FakeContext = INSTANCE
    }

    override fun getPackageName(): String = PACKAGE_NAME

    override fun getOpPackageName(): String = PACKAGE_NAME

    @TargetApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource {
        val builder = AttributionSource.Builder(Process.SHELL_UID)
        builder.setPackageName(PACKAGE_NAME)
        return builder.build()
    }

    // @Override to be added on SDK upgrade for Android 14
    @Suppress("unused", "OVERRIDE_DEPRECATION")
    override fun getDeviceId(): Int = 0

    override fun getApplicationContext(): Context = this

    override fun createPackageContext(packageName: String, flags: Int): Context = this

    @SuppressLint("SoonBlockedPrivateApi")
    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name) ?: return null

        // "semclipboard" is a Samsung-internal service
        // See:
        //  - <https://github.com/Genymobile/scrcpy/issues/6224>
        //  - <https://github.com/Genymobile/scrcpy/issues/6523>
        if (name == CLIPBOARD_SERVICE || name == "semclipboard" || name == ACTIVITY_SERVICE) {
            try {
                val field = service.javaClass.getDeclaredField("mContext")
                field.isAccessible = true
                field.set(service, this)
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
        }

        return service
    }
}
