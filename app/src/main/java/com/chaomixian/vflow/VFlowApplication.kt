package com.chaomixian.vflow

import android.app.Application
import com.chaomixian.vflow.core.logging.CrashReportManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager

class VFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogManager.initialize(applicationContext)
        DebugLogger.initialize(applicationContext)
        CrashReportManager.install(applicationContext)
    }
}
