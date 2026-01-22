package com.chaomixian.vflow.server.common.utils

import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Surface
import java.lang.reflect.Method

// 日志工具别名
private val Logger = com.chaomixian.vflow.server.common.Logger

/**
 * 屏幕捕获工具类
 * 基于scrcpy的实现原理，支持在高版本Android上捕获屏幕
 *
 * 核心原理：
 * 1. 使用DisplayManager.createVirtualDisplay()创建虚拟显示器（优先）
 * 2. 备用方案：使用SurfaceControl.createDisplay()创建虚拟显示
 * 3. 将屏幕内容渲染到提供的Surface上
 *
 * 初始化策略：
 * - DisplayManager: 必须初始化成功，否则功能不可用
 * - SurfaceControl: 不预初始化，运行时懒加载方法
 */
object DisplayCaptureUtils {

    private const val TAG = "DisplayCaptureUtils"

    // DisplayManager Global 实例（必需）
    private var displayManagerGlobal: Any? = null

    // 反射方法缓存 - DisplayManager
    private var getDisplayInfoMethod: Method? = null
    private var createVirtualDisplayMethod: Method? = null
    private var getDisplayIdsMethod: Method? = null

    // SurfaceControl 类引用和懒加载方法
    private var surfaceControlClass: Class<*>? = null
    private var createDisplayMethod: Method? = null
    private var destroyDisplayMethod: Method? = null
    private var setDisplaySurfaceMethod: Method? = null
    private var setDisplayProjectionMethod: Method? = null
    private var setDisplayLayerStackMethod: Method? = null
    private var openTransactionMethod: Method? = null
    private var closeTransactionMethod: Method? = null
    private var getInternalDisplayTokenMethod: Method? = null
    private var getBuiltInDisplayMethod: Method? = null

    // 标记是否已初始化SurfaceControl方法
    private var surfaceControlMethodsInitialized = false

    init {
        initializeDisplayManager()
        // SurfaceControl方法延迟到首次使用时初始化
    }

