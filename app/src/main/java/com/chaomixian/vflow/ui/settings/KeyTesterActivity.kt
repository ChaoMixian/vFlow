// 文件：main/java/com/chaomixian/vflow/ui/settings/KeyTesterActivity.kt
package com.chaomixian.vflow.ui.settings

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.services.ShizukuManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 按键测试 Activity
 * 用于查找物理按键的设备路径 (Device Path) 和按键代码 (Code Name/Key Code)。
 * 直接显示 getevent -l 的原始数据。
 */
class KeyTesterActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private var listenerJob: Job? = null
    private val scriptFileName = "vflow_key_tester.sh"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // 接收来自 Shell 脚本的广播
    private val keyEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_KEY_TEST_EVENT) {
                val rawData = intent.getStringExtra("data") ?: return
                displayRawData(rawData)
            }
        }
    }

    companion object {
        const val ACTION_KEY_TEST_EVENT = "com.chaomixian.vflow.KEY_TEST_EVENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.chaomixian.vflow.R.layout.activity_key_tester)

        val toolbar = findViewById<MaterialToolbar>(com.chaomixian.vflow.R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "按键测试器 (原始数据)"

        logTextView = findViewById(com.chaomixian.vflow.R.id.text_logs)
        statusTextView = findViewById(com.chaomixian.vflow.R.id.text_status)

        // 注册广播接收器
        val filter = IntentFilter(ACTION_KEY_TEST_EVENT)
        // 注意：在高版本 Android 上，必须使用 RECEIVER_EXPORTED 才能接收来自 Shell 的广播
        ContextCompat.registerReceiver(this, keyEventReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        startKeyListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(keyEventReceiver)
        } catch (e: Exception) {
            // 忽略未注册异常
        }
        stopKeyListening()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startKeyListening() {
        // 虽然日志显示未授权，但命令能执行，所以我们尝试继续运行
        // 只有当 ShizukuManager 彻底报错时才停止
        if (!ShizukuManager.isShizukuActive(this)) {
            statusTextView.text = "Shizuku 未激活，无法监听按键。"
            return
        }

        statusTextView.text = "正在初始化监听脚本..."
        logTextView.text = "请按下物理按键...\n(显示 getevent -l 原始输出)\n----------------------------------------\n"

        listenerJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取当前包名
                val packageName = packageName

                // 创建监听脚本
                val scriptContent = """
                    #!/system/bin/sh
                    PACKAGE_NAME="$packageName"
                    ACTION="$ACTION_KEY_TEST_EVENT"
                    
                    getevent -l | grep --line-buffered "EV_KEY" | while read line; do
                        am broadcast -a "${'$'}ACTION" -p "${'$'}PACKAGE_NAME" --es data "${'$'}line"
                    done
                """.trimIndent()

                DebugLogger.d(TAG, "KeyTesterActivity 缓存文件夹：$cacheDir 脚本：$scriptFileName ")
                val scriptFile = File(cacheDir, scriptFileName)
                scriptFile.writeText(scriptContent)
                scriptFile.setExecutable(true)

                withContext(Dispatchers.Main) {
                    statusTextView.text = "监听中... "
                }

                DebugLogger.d(TAG, "KeyTesterActivity 开始执行脚本: ${scriptFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    statusTextView.text = "监听中... \n开始执行脚本：${scriptFile.absolutePath} \n 脚本内容：\n${scriptFile.readText()}\n"
                }
                // 执行脚本 (会阻塞，直到被 kill)
                ShizukuManager.execShellCommand(this@KeyTesterActivity, "sh ${scriptFile.absolutePath}")

            } catch (e: Exception) {
                DebugLogger.d(TAG, "KeyTesterActivity 监听出错: ${e.message}")
                withContext(Dispatchers.Main) {
                    statusTextView.text = "监听出错: ${e.message}"
                    // 如果出错，把错误信息也打印到日志区方便排查
                    displayRawData("Error: ${e.message}")
                }
            }
        }
    }

    private fun stopKeyListening() {
        listenerJob?.cancel()
        // 强制杀掉所有相关的 getevent 进程，防止后台残留
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DebugLogger.d(TAG, "KeyTesterActivity 准备杀死残留进程...")
                ShizukuManager.execShellCommand(this@KeyTesterActivity, "pkill -f \"getevent -l\"")
                ShizukuManager.execShellCommand(this@KeyTesterActivity, "pkill -f $scriptFileName")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 直接显示原始数据
    private fun displayRawData(rawData: String) {
        val timestamp = timeFormat.format(Date())
        // 将新日志插到最前面，并附带时间戳
        val logLine = "[$timestamp] $rawData\n"
        logTextView.text = logLine + logTextView.text

        // 避免日志过长影响性能
        if (logTextView.text.length > 10000) {
            logTextView.text = logTextView.text.substring(0, 10000)
        }
    }
}