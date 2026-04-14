package com.chaomixian.vflow.ui.app_picker

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import com.chaomixian.vflow.services.ShellManager
import java.util.Locale

data class AppPickerUser(
    val userId: Int,
    val handle: UserHandle?,
    val isCurrentUser: Boolean
)

object AppUserSupport {
    fun getCurrentUserId(): Int = getUserIdentifier(Process.myUserHandle())

    fun getUserLabel(context: Context, userId: Int): String {
        return if (Locale.getDefault().language.startsWith("zh")) {
            "用户 $userId"
        } else {
            "User $userId"
        }
    }

    fun getAvailableUsers(context: Context): List<AppPickerUser> {
        val currentHandle = Process.myUserHandle()
        val currentUserId = getUserIdentifier(currentHandle)
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        val handles = buildList {
            add(currentHandle)
            launcherApps?.profiles?.let { addAll(it) }
        }.distinctBy { getUserIdentifier(it) }

        return handles
            .map { handle ->
                val userId = getUserIdentifier(handle)
                AppPickerUser(
                    userId = userId,
                    handle = handle,
                    isCurrentUser = userId == currentUserId
                )
            }
            .sortedWith(compareBy<AppPickerUser> { !it.isCurrentUser }.thenBy { it.userId })
    }

    suspend fun getAvailableUsersForPicker(context: Context): List<AppPickerUser> {
        val usersById = getAvailableUsers(context).associateBy { it.userId }.toMutableMap()
        if (!isShellAvailable(context)) {
            return usersById.values.sortedWith(compareBy<AppPickerUser> { !it.isCurrentUser }.thenBy { it.userId })
        }

        loadUserIdsViaShell(context).forEach { userId ->
            usersById.putIfAbsent(
                userId,
                AppPickerUser(
                    userId = userId,
                    handle = null,
                    isCurrentUser = userId == getCurrentUserId()
                )
            )
        }

        return usersById.values.sortedWith(compareBy<AppPickerUser> { !it.isCurrentUser }.thenBy { it.userId })
    }

    fun findUserHandle(context: Context, userId: Int?): UserHandle? {
        if (userId == null) return null
        return getAvailableUsers(context).firstOrNull { it.userId == userId }?.handle
    }

    fun loadAppLabel(context: Context, packageName: String, userId: Int?): String? {
        val pm = context.packageManager
        val targetUserId = userId ?: getCurrentUserId()

        if (targetUserId == getCurrentUserId()) {
            return try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

        val userHandle = findUserHandle(context, targetUserId) ?: return null
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val launcherActivity = launcherApps.getActivityList(packageName, userHandle).firstOrNull() ?: return null

        return launcherActivity.label?.toString()?.takeIf { it.isNotBlank() }
            ?: launcherActivity.applicationInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
    }

    private fun getUserIdentifier(userHandle: UserHandle): Int {
        return try {
            UserHandle::class.java.getMethod("getIdentifier").invoke(userHandle) as Int
        } catch (_: Exception) {
            userHandle.hashCode()
        }
    }

    suspend fun listPackagesForUser(context: Context, userId: Int): Set<String> {
        if (!isShellAvailable(context)) return emptySet()

        val output = ShellManager.execShellCommand(
            context,
            "pm list packages --user $userId"
        )

        if (output.startsWith("Error:", ignoreCase = true)) {
            return emptySet()
        }

        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isShellAvailable(context: Context): Boolean {
        return ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()
    }

    private suspend fun loadUserIdsViaShell(context: Context): Set<Int> {
        val output = ShellManager.execShellCommand(context, "pm list users")
        if (output.startsWith("Error:", ignoreCase = true)) {
            return emptySet()
        }

        return USER_INFO_REGEX.findAll(output)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toSet()
    }

    private val USER_INFO_REGEX = Regex("""UserInfo\{(\d+):""")
}