    /**
     * 初始化 DisplayManager Global
     */
    private fun initializeDisplayManager() {
        try {
            val dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val getInstanceMethod = dmgClass.getDeclaredMethod("getInstance")
            displayManagerGlobal = getInstanceMethod.invoke(null)

            // 缓存方法
            getDisplayInfoMethod = dmgClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType)
            getDisplayIdsMethod = dmgClass.getMethod("getDisplayIds")

            // createVirtualDisplay 方法签名在不同Android版本可能不同
            try {
                createVirtualDisplayMethod = android.hardware.display.DisplayManager::class.java.getMethod(
                    "createVirtualDisplay",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Surface::class.java
                )
            } catch (e: NoSuchMethodException) {
                Logger.warn(TAG, "createVirtualDisplay method not found: ${e.message}")
            }

            com.chaomixian.vflow.server.common.Logger.debug(TAG, "DisplayManagerGlobal initialized successfully")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize DisplayManagerGlobal", e)
        }
    }

    /**
     * 懒加载初始化 SurfaceControl 方法
     * 只在首次需要时才初始化，避免不必要的错误日志
     */
    private fun ensureSurfaceControlInitialized() {
        if (surfaceControlMethodsInitialized) {
            return  // 已初始化，直接返回
        }

        try {
            surfaceControlClass = Class.forName("android.view.SurfaceControl")

            createDisplayMethod = surfaceControlClass!!.getMethod("createDisplay", String::class.java, Boolean::class.javaPrimitiveType)
            destroyDisplayMethod = surfaceControlClass!!.getMethod("destroyDisplay", IBinder::class.java)
            setDisplaySurfaceMethod = surfaceControlClass!!.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java)
            setDisplayProjectionMethod = surfaceControlClass!!.getMethod("setDisplayProjection", IBinder::class.java, Int::class.javaPrimitiveType, Rect::class.java, Rect::class.java)
            setDisplayLayerStackMethod = surfaceControlClass!!.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.javaPrimitiveType)
            openTransactionMethod = surfaceControlClass!!.getMethod("openTransaction")
            closeTransactionMethod = surfaceControlClass!!.getMethod("closeTransaction")

            // Android 10+ 使用 getInternalDisplayToken
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    getInternalDisplayTokenMethod = surfaceControlClass!!.getMethod("getInternalDisplayToken")
                } catch (e: NoSuchMethodException) {
                    Logger.warn(TAG, "getInternalDisplayToken method not found")
                }
            } else {
                // Android 9 及以下使用 getBuiltInDisplay
                try {
                    getBuiltInDisplayMethod = surfaceControlClass!!.getMethod("getBuiltInDisplay", Int::class.javaPrimitiveType)
                } catch (e: NoSuchMethodException) {
                    Logger.warn(TAG, "getBuiltInDisplay method not found")
                }
            }

            surfaceControlMethodsInitialized = true
            Logger.debug(TAG, "SurfaceControl methods initialized successfully")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize SurfaceControl methods", e)
            surfaceControlMethodsInitialized = true  // 标记为已尝试，避免重复初始化
        }
    }

    /**
     * 获取显示信息
     * @param displayId 显示器ID，0表示主显示器
     * @return DisplayInfo 包含宽度、高度、旋转角度等信息
     */
    fun getDisplayInfo(displayId: Int = 0): DisplayInfo? {
        if (displayManagerGlobal == null || getDisplayInfoMethod == null) {
            Logger.error(TAG, "DisplayManagerGlobal not initialized")
            return null
        }

        return try {
            val displayInfoObj = getDisplayInfoMethod!!.invoke(displayManagerGlobal, displayId) ?: run {
                // 如果反射失败，尝试从 dumpsys display 解析
                return parseDisplayInfoFromDumpsys(displayId)
            }

            val cls = displayInfoObj.javaClass
            val width = cls.getDeclaredField("logicalWidth").getInt(displayInfoObj)
            val height = cls.getDeclaredField("logicalHeight").getInt(displayInfoObj)
            val rotation = cls.getDeclaredField("rotation").getInt(displayInfoObj)
            val layerStack = cls.getDeclaredField("layerStack").getInt(displayInfoObj)
            val flags = cls.getDeclaredField("flags").getInt(displayInfoObj)

            DisplayInfo(
                displayId = displayId,
                width = width,
                height = height,
                rotation = rotation,
                layerStack = layerStack,
                flags = flags
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get display info", e)
            parseDisplayInfoFromDumpsys(displayId)
        }
    }

    /**
     * 从 dumpsys display 输出解析显示信息（备用方案）
     */
    private fun parseDisplayInfoFromDumpsys(displayId: Int): DisplayInfo? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys display"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val regex = Regex("""mOverrideDisplayInfo=DisplayInfo\{.*?displayId $displayId.*?real (\d+) x (\d+).*?rotation (\d+).*?layerStack (\d+)""")
            val match = regex.find(output) ?: return null

            val (width, height, rotation, layerStack) = match.destructured
            DisplayInfo(
                displayId = displayId,
                width = width.toInt(),
                height = height.toInt(),
                rotation = rotation.toInt(),
                layerStack = layerStack.toInt(),
                flags = 0
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse display info from dumpsys", e)
            null
        }
    }

    /**
     * 创建虚拟显示器
     * @param name 显示器名称
     * @param width 宽度
     * @param height 高度
     * @param displayId 要镜像的显示器ID
     * @param surface 用于接收屏幕内容的Surface
     * @return VirtualDisplay实例，如果失败则返回null
     */
    fun createVirtualDisplay(name: String, width: Int, height: Int, displayId: Int, surface: Surface): Any? {
        if (createVirtualDisplayMethod == null) {
            Logger.error(TAG, "createVirtualDisplay method not available")
            return null
        }

        return try {
            createVirtualDisplayMethod!!.invoke(null, name, width, height, displayId, surface)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create virtual display", e)
            null
        }
    }

    /**
     * 使用 SurfaceControl 创建显示器（备用方案）
     * @param name 显示器名称
     * @param secure 是否创建安全显示器
     * @return 显示器Token (IBinder)
     */
    fun createDisplay(name: String, secure: Boolean = false): IBinder? {
        ensureSurfaceControlInitialized()  // 懒加载

        if (createDisplayMethod == null) {
            Logger.error(TAG, "createDisplay method not available")
            return null
        }

        return try {
            // Android 12+ 不能使用shell权限创建secure显示器
            val isSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) false else secure
            createDisplayMethod!!.invoke(null, name, isSecure) as? IBinder
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create display", e)
            null
        }
    }

    /**
     * 设置显示器的Surface
     */
    fun setDisplaySurface(displayToken: IBinder, surface: Surface): Boolean {
        ensureSurfaceControlInitialized()  // 懒加载

        if (setDisplaySurfaceMethod == null) {
            return false
        }

        return try {
            setDisplaySurfaceMethod!!.invoke(null, displayToken, surface)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to set display surface", e)
            false
        }
    }

    /**
     * 设置显示器的投影（用于缩放和裁剪）
     */
    fun setDisplayProjection(
        displayToken: IBinder,
        orientation: Int,
        layerStackRect: Rect,
        displayRect: Rect
    ): Boolean {
        ensureSurfaceControlInitialized()  // 懒加载

        if (setDisplayProjectionMethod == null) {
            return false
        }

        return try {
            openTransactionMethod?.invoke(null)
            setDisplayProjectionMethod!!.invoke(null, displayToken, orientation, layerStackRect, displayRect)
            closeTransactionMethod?.invoke(null)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to set display projection", e)
            try {
                closeTransactionMethod?.invoke(null)
            } catch (ignored: Exception) {}
            false
        }
    }

    /**
     * 设置显示器的Layer Stack
     */
    fun setDisplayLayerStack(displayToken: IBinder, layerStack: Int): Boolean {
        ensureSurfaceControlInitialized()  // 懒加载

        if (setDisplayLayerStackMethod == null) {
            return false
        }

        return try {
            openTransactionMethod?.invoke(null)
            setDisplayLayerStackMethod!!.invoke(null, displayToken, layerStack)
            closeTransactionMethod?.invoke(null)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to set display layer stack", e)
            try {
                closeTransactionMethod?.invoke(null)
            } catch (ignored: Exception) {}
            false
        }
    }

    /**
     * 销毁显示器
     */
    fun destroyDisplay(displayToken: IBinder): Boolean {
        ensureSurfaceControlInitialized()  // 懒加载

        if (destroyDisplayMethod == null) {
            return false
        }

        return try {
            destroyDisplayMethod!!.invoke(null, displayToken)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to destroy display", e)
            false
        }
    }

    /**
     * 获取主显示器的Token
     */
    fun getMainDisplayToken(): IBinder? {
        ensureSurfaceControlInitialized()  // 懒加载

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getInternalDisplayTokenMethod != null) {
                getInternalDisplayTokenMethod!!.invoke(null) as? IBinder
            } else if (getBuiltInDisplayMethod != null) {
                getBuiltInDisplayMethod!!.invoke(null, 0) as? IBinder
            } else {
                Logger.error(TAG, "No method available to get display token")
                null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get main display token", e)
            null
        }
    }

    /**
     * 显示信息数据类
     */
    data class DisplayInfo(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val layerStack: Int,
        val flags: Int
    )
}