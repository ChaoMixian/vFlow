// 文件: server/src/main/java/com/chaomixian/vflow/server/common/utils/SystemUtils.kt
package com.chaomixian.vflow.server.common.utils

import android.os.Build
import java.io.File

object SystemUtils {

    // Shell 用户的标准 UID/GID
    const val AID_SHELL = 2000

    // 常用附属组: shell, input, log, adb, sdcard_rw, net_bt_admin, net_bt, inet, net_bw_stats
    private val SHELL_GROUPS = intArrayOf(
        2000, 1004, 1007, 1011, 1015, 3001, 3002, 3003, 3006
    )

    fun getMyUid(): Int {
        return try {
            android.os.Process.myUid()
        } catch (e: Exception) {
            -1
        }
    }

    fun isRoot(): Boolean {
        return getMyUid() == 0
    }

    fun getClassPath(): String {
        return System.getProperty("java.class.path") ?: "."
    }

    /**
     * 启动 vFlow Worker 子进程 (直接 Fork)
     */
    fun startWorkerProcess(type: String): Process {
        val classPath = getClassPath()
        val mainClass = "com.chaomixian.vflow.server.VFlowCore"

        val appProcessCmd = listOf(
            "/system/bin/app_process",
            "-Djava.class.path=$classPath",
            "/system/bin",
            mainClass,
            "--worker",
            "--type",
            type
        )

        println(">>> Spawning Worker [$type]: ${appProcessCmd.joinToString(" ")}")

        return ProcessBuilder(appProcessCmd)
            .redirectErrorStream(true)
            .start()
    }

    fun killProcess(process: Process?) {
        if (process != null && process.isAlive) {
            try {
                process.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 使用反射调用 android.system.Os 进行降权
     * 解决 Unresolved reference 'setgroups' 问题
     */
    fun dropPrivilegesToShell(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            System.err.println("⚠️ System too old (Need API 21+)")
            return false
        }

        try {
            println("ℹ️ Dropping privileges from Root to Shell (UID 2000)...")

            val osClass = Class.forName("android.system.Os")

            // 1. 设置附属组 (setgroups)
            try {
                val setGroupsMethod = osClass.getMethod("setgroups", IntArray::class.java)
                setGroupsMethod.invoke(null, SHELL_GROUPS)
            } catch (e: Exception) {
                System.err.println("⚠️ Failed to set supplementary groups (Non-fatal): ${e.message}")
            }

            // 2. 设置 GID (setgid)
            val setGidMethod = osClass.getMethod("setgid", Int::class.javaPrimitiveType)
            setGidMethod.invoke(null, AID_SHELL)

            // 3. 设置 UID (setuid) - 此时丢失 Root 权限
            val setUidMethod = osClass.getMethod("setuid", Int::class.javaPrimitiveType)
            setUidMethod.invoke(null, AID_SHELL)

            // 4. 验证
            val currentUid = getMyUid()
            if (currentUid == AID_SHELL) {
                println("✅ Successfully dropped to Shell (UID: $currentUid)")
                return true
            } else {
                System.err.println("❌ Failed to drop uid. Current UID: $currentUid")
                return false
            }

        } catch (e: Exception) {
            System.err.println("❌ Drop privileges failed: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}