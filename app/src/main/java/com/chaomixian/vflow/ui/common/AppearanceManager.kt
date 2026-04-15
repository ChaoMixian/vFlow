package com.chaomixian.vflow.ui.common

import android.content.Context
import android.content.res.Configuration
import kotlin.math.abs
import kotlin.math.roundToInt

object AppearanceManager {
    const val PREFS_NAME = "vFlowPrefs"
    const val KEY_APP_SCALE = "appScale"

    const val DEFAULT_APP_SCALE = 1.0f
    const val MIN_APP_SCALE = 0.75f
    const val MAX_APP_SCALE = 1.25f
    const val APP_SCALE_STEP = 0.05f

    fun getAppScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return clampAppScale(prefs.getFloat(KEY_APP_SCALE, DEFAULT_APP_SCALE))
    }

    fun clampAppScale(scale: Float): Float {
        return scale.coerceIn(MIN_APP_SCALE, MAX_APP_SCALE)
    }

    fun applyDisplayScale(context: Context): Context {
        val appScale = getAppScale(context)
        if (abs(appScale - DEFAULT_APP_SCALE) < 0.001f) return context

        val baseConfig = context.resources.configuration
        val scaledConfig = Configuration(baseConfig)
        val baseDensityDpi = baseConfig.densityDpi

        if (baseDensityDpi > 0) {
            scaledConfig.densityDpi = (baseDensityDpi * appScale).roundToInt().coerceAtLeast(1)
        }
        scaledConfig.fontScale = (baseConfig.fontScale * appScale).coerceIn(0.5f, 2.0f)

        return context.createConfigurationContext(scaledConfig)
    }
}
