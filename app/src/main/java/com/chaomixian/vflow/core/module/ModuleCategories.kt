package com.chaomixian.vflow.core.module

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.chaomixian.vflow.R

data class ModuleCategorySpec(
    val id: String,
    @StringRes val labelRes: Int?,
    @ColorRes val colorRes: Int,
    val sortOrder: Int,
    val defaultLabel: String
)

object ModuleCategories {
    const val TRIGGER = "trigger"
    const val INTERACTION = "interaction"
    const val LOGIC = "logic"
    const val DATA = "data"
    const val FILE = "file"
    const val NETWORK = "network"
    const val DEVICE = "device"
    const val CORE = "core"
    const val SHIZUKU = "shizuku"
    const val TEMPLATE = "template"
    const val UI = "ui"
    const val FEISHU = "feishu"
    const val USER_MODULE = "user_module"

    private val specs = listOf(
        ModuleCategorySpec(TRIGGER, R.string.category_trigger, R.color.category_trigger, 0, "触发器"),
        ModuleCategorySpec(INTERACTION, R.string.category_interaction, R.color.category_ui_interaction, 1, "界面交互"),
        ModuleCategorySpec(LOGIC, R.string.category_logic, R.color.category_logic, 2, "逻辑控制"),
        ModuleCategorySpec(DATA, R.string.category_data, R.color.category_data, 3, "数据"),
        ModuleCategorySpec(FILE, R.string.category_file, R.color.category_file, 4, "文件"),
        ModuleCategorySpec(NETWORK, R.string.category_network, R.color.category_network, 5, "网络"),
        ModuleCategorySpec(DEVICE, R.string.category_device, R.color.category_system, 6, "应用与系统"),
        ModuleCategorySpec(CORE, R.string.category_core, R.color.static_pill_color, 7, "Core (Beta)"),
        ModuleCategorySpec(SHIZUKU, R.string.category_shizuku, R.color.category_shizuku, 8, "Shizuku"),
        ModuleCategorySpec(TEMPLATE, R.string.category_template, R.color.static_pill_color, 9, "模板"),
        ModuleCategorySpec(UI, R.string.category_ui, R.color.static_pill_color, 10, "UI 组件"),
        ModuleCategorySpec(FEISHU, R.string.category_feishu, R.color.category_feishu, 11, "飞书"),
        ModuleCategorySpec(USER_MODULE, null, R.color.category_user_module, 12, "用户模块")
    )

    private val specsById = specs.associateBy { it.id }
    private val legacyLabelsToIds = mapOf(
        "触发器" to TRIGGER,
        "界面交互" to INTERACTION,
        "屏幕交互" to INTERACTION,
        "逻辑控制" to LOGIC,
        "逻辑" to LOGIC,
        "数据" to DATA,
        "数据处理" to DATA,
        "文件" to FILE,
        "网络" to NETWORK,
        "应用与系统" to DEVICE,
        "设备与系统" to DEVICE,
        "Core (Beta)" to CORE,
        "核心 (Beta)" to CORE,
        "Shizuku" to SHIZUKU,
        "模板" to TEMPLATE,
        "Templates" to TEMPLATE,
        "UI 组件" to UI,
        "UI控制" to UI,
        "UI Control" to UI,
        "飞书" to FEISHU,
        "Feishu" to FEISHU,
        "用户模块" to USER_MODULE
    )

    fun allSpecs(): List<ModuleCategorySpec> = specs

    fun getSpec(categoryIdOrLegacy: String?): ModuleCategorySpec? {
        val resolvedId = resolveId(categoryIdOrLegacy) ?: return null
        return specsById[resolvedId]
    }

    fun resolveId(categoryIdOrLegacy: String?): String? {
        if (categoryIdOrLegacy.isNullOrBlank()) return null
        return specsById[categoryIdOrLegacy]?.id ?: legacyLabelsToIds[categoryIdOrLegacy]
    }

    fun getSortOrder(categoryIdOrLegacy: String?): Int {
        return getSpec(categoryIdOrLegacy)?.sortOrder ?: Int.MAX_VALUE
    }

    fun getColorRes(categoryIdOrLegacy: String?): Int {
        return getSpec(categoryIdOrLegacy)?.colorRes ?: R.color.static_pill_color
    }

    fun getDisplayName(categoryIdOrLegacy: String?): String {
        return getSpec(categoryIdOrLegacy)?.defaultLabel ?: (categoryIdOrLegacy ?: "")
    }

    fun getLocalizedLabel(context: Context, categoryIdOrLegacy: String?): String {
        val spec = getSpec(categoryIdOrLegacy) ?: return categoryIdOrLegacy.orEmpty()
        return spec.labelRes?.let(context::getString) ?: spec.defaultLabel
    }
}
