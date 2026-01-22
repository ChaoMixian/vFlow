// 文件: server/src/main/java/com/chaomixian/vflow/server/common/Workarounds.kt
package com.chaomixian.vflow.server.common

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper
import java.lang.reflect.Field

/**
 * Workarounds to set up a fake Android environment
 * 基于 scrcpy 的实现，让 vFlow 的 Shell Worker 伪装成 com.android.shell
 */
object Workarounds {

    private val activityThreadClass by lazy {
        Class.forName("android.app.ActivityThread")
    }

    private val activityThread by lazy {
        // Prepare Looper before creating ActivityThread
        // ActivityThread constructor creates a Handler which requires a Looper
        prepareMainLooper()

        val constructor = activityThreadClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()

        // Set ActivityThread.sCurrentActivityThread = activityThread
        val sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread")
        sCurrentActivityThreadField.isAccessible = true
        sCurrentActivityThreadField.set(null, instance)

        // Set activityThread.mSystemThread = true
        val mSystemThreadField = activityThreadClass.getDeclaredField("mSystemThread")
        mSystemThreadField.isAccessible = true
        mSystemThreadField.setBoolean(instance, true)

        instance
    }

    /**
     * Like Looper.prepareMainLooper(), but with quitAllowed set to true
     * 类似于 scrcpy 的实现
     */
    private fun prepareMainLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepare()
            try {
                val sMainLooperField: Field = Looper::class.java.getDeclaredField("sMainLooper")
                sMainLooperField.isAccessible = true
                sMainLooperField.set(null, Looper.myLooper())
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * Apply all workarounds to set up the fake context
     * 必须在 Worker 启动早期调用，在任何服务连接之前
     */
    @JvmStatic
    fun apply() {
        if (Build.VERSION.SDK_INT >= 31) {
            // On some Samsung devices, DisplayManagerGlobal.getDisplayInfoLocked() calls ActivityThread.currentActivityThread().getConfiguration(),
            // which requires a non-null ConfigurationController.
            // ConfigurationController was introduced in Android 12, so do not attempt to set it on lower versions.
            // <https://github.com/Genymobile/scrcpy/issues/4467>
            // Must be called before fillAppContext() because it is necessary to get a valid system context.
            fillConfigurationController()
        }

        // On ONYX devices, fillAppInfo() breaks video mirroring:
        // <https://github.com/Genymobile/scrcpy/issues/5182>
        val mustFillAppInfo = !Build.BRAND.equals("ONYX", ignoreCase = true)

        if (mustFillAppInfo) {
            fillAppInfo()
        }

        fillAppContext()
    }

    private fun fillAppInfo() {
        try {
            // ActivityThread.AppBindData appBindData = new ActivityThread.AppBindData();
            val appBindDataClass = Class.forName("android.app.ActivityThread\$AppBindData")
            val appBindDataConstructor = appBindDataClass.getDeclaredConstructor()
            appBindDataConstructor.isAccessible = true
            val appBindData = appBindDataConstructor.newInstance()

            val applicationInfo = ApplicationInfo()
            applicationInfo.packageName = FakeContext.PACKAGE_NAME

            // appBindData.appInfo = applicationInfo;
            val appInfoField = appBindDataClass.getDeclaredField("appInfo")
            appInfoField.isAccessible = true
            appInfoField.set(appBindData, applicationInfo)

            // activityThread.mBoundApplication = appBindData;
            val mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
            mBoundApplicationField.isAccessible = true
            mBoundApplicationField.set(activityThread, appBindData)
        } catch (t: Throwable) {
            // this is a workaround, so failing is not an error
            System.err.println("Could not fill app info: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
        }
    }

    private fun fillAppContext() {
        try {
            val app = Instrumentation.newApplication(Application::class.java, FakeContext.get())

            // activityThread.mInitialApplication = app;
            val mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication")
            mInitialApplicationField.isAccessible = true
            mInitialApplicationField.set(activityThread, app)
        } catch (t: Throwable) {
            // this is a workaround, so failing is not an error
            System.err.println("Could not fill app context: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
        }
    }

    private fun fillConfigurationController() {
        try {
            val configurationControllerClass = Class.forName("android.app.ConfigurationController")
            val activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal")

            // configurationController = new ConfigurationController(ACTIVITY_THREAD);
            val configurationControllerConstructor =
                configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass)
            configurationControllerConstructor.isAccessible = true
            val configurationController = configurationControllerConstructor.newInstance(activityThread)

            // ACTIVITY_THREAD.mConfigurationController = configurationController;
            val configurationControllerField = activityThreadClass.getDeclaredField("mConfigurationController")
            configurationControllerField.isAccessible = true
            configurationControllerField.set(activityThread, configurationController)
        } catch (t: Throwable) {
            // this is a workaround, so failing is not an error
            System.err.println("Could not fill configuration: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
        }
    }

    @JvmStatic
    fun getSystemContext(): Context {
        return try {
            val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
            getSystemContextMethod.invoke(activityThread) as Context
        } catch (t: Throwable) {
            throw RuntimeException("Could not get system context: ${t.message}", t)
        }
    }
}
