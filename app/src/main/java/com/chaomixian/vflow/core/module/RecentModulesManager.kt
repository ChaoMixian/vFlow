// 文件：RecentModulesManager.kt
// 描述：管理最近使用的模块记录

package com.chaomixian.vflow.core.module

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 最近使用的模块管理器
 * 负责保存和读取用户最近使用的模块列表
 */
object RecentModulesManager {
    private const val PREFS_NAME = "recent_modules_prefs"
    private const val KEY_RECENT_MODULES = "recent_modules"
    private const val MAX_RECENT_COUNT = 8

    private val gson = Gson()

    /**
     * 最近使用记录的数据结构
     */
    private data class RecentModuleRecord(
        val moduleId: String,
        val timestamp: Long
    )

    /**
     * 获取最近使用的模块列表（按时间倒序）
     * @param context Android上下文
     * @return 最近使用的模块ID列表，最多返回8个
     */
    suspend fun getRecentModules(context: Context): List<ActionModule> = withContext(Dispatchers.IO) {
        val records = getRecentModuleRecords(context)
        val allModules = ModuleRegistry.getAllModules()

        // 按记录顺序（时间倒序）获取模块实例
        records.mapNotNull { record ->
            allModules.find { it.id == record.moduleId }
        }
    }

    /**
     * 添加模块到最近使用记录
     * @param context Android上下文
     * @param moduleId 模块ID
     */
    suspend fun addRecentModule(context: Context, moduleId: String) = withContext(Dispatchers.IO) {
        val records = getRecentModuleRecords(context).toMutableList()

        // 移除该模块的旧记录（如果存在）
        records.removeAll { it.moduleId == moduleId }

        // 添加到最前面
        records.add(0, RecentModuleRecord(moduleId, System.currentTimeMillis()))

        // 限制数量
        if (records.size > MAX_RECENT_COUNT) {
            records.removeAt(records.size - 1)
        }

        // 保存
        saveRecentModuleRecords(context, records)
    }

    /**
     * 清除所有最近使用记录
     * @param context Android上下文
     */
    suspend fun clearRecentModules(context: Context) = withContext(Dispatchers.IO) {
        getPersistence(context).edit().remove(KEY_RECENT_MODULES).apply()
    }

    /**
     * 获取最近使用记录列表
     */
    private fun getRecentModuleRecords(context: Context): List<RecentModuleRecord> {
        val jsonStr = getPersistence(context).getString(KEY_RECENT_MODULES, null)
            ?: return emptyList()

        return try {
            val type = object : TypeToken<List<RecentModuleRecord>>() {}.type
            gson.fromJson(jsonStr, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存最近使用记录列表
     */
    private fun saveRecentModuleRecords(context: Context, records: List<RecentModuleRecord>) {
        val jsonStr = gson.toJson(records)
        getPersistence(context).edit().putString(KEY_RECENT_MODULES, jsonStr).apply()
    }

    /**
     * 获取SharedPreferences实例
     */
    private fun getPersistence(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
